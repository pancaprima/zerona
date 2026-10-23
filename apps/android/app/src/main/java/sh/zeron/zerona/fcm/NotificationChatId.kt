package sh.zeron.zerona.fcm

/** Validates FCM / notification `chatId` extras before deep-link navigation. */
object NotificationChatId {
    private val ALLOWED = Regex("^[A-Za-z0-9-]{1,64}$")

    fun validate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!ALLOWED.matches(value)) return null
        return value
    }
}
