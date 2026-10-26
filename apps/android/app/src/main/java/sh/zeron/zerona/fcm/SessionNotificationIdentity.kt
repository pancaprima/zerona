package sh.zeron.zerona.fcm

/**
 * Stable notification tray and [PendingIntent] identity for session pushes.
 * Must stay aligned with [androidNotificationTagForChat] in edge `push-notify.ts`.
 */
object SessionNotificationIdentity {
    const val TAG_PREFIX = "zerona.chat."
    private const val FALLBACK_NOTIFICATION_ID = 1

    private const val PENDING_INTENT_SCHEME = "zerona"
    private const val PENDING_INTENT_HOST = "notification"

    fun notificationTag(chatId: String): String? {
        val id = NotificationChatId.validate(chatId) ?: return null
        return TAG_PREFIX + id
    }

    /** FNV-1a 32-bit; paired with [notificationTag] for collision-safe tray identity. */
    fun notificationId(chatId: String): Int {
        val value = NotificationChatId.validate(chatId) ?: return FALLBACK_NOTIFICATION_ID
        return fnv1aNotificationId(value)
    }

    /** Tray id used before per-chat tags (untagged [NotificationManager.notify]). */
    fun legacyUntaggedNotificationId(chatId: String): Int? {
        val value = NotificationChatId.validate(chatId) ?: return null
        return value.hashCode()
    }

    internal fun fnv1aNotificationId(validatedChatId: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (ch in validatedChatId) {
            hash = hash xor ch.code
            hash *= 0x01000193
        }
        val positive = hash and 0x7fffffff
        return if (positive == 0) FALLBACK_NOTIFICATION_ID else positive
    }

    fun pendingIntentUriString(chatId: String): String? {
        val id = NotificationChatId.validate(chatId) ?: return null
        return "$PENDING_INTENT_SCHEME://$PENDING_INTENT_HOST/$id"
    }
}
