# Chat transcript UI polish (Android)

## Goal

Ship the chat-transcript motion and status polish in the **Android app** (Compose shell + native `TranscriptListView` rows). Desktop GPUI work in `crates/ui` is a separate surface and is **out of scope** for this patch.

## Requirements

| Item | Android target |
|------|----------------|
| Send status | Small indeterminate spinner beside the user bubble (not composer “Sending…” copy); TalkBack “Sending” on the row |
| Assistant entrance | `RowKind.MARKDOWN` rows slide in from the left once per stable row key |
| User entrance | `RowKind.USER` from lower-right with slight scale-up and subtle bounce; once per key; no replay on stream/version rebind; cancel on pool recycle; honor animator / reduce-motion |
| Transcript loading | Slim indeterminate bar under the top app bar (`TranscriptLoadingBar`) whenever transcript loading is active (initial load and pagination), not only the first empty frame; no fake percentage; keep error / pagination copy / empty behavior |
| Jump to latest | Icon-only `KeyboardDoubleArrowDown`; keep “Scroll to latest” content description |

## Insertion points

- `apps/android/.../ui/SessionScreen.kt` — loading bar, jump control
- `apps/android/.../ui/SessionViewModel.kt` — pending-send row keys, hydration gate for entrances, suppress sending banner
- `apps/android/.../ui/TranscriptLoadingBar.kt` — bar motion + colors
- `apps/android/.../transcript/TranscriptListView.kt` — entrance tracker, pending-send overlay wiring
- `apps/android/.../transcript/RowView.kt` — entrance transforms, send spinner layout
- `apps/android/.../transcript/TranscriptMessageMotion.kt` — paint math (ported from desktop `motion.rs`)
- `apps/android/.../transcript/TranscriptRowKey.kt` — FNV-1a keys for pending sends

## Verification (CI)

```bash
./apps/android/gradlew :app:testDebugUnitTest
```

Manual: send while host is slow, receive assistant reply, open chat while transcript is loading (first paint and “Loading earlier messages…”), toggle system reduce motion, scroll up for jump pill.

## Rollback

Revert the Kotlin files above and this plan. No migrations or feature flags.
