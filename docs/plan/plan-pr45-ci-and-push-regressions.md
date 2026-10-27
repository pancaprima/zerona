# Plan: PR #45 CI root cause and push regressions

Request source: Rora (grokbot), acting as the user's delegate for personal repo `pancaprima/zerona`.

## Target
- Draft PR #45, branch `feat/unread-push-delay`
- Starting head: `664e34beaa60f41c70dd8ad238504484f9703bfc`
- Keep the PR draft; do not merge.

## Work
1. Retrieve the first `error[...]` or `panicked at` from ios-core Actions run 40445713106/job 121996582132; establish the actual root cause from the original log.
2. Fix the root cause in the smallest appropriate scope.
3. Add/repair Carmen's regression: a trigger queued before `lastMessageAt` advances must still send its push.
4. Ensure the alarm-fires-twice test is actually selected and executed, not merely present or statically counted.
5. Run suitable tests, review changes against the existing worktree baseline, commit and push only to PR #45's branch.
6. Verify CI for the exact pushed SHA and confirm PR remains draft.

## CI evidence (run 40445713106, job 121996582132 `ios-core`, head `664e34be`)
- First compiler error (no `panicked at`; tests did not run):
  - `error: literal out of range for \`i32\`` at `crates/client/src/live/urls.rs:172` — `assert_eq!(body["messageAt"], 1_700_000_000_000)` under `#[deny(overflowing_literals)]`.
  - Follow-on: `error: could not compile \`zeron-client\` (lib test) due to 1 previous error`.
- Fix (local, uncommitted): compare via `serde_json::json!(1_700_000_000_000)` so the literal is not typed as `i32`.

## Push regression (b) and alarm-twice test (local edge, uncommitted scripts only)
- Message-id read matching + `lastMessageAt` bump: already on branch at `664e34be` (`registry-room.ts`, `unread-push.workerd.test.ts` case *still sends push when an older message was already seen at the prior timestamp*).
- Explicit workerd invocation added in `edge/package.json`:
  - `npm run test:workerd:unread-push-alarm-twice` → vitest `-t "fires alarm twice in a row"` on `test/workerd/unread-push.workerd.test.ts`.
  - Full file: `npm run test:workerd:unread-push`.
- CI path unchanged: `preview-tests.yml` / `deploy.yml` run `npm test` in `edge/`, which includes `test/workerd/**/*.test.ts` (covers `unread-push.workerd.test.ts`).

## Constraints / acceptance
- Preserve pre-existing untracked plan files and unrelated changes.
- No merge, no main push, no widening scope without stopping for confirmation.
- Report the first CI error, fix commit, actual test evidence, and exact-SHA CI status.

## Agent verification (2026-10-27, no commit/push)
- Preconditions: branch `feat/unread-push-delay`, `HEAD` `664e34beaa60f41c70dd8ad238504484f9703bfc`, `origin` → `pancaprima/zerona`; remote PR #45 head matches same SHA, still draft.
- **ios-core** run [40445713106](https://github.com/pancaprima/zerona/actions/runs/40445713106) job [121996582132](https://github.com/pancaprima/zerona/actions/runs/40445713106/job/121996582132): `failure` — first error only `literal out of range for i32` at `urls.rs:172` (as above); no test panics.
- Local fix (uncommitted): `assert_eq!(body["messageAt"], serde_json::json!(1_700_000_000_000))` in `crates/client/src/live/urls.rs`; `edge/package.json` adds `test:workerd:unread-push` and `test:workerd:unread-push-alarm-twice` (`-t "fires alarm twice in a row"` matches full test name substring).
- Workerd regressions on branch at `664e34be` (no test file edits this session):
  - Queued trigger before `lastMessageAt` advances + push still sends: `still sends push when an older message was already seen at the prior timestamp`; marker bump: `raises msg_at when lastMessageAt advances within the marker window`.
  - Double alarm: `fires alarm twice in a row for one pending push and sends exactly one push` — included in `test/workerd/**/*.test.ts` via `npm test` / `test:workerd`; explicit script selects it.
- PR body table (Carmen’s five scenarios): all five `it(...)` names exist in `unread-push.workerd.test.ts` — **no PR description update** (no mismatch).
- Gaps: Rust/workerd tests not run locally (per request); fix not on remote so CI for `664e34be` stays red until commit/push; `gh` CLI unauthenticated here.
