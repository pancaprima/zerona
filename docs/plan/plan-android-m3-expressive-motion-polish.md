# Zerona Android: M3 Expressive motion polish (12 interactions)

Status: implementation in this worktree (`feat/android-m3-expressive`). Builds on committed M1–M3 shell motion (`ZeronaMotionPolicy`, `ZeronaMotion.kt`, `ZeronaApp` nav, `ComposerBar`, `ExpressiveLoading.kt`). Does **not** amend `plan-android-m3-expressive-motion.md`. No merge/release.

Research date: 24 October 2026 UTC.

## Goal

Give each listed user-visible moment a **distinct but coherent** expressive motion using `MaterialTheme.motionScheme` (Compose) and a minimal native bridge (transcript rows only). Preserve calm palette, opaque Edge Dock, reduced-motion / animator-scale-zero static paths, and existing architecture.

## Behaviour map

| # | Moment | Motion language | Primary insertion |
| --- | --- | --- | --- |
| 1 | Session list initial load → loaded | Centered `ZeronaInitialLoading` while workspace pending; list **effects fade + slight upward spatial** reveal when `workspaceReady` | `SessionsListPresentation`, `AppUiState.workspaceReady`, `SessionsScreen` |
| 2 | Session status appears / updates | **Horizontal expand + fade** on first show; `AnimatedContent` **crossfade** on label/kind change | `SessionRowItem` |
| 3 | Profile press + menu open | Existing **tactile compression** on press; **scale-from-anchor** (top-end) on menu surface while open | `SessionsListHeader` |
| 4 | Select session → chat | Existing **directional slide (¼ width) + effects fade** on `NavHost` | `ZeronaApp.kt` (unchanged) |
| 5 | Incoming message (transcript) | Native **effects-first fade + small upward translation** (once per row key); streaming uses **one** veil reveal per message key (prefix updates refresh draw model only; same-key rebind does not cancel entrance) | `ZeronaNativeMotion`, `RowStreamMotionPolicy`, `RowView`, `TranscriptListView` |
| 6 | Sent message in transcript | Native **spatial-forward nudge** (user rows: slightly stronger Y than assistant) | same |
| 7 | Transcript loading | Existing **breathe + entrance scale** loading shell | `ExpressiveLoading` / `SessionScreen` (unchanged) |
| 8 | Transcript finished loading | **Effects fade + subtle scale** on `AndroidView` when shell dismisses | `SessionScreen` |
| 9 | Send status (sending / queued / error) | `AnimatedContent` on banner text with **vertical slide**; error uses **fast effects** emphasis (colour already animated) | `ComposerBar`, `ComposerPresentation` |
| 10 | Go-to-latest show / hide | **Fast spatial slide + fade** enter; **slide down + fade** exit (not symmetric fade-only) | `SessionScreen` |
| 11 | Send button press | **Tactile compression + colour + in-flight icon nudge** | `ComposerBar` (unchanged) |
| 12 | Leave chat → session list | **Pop exit slide + fade** on `NavHost` | `ZeronaApp.kt` (unchanged) |

## Non-goals

- Tool chevron / shimmer changes (deferred M4 in parent plan).
- List stagger on every refresh.
- Shared-element row → transcript morph.
- Second global duration scaler (Compose `MotionDurationScale` + Settings animator scale only).

## State / policy

- `AppUiState.workspaceReady`: set `true` on first `refreshWorkspace`; cleared on `stopClient`. Drives honest initial wait for signed-in live users without treating “zero sessions” as infinite load.
- `SessionsListPresentation`: pure helpers for initial wait + loaded reveal gating.
- `ComposerPresentation.sendStatusBannerKey`: stable key for `AnimatedContent` on composer banner transitions.
- `ZeronaNativeMotion`: reads `Settings.Global.ANIMATOR_DURATION_SCALE`; matches `ZeronaMotionPolicy.nativeOrnamentalMotionEnabled`.
- `TranscriptListView.entranceAnimatedKeys`: one entrance per row key per epoch; cleared in `invalidateDrawCache()`.
- `RowStreamMotionPolicy` + `RowView.streamRevealPhase`: at most one stream veil `ValueAnimator` per row key; further prefix revisions update settled/fresh canvases without restarting alpha; after reveal completes (`Done`), later growth draws on the main canvas without another fade; same-key non-growth rebinds (version/width-only stale path in `TranscriptListView`) preserve an in-flight reveal while the animator is running; if phase is `Animating` but the animator has stopped, rebind settles to `Done` with full text (no stuck veil); `None`/`Done` stay unchanged on non-growth; only key change or `resetForPool` returns phase to `None`; `resetForPool` / animator-scale-zero unchanged.

## Verification

- JVM: `SessionsListPresentationTest`, `ComposerPresentationTest` (banner key), `RowStreamMotionPolicyTest`, existing `ZeronaMotionPolicyTest`.
- Gradle: `:app:testDebugUnitTest` when core artifacts available; else `scripts/android/test-design-helpers.py` for pure Kotlin.
- Device: all 12 moments, animator scale 0/1, rapid back/send, long transcript — **required for feel**; not claimed here.

## Rollback

Revert polish commit(s) only; `workspaceReady` is UI-only with default reset on sign-out.
