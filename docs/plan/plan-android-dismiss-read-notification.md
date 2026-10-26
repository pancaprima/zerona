# Plan: Dismiss Android push notification when its chat is read

## Status
**Implemented** on branch `feat/android-dismiss-read-notification` (base `5be5044acf38cf8b42acaa0a55a00ebc8052accd`). VPS verification pass completed 2026-10-26 (no Gradle/device build here).

### Root cause (verified)
1. **No dismissal on read** — tray posts were never cancelled when a session became read; `setViewAttached(true)` ran before `TranscriptView.attach`, so attach failure could mark seen without a reliable open path.
2. **Tray identity mismatch** — posts used untagged `String.hashCode()` notification ids (collision-prone); cancel/reconcile must use per-chat **tag** `zerona.chat.{chatId}` + FNV-1a id, and **legacy untagged** `hashCode()` cancel for notifications already in the tray.
3. **API 23+ reconcile gap** — scanning only tagged `activeNotifications` skipped all legacy/untagged tray items; fixed by also calling `cancelSessionNotification` for every synced snapshot row with `unseen == false`.
4. **Cross-device desktop read** — no separate Android push; uses existing `WorkspaceChanged` → `refreshWorkspace` when `WorkspaceSnapshot.synced` and row `unseen` updates from `chats.lastSeenAt` (same signal as session list). Requires edge FCM `android.notification.tag` aligned with the app for **background** system-rendered pushes.

### Implementation checklist
| Item | Status |
|------|--------|
| Collision-safe per-chat notification tag + FCM `android.notification.tag` | Done (source + edge tests) |
| Collision-safe `PendingIntent` via `zerona://notification/{chatId}` data URI | Done (source tests) |
| `setViewAttached(true)` only after successful `TranscriptView.attach` | Done (`SessionViewModel`) |
| Dismiss matching notification after successful attach | Done |
| Cross-device reconcile via `WorkspaceSnapshot` / `synced` + row `unseen` | Done (`AppModel` on every `refreshWorkspace`) |
| No cancel when chat absent from snapshot or workspace not `synced` | Done (policy tests) |
| Stale push while chat already read/open | Done (`workspaceTick` / `refreshTick` in `SessionViewModel`) |
| Legacy untagged tray posts (`hashCode` id) | Done (`legacyUntaggedNotificationId` + reconcile fallback) |
| Device tray / background FCM system render | **Device verification required** |
| Untagged FCM until edge tag deploys | Tray may persist until tag-aligned push or manual clear |

### Tests run (automated)
- `edge` — `npm run test:unit -- src/push-notify.test.ts` passed **14/14**
- `bash apps/android/scripts/selfcheck-native-transcript.sh` passed and reports **194** `@Test` annotations in **26** classes as static inventory only
- `python3 scripts/android/test-design-helpers.py` failed before compilation because `/root/.cache/zerona-jvm/kotlin-stdlib-1.9.23.jar` is missing, so Kotlin helper tests did not run
- Android CI and device verification remain pending

### Limitations (unchanged)
- No dismissal while the Android process is fully stopped until the next successful workspace sync/reconnect.
- Reconciliation trusts `WorkspaceSnapshot.synced`; unsynced snapshots skip tray enumeration cancel.
- API &lt; 23 cannot enumerate active notifications; reconcile cancels only chats present in the synced unseen map (no unknown-tag scan).

## Source of truth reviewed
Zerona fork `pancaprima/zerona`, fetched `origin/main` at `5be5044acf38cf8b42acaa0a55a00ebc8052accd` (2026-10-25).

## Review outcome and implementation decision
Cross-device read uses existing `chats.lastSeenAt` → Android `WorkspaceChanged` / session `unseen`. No new read protocol.

## Goal
When a user actually reads a chat, remove only that chat's Android push notification(s), including when opened on Android or read on desktop. Preserve notifications for other chats.

## Recommended behavior (as implemented)
1. Dismiss after transcript attachment succeeds; not on navigation alone or attach failure.
2. Per-chat tag `zerona.chat.{chatId}` + FNV-1a notification id; FCM uses the same tag.
3. `setAutoCancel(true)` preserved; explicit cancel on successful open is idempotent.
4. Open/read chat clears stale tray items when `unseen` becomes false.
5. Local cancellation only.

## Cross-device desktop-read requirement
Reconcile on initial `refreshWorkspace` and each `WorkspaceChanged` when `ws.synced` is true, matching tag/id to posting. Chats not in the snapshot are not cancelled.

## Tests and acceptance criteria
See checklist above; device should confirm unrelated notifications remain after desktop read of one chat.

## Out of scope
Server push deletion, iOS, blanket notification clear, new read protocol.
