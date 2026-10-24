# Chat transcript motion (Android PR scope)

## Goal

Ship **jump-to-latest** affordance polish and **send/receive message motion** on the Android native transcript (`TranscriptListView` / `RowView`) without unrelated shell redesign or transcript loading-bar work.

## Requirements

| Item | Target |
|------|--------|
| Jump to latest | Icon-only `KeyboardDoubleArrowDown`; content description **Scroll to latest**; existing 240 dp threshold and `jumpToLatest()` behavior unchanged |
| Send status | Indeterminate spinner beside the user bubble while `SendState.SENDING`; TalkBack **Sending…** on the row; no composer **Sending…** banner for that state |
| Assistant entrance | `RowKind.MARKDOWN` slides in from the left once per stable row key at the live tail |
| User entrance | `RowKind.USER` from lower-right with slight scale-up and subtle bounce; once per key; no replay on hydration baseline; honor animator / reduce-motion |
| Streaming | Keep `RowStreamMotionPolicy` veil reveal for in-flight assistant text growth (PR #34 baseline) |

## Insertion points

- `apps/android/.../ui/SessionScreen.kt` — jump control
- `apps/android/.../ui/SessionViewModel.kt` — pending-send row keys, hydration gate, banner suppression
- `apps/android/.../transcript/TranscriptListView.kt` — entrance tracker, pending-send wiring, Choreographer ticks
- `apps/android/.../transcript/RowView.kt` — entrance transforms, send spinner layout
- `apps/android/.../transcript/TranscriptMessageMotion.kt` — paint math (aligned with desktop `motion.rs`)
- `apps/android/.../transcript/TranscriptMessageEntranceTracker.kt` — live-tail-only entrances
- `apps/android/.../transcript/TranscriptRowKey.kt` — FNV-1a keys for pending sends
- `apps/android/.../transcript/TranscriptUserBubbleLayout.kt` — spinner placement

## Verification

```bash
./apps/android/gradlew :app:testDebugUnitTest --tests 'sh.zeron.zerona.transcript.TranscriptMessage*' --tests 'sh.zeron.zerona.transcript.TranscriptRowKeyTest'
./apps/android/scripts/selfcheck-native-transcript.sh
```

Manual: send while host is slow (spinner, no banner), receive assistant reply (entrance), scroll up >240 dp (jump pill), tap jump, toggle system reduce motion.

## Rollback

Revert the Kotlin files above and this plan. No migrations or feature flags.
