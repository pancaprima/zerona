# Zerona: sync upstream main and next release

Status: **MERGE IN PROGRESS** (worktree `/root/zeron-upstream-20261023-release`, branch `sync/upstream-20261023-release`). Conflict-resolution pass applied; **no commit/tag/release/CI dispatch yet**. Parent reviews full diff vs fork `main` and `upstream/main`, then commits, runs CI, bumps **0.2.119**, tags, and publishes.

## Verified sources
- Fork `origin/main` **40c454f805116592c99b679fd10f6c4821f5c437** — workspace **0.2.115**; latest published fork release **v0.2.115**.
- Incoming `upstream/main` **4cd15917561a4d91a763e05356afd45ea6d37fff** — workspace **0.2.118** (target for sync commit; **0.2.119** is a separate bump after merge CI).
- Comparison `origin/main...upstream/main`: 94 fork-only and 10 upstream-only commits.
- Upstream-only work: Cloud/provider switching/phone model picker; large RPC message fragmentation; login from errors/session delete/iOS bottom bar; Codex install repair/engine wake/cloud grouping; Claude login after update. Do not assume these features are automatically exposed in Android.
- Android PR21,23,24,25,26,27,28,29,30 verified MERGED; key heads 29/30/27 are ancestors of origin/main. Only open PR20 engine/desktop chat images remains unrelated and excluded.
- Read-only `git merge-tree` simulation predicted **11 conflicted paths** (now resolved in worktree): `Cargo.toml`, `Cargo.lock`, `apps/ios/Zeron/Core/Generated/zeron_core.swift`, `apps/ios/Zeron/Design/Palette.swift`, `crates/engine/src/lib.rs`, `crates/engine/src/workspace_files.rs`, `crates/mobile/src/layout/display.rs`, `rows.rs`, `tests.rs`, `crates/ui/src/pickers.rs`, `scripts/ci/test-core.sh`.

## Generated Swift bindings (pre-CI)
- `apps/ios/Zeron/Core/Generated/zeron_core.swift` is a **provisional byte-identical copy of `upstream/main`** (`git show upstream/main:…`); no hand-authored UniFFI union numeric mappings or guessed checksum/binding edits in the merge worktree.
- Merged fork `zeron-mobile` metadata (enum fork-order, new records) is not yet the authoritative bindgen input on Linux; **parent** must run `scripts/ios/build-core.sh` / macOS **`ios-core`** (`diff` committed Swift vs `uniffi-bindgen generate` from `libzeron_mobile`) after merge metadata settles and **apply that generator output exactly** (replace this placeholder in one shot).
- Until that refresh lands, expect **`ios-core` “Generated Swift bindings are current”** to fail if the job runs against this branch; that failure is expected, not a signal to patch Swift by hand.

## Android native limitations (this sync)
- `WidgetKind.Action { action, label }` from `crates/mobile/src/layout/display.rs` is laid out on error cards (e.g. `sign-in` / “Log in”) but **Android does not implement transcript actions** in this milestone. `ToolViews.kt` renders an explicit read-only label (`{label} (unavailable)`), `Palette` `TEXT_SECONDARY`, non-clickable, with accessibility text that the action is unavailable on Android — not a login button and no account/provider routing.

## Proposed execution (remaining after this pass)
1. Parent: `git add` / commit merge on `sync/upstream-20261023-release`, push, open PR to fork `main`.
2. Full Linux/macOS/Windows/mobile/iOS CI + exact-SHA Android unit tests/APK. Fix compile/test issues through Cursor; no relaxed assertions.
3. User APK smoke for post-sync rendering, model label, Latest, IME/multiline, links/images.
4. Merge sync PR into fork `main` only when every relevant check passes. Keep PR20 out.
5. Separate minimal **0.2.119** workspace-version bump PR after sync merge; annotated tag at exact final `main`.
6. Tag triggers existing public GitHub desktop release and updater manifest, plus R2 upload ONLY when current fork secret present.
7. Build Android APK at SAME final release SHA via `workflow_dispatch` if needed; attach clearly labeled debug/test APK (no new production signing).
8. Verify remote tag target, release assets/checksums/manifest version; send release link.

## Decision requested
Approve sync-preserving fork features, full verification, and release **v0.2.119** with optional clearly labeled Android debug/test asset. Stop and reconfirm if an architectural/feature conflict requires choosing between upstream and accepted fork behavior. No upstream push, no PR20 merge, no new credentials, no signed Android production release in this scope.
