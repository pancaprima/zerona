# Zerona: sync upstream main and next release

Status: **PR31 CI IN PROGRESS**, candidate `3508fe4d` (worktree `/root/zeron-upstream-20261023-release`, branch `sync/upstream-20261023-release`). Conflict resolution reviewed and pushed; helper tests50 PASS. Full native CI and authoritative generated binding gate remain pending. No sync merge, tag or release yet. User additionally authorizes PR20 image preview before the release; review is in progress separately.

## Verified sources
- Fork `origin/main` **40c454f805116592c99b679fd10f6c4821f5c437** — workspace **0.2.115**; latest published fork release **v0.2.115**.
- Incoming `upstream/main` **4cd15917561a4d91a763e05356afd45ea6d37fff** — workspace **0.2.118** (target for sync commit; **0.2.119** is a separate bump after merge CI).
- Comparison `origin/main...upstream/main`: 94 fork-only and 10 upstream-only commits.
- Upstream-only work: Cloud/provider switching/phone model picker; large RPC message fragmentation; login from errors/session delete/iOS bottom bar; Codex install repair/engine wake/cloud grouping; Claude login after update. Do not assume these features are automatically exposed in Android.
- Android PR21,23,24,25,26,27,28,29,30 verified MERGED; key heads 29/30/27 are ancestors of origin/main. PR20 engine/desktop chat image preview is NOW included in the release by explicit user request on 2026-10-23. Keep its integration separate from this upstream sync PR; review against the synced fork and require fresh combined-tree CI before release. Its existing scope defers Android/iOS image-preview expansion.
- Read-only `git merge-tree` simulation predicted **11 conflicted paths** (now resolved in worktree): `Cargo.toml`, `Cargo.lock`, `apps/ios/Zeron/Core/Generated/zeron_core.swift`, `apps/ios/Zeron/Design/Palette.swift`, `crates/engine/src/lib.rs`, `crates/engine/src/workspace_files.rs`, `crates/mobile/src/layout/display.rs`, `rows.rs`, `tests.rs`, `crates/ui/src/pickers.rs`, `scripts/ci/test-core.sh`.
- **`pickers.rs` post-merge integration:** upstream `device_rows` inlined the Cloud row before fork home `extend`, so Cloud was not last. Checkout and Home are distinct rows (same name/id, different `home` flag); the upstream cloud test only compared `(name, cloud)` and treated duplicate “Studio” labels as one destination. Fix: unconditional home rows (fork `40c454f8`), Cloud pushed last; strengthen `cloud_is_a_device_option_while_on_and_reaching_the_projects_repository` to assert full row tuples including `home` and space id. PR20 scope unchanged.

## Generated Swift bindings
- `apps/ios/Zeron/Core/Generated/zeron_core.swift` was updated by applying the **complete unified diff** from the failed **`ios-core` “Generated Swift bindings are current”** step (candidate `3508fe4d` CI log: `zerona-sync-second-macos-failure.log`), via `git apply` of `zerona-sync-generated-swift.patch` — not hand-authored UniFFI mappings or guessed checksums.
- **Next gate (parent):** re-run **`ios-core`** at the exact post-fix SHA and confirm `diff -u` is clean; macOS `scripts/ios/build-core.sh` refresh remains the authoritative cross-check before release.

## Android native limitations (this sync)
- `WidgetKind.Action { action, label }` from `crates/mobile/src/layout/display.rs` is laid out on error cards (e.g. `sign-in` / “Log in”) but **Android does not implement transcript actions** in this milestone. `ToolViews.kt` renders an explicit read-only label (`{label} (unavailable)`), `Palette` `TEXT_SECONDARY`, non-clickable, with accessibility text that the action is unavailable on Android — not a login button and no account/provider routing.

## Proposed execution (remaining after this pass)
1. Parent: `git add` / commit merge on `sync/upstream-20261023-release`, push, open PR to fork `main`.
2. Full Linux/macOS/Windows/mobile/iOS CI + exact-SHA Android unit tests/APK. Fix compile/test issues through Cursor; no relaxed assertions.
3. User APK smoke for post-sync rendering, model label, Latest, IME/multiline, links/images.
4. Merge sync PR into fork `main` only when every relevant check passes. Integrate PR20 image preview onto the newly synced fork, review security and feature preservation, and require fresh combined-tree CI before merging PR20. Old PR20 green checks are not combined-tree acceptance.
5. Separate minimal **0.2.119** workspace-version bump PR after BOTH sync and PR20 merge; recheck upstream/fork versions and existing tags first. Annotated tag at exact final `main` only after all gates pass.
6. Tag triggers existing public GitHub desktop release and updater manifest, plus R2 upload ONLY when current fork secret present.
7. Build Android APK at SAME final release SHA via `workflow_dispatch` if needed; attach clearly labeled debug/test APK (no new production signing).
8. Verify remote tag target, release assets/checksums/manifest version; send release link.

## Decision requested
Approved: sync preserving fork features, full verification, and release **v0.2.119** with clearly labeled Android debug/test asset. Latest explicit user request additionally includes PR20 image preview before release. Stop and reconfirm if an architectural/feature conflict requires choosing between upstream and accepted fork behavior. No upstream push, no new credentials, no signed Android production release in this scope. PR20 currently implements engine/desktop preview; do not describe it as newly implemented mobile parity.
