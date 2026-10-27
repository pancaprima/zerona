# Plan: PR #45 CI root cause and push regressions

Request source: Rora (grokbot), acting as the user's delegate for personal repo `pancaprima/zerona`.

## Target
- Draft PR #45, branch `feat/unread-push-delay`
- Worktree HEAD: `c1a02dc64da4e6399b31eba68ba4035b4a2d0d77` (prior inbound quote `664e34be` was stale)
- Keep the PR draft; do not merge.

## Work
1. Fix ios-core compile error from Actions run 40445713106 / job 121996582132.
2. Fix linux-browser UI compile errors in seen-report desktop path (`seen_report.rs`, `shell.rs`, `transcript.rs`).
3. Carmen regression: trigger queued before `lastMessageAt` advances must still deliver push.
4. Alarm-fires-twice regression must run under CI workerd suite (not only defined or filtered out).
5. Preserve untracked plan files `plan-alarm-double-push-test-pr45.md` and `plan-unread-push-delay-read-by-message-id.md`.

## CI evidence (ios-core run 40445713106, job 121996582132)
- First error: `literal out of range for i32` at `crates/client/src/live/urls.rs:172` — `assert_eq!(body["messageAt"], 1_700_000_000_000)` under `#[deny(overflowing_literals)]`.
- Fix on branch: `assert_eq!(body["messageAt"], serde_json::json!(1_700_000_000_000))` so the literal is JSON `Number`, not a typed `i32` operand.

## UI compile fixes (linux-browser / `zeron-ui`)
| File | Root cause | Fix |
|------|------------|-----|
| `crates/ui/src/seen_report.rs` | `impl Fn(usize) -> Option<&str>` without lifetime; `for index in &[usize]` passes `&usize` to `Fn(usize)` | `newest_visible_message<'a>(…, impl Fn(usize) -> Option<&'a str>, …)` and `for &index in placement_indices` |
| `crates/ui/src/shell.rs` | `emit_seen_report` used private field `AppState.engine` | `self.state.read(cx).engine().cloned()` |
| `crates/ui/src/transcript.rs` | `visible_seen_message` called `Entity<AppState>::read` with generic `AppContext` | `self.state.read_with(cx, closure)` |

## Push regressions (edge workerd)
- Carmen scenario (queued trigger, then `lastMessageAt` advances, prior message already seen):
  `still sends push when an older message was already seen at the prior timestamp` in `edge/test/workerd/unread-push.workerd.test.ts`
  — seeds seen for `prev-msg`, `triggerDone()` while `msg_at` is still `first`, asserts `triggerMsgAt === first`, then `markChatMessage(later, …)`, asserts `triggerMsgAt === later`, alarm → `pushLog` length 1.
- Marker bump (control): `raises msg_at when lastMessageAt advances within the marker window`.
- Double alarm: `fires alarm twice in a row for one pending push and sends exactly one push`.

### Test commands and CI wiring
| Command | What runs |
|---------|-----------|
| `npm test` (in `edge/`) | `test:unit` + `test:workerd` — **includes** `test/workerd/unread-push.workerd.test.ts` via `vitest.workerd.config.ts` `include: ["test/workerd/**/*.test.ts"]` |
| `npm run test:workerd:unread-push` | Full unread-push file only |
| `npm run test:workerd:unread-push-alarm-twice` | Same file, vitest `-t "fires alarm twice in a row"` (substring of full `it` name) |

Workflows that run `npm test` in `edge/` on PRs touching `edge/**`:
- `.github/workflows/preview-tests.yml` (job `edge`)
- `.github/workflows/deploy.yml` (job `edge`, when edge changes deploy path)

Rust/ios-core: workspace `cargo test` via ios-core / linux-browser workflows (not run locally for this task).

## Session status (2026-10-27, no commit/push)
- Baseline untracked plans preserved (unchanged).
- Committed at `c1a02dc`: `urls.rs` JSON assert, Carmen trigger `msg_at` asserts, `edge/package.json` unread-push scripts, initial version of this plan.
- Uncommitted after agent pass: UI fixes above; this plan updated to `c1a02dc` + UI delta.
- Verification: GitHub CI only (no local `cargo` / `rustc` / rustfmt / Rust tests).
