# Plan: delayed unread push for Android (T2)

Status: **in progress** (branch `feat/unread-push-delay`, PR to `main`, do not merge without the merge gate).
Requested by: Prima via Rora (T2 spec), confirmed directly by Prima on 2026-10-27. Review notes: Carmen.

## Behavior (spec)
- Triggers: a chat run finishes (`done`), fails (`failed`) or needs input (`input`).
- A message counts as **read** only once that exact message has been shown on screen on desktop or on the phone. The app being open is not enough.
- 5 minutes after the triggering message, if it is still unread on **both** devices, send **one** push to the phone for that chat, summarizing all its unread triggers. If it is read on either device first, send nothing.
- Replaces the current instant push for done / failed / input. Tap opens the chat (existing). Android only, existing Firebase (FCM) path.

## Decisions (analysis: `registry-room.ts`, `push-notify.ts`, `chat-room.ts`, clients)
1. **Where**: `RegistryRoom` (it already calls `notifySessions` and owns `push_targets`). ChatRoom only shows the multi-job alarm pattern (`Math.min(wake.next_at, backup)` + re-arm in `finally`).
2. **iOS stays instant**: APNs targets keep the current immediate send; only `kind === "fcm"` targets are deferred. (Spec says Android only.)
3. **Trigger identity**: sessions-level, not transcript message id. `done` -> `lastCompletedTurn`; `input`/`failed` -> status transition at `updatedAt`. Each trigger stores `created_at` (ms, edge clock).
4. **Read receipt**: clients report the newest message **actually rendered on screen** as `{chatId, messageId, messageAt}` to a new `POST /seen?device=<id>` on the RegistryRoom (same pattern as `/push-target`). A trigger is read when any device's `messageAt >= trigger.created_at`. Receipts are per (chat, device), monotonic (never move backwards), kept in a small SQL table.
5. **Alarm**: one durable alarm, `next = min(earliest push due, daily backup/GC due)`; `alarm()` runs due push jobs first, then the daily job only when it is due, then re-arms. The existing "idle: stop the chain" behaviour of the daily job is preserved.
6. **Prefs** stay per category (`done`/`input`/`failed`); a deferred push is sent only if at least one unread trigger's category is enabled for that device.
7. **Summary copy**: 1 trigger -> same body as today ("Run finished" / "Waiting on your input" / "Run failed"); N triggers -> "N updates: <most urgent first>" (input > failed > done), title = chat title.
8. **Cleanup**: archived/side/deleted chats drop their queue; GC removes receipts older than the daily GC horizon.
9. Stale guard (`SESSION_STALE_MS`) is applied at enqueue time (same as today), not again at send time.

## Work breakdown (one branch, reviewed in commits)
1. **Edge** (`edge/src/registry-room.ts`, `push-notify.ts`, tests): queue + `/seen` + multi-job alarm + deferred FCM send. Tests (workerd + unit): read on desktop before 5 min -> no push; read on phone -> no push; unread on both -> exactly one push per chat; several messages -> one push; daily cleanup still runs; iOS target still instant.
2. **Client plumbing** (`crates/client`): `report_message_seen(chat_id, message_id, message_at_ms)` + URL helper + UniFFI export.
3. **Android** (`apps/android`): report newest rendered message from the session transcript (not just `setViewAttached`).
4. **Desktop** (`crates/ui`, engine RPC): report newest rendered transcript item for the selected chat in the focused window.

## Verification
- VPS cannot build; all tests run in CI (`npm test` in edge, Gradle, Rust). Merge gate: CI green at the same SHA **and** a real-device/desktop check or Prima saying it was tested (this is user-visible behavior).

## Open risks
- Clock skew between device `messageAt` and edge `created_at` (mitigate: compare against message timestamps from the edge-side log where available; document).
- Desktop "on screen" is stricter than `mark_chat_seen`; start with newest rendered item of the selected chat while the window is focused.
