# Android notification → session deep link

## Decisions

- **Foreground FCM**: `ZeronaFcmService` passes `data.chatId` into `Notifications.showSessionNotification`; the activity `PendingIntent` carries the same `"chatId"` extra as the system notification tap path (FCM `data` map).
- **Per-chat notifications**: notification id and `PendingIntent` request code use `chatId.hashCode()` (fallback `1` when missing) so extras are not merged across chats.
- **Intent one-shot**: `MainActivity` validates `chatId` (`NotificationChatId.validate`), stores it in `AppModel.pendingChatId`, then removes the extra (only on fresh `onCreate`, plus every `onNewIntent`) — same pattern as auth URI handling.
- **Navigation**: when signed in, not demo, core client ready, and `pendingChatId` set, `ZeronaApp` navigates to `session/{chatId}` via `nav.navigate("session/$chatId") { launchSingleTop = true }` (list rows use the same route without options). Pending is cleared after navigation or if that session is already top.
- **Sign-out**: explicit `signOut()` and `stopClient()` drop pending; unknown chat ids rely on existing `SessionScreen` behaviour.

## Out of scope

- Edge / `fcmPayload`, FCM registration, permission flow, iOS, Sessions list UI, session status marker, Gradle/CI workflow changes.

## Verification

- CI: Android module compiles.
- Device: tap notification with app **foreground** (local notification) and **background/killed** (FCM notification block); each opens the correct chat.
