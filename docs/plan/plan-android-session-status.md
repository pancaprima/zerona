# Plan: Android sessions-list status marker

Status: implementation delegated (uncompiled until CI is green; not device-verified).
Branch: `feat/android-fcm` (PR #33), same-branch follow-up.

## Request
User (2026-10-23, after testing the FCM APK): "di list session bisa dikasih marker juga ya chat nya statusnya apa".
Push notifications already show the session title, so only the list marker is in scope here.

## Decisions
- Kotlin-only. `uniffi.zeron_core.SessionRow` already exposes `indicator` (`ChatIndicator`: WORKING, AWAITING_INPUT, ERRORED, COMPLETED, IDLE; staleness-gated at 45 s in the core), `unseen`, and `sendState` (`SendState`: SENDING, QUEUED, FAILED). No Rust/FFI/edge change.
- Mirror iOS (`SessionCell.swift` corner + `StatusGlyph.swift`) precedence: sendFailed -> "Failed"; WORKING -> "Working"; AWAITING_INPUT -> "Needs input"; ERRORED -> "Failed"; COMPLETED (+ unseen) -> "Done"; IDLE -> no marker (time label only).
- Keep the accepted calm lilac list design. The existing 8dp project-accent square stays purely decorative. The status marker is separate: a small dot + short label (labelSmall, 11sp) placed with the right-aligned time label, in a restrained semantic colour (tertiary/primary/error from the theme, no new saturated palette). Idle rows look exactly as before.
- Accessibility: marker text is the label itself plus a contentDescription on the row ("<title>, <status>, <time>"); do not rely on colour alone.
- Freshness: the core derives the indicator at refresh time; add a light ~30 s refresh of the workspace list while the sessions screen is visible and the user is signed in (like iOS's 30 s timer) so WORKING ages out and time labels update. Do not touch the open-session refresh path.
- Pure mapping function (indicator, unseen, sendState) -> marker kind lives in its own small Kotlin file with a JVM unit test registered in `scripts/android/test-design-helpers.py` (TEST_SOURCES + TEST_CLASSES).

## Out of scope
Notification deep link / per-chat notification id (possible follow-up), iOS changes, Rust changes, redesign of the list.

## Verification
Source review + helper tests only; CI `Android app` must compile; user confirms on device.
