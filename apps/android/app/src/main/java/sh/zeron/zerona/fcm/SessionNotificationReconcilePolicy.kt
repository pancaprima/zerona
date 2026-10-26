package sh.zeron.zerona.fcm

/** Pure read/dismiss rules for session notifications (no Android / UniFFI). */
object SessionNotificationReconcilePolicy {
    fun chatIdFromNotificationTag(tag: String?): String? {
        val value = tag ?: return null
        if (!value.startsWith(SessionNotificationIdentity.TAG_PREFIX)) return null
        return NotificationChatId.validate(value.removePrefix(SessionNotificationIdentity.TAG_PREFIX))
    }

    fun readChatIds(
        unseenByChatId: Map<String, Boolean>,
        workspaceSynced: Boolean,
    ): Set<String> {
        if (!workspaceSynced) return emptySet()
        return unseenByChatId.filterValues { !it }.keys
    }

    fun shouldCancelForChat(
        chatId: String,
        unseenByChatId: Map<String, Boolean>,
        workspaceSynced: Boolean,
    ): Boolean {
        if (!workspaceSynced) return false
        val validated = NotificationChatId.validate(chatId) ?: return false
        val unseen = unseenByChatId[validated] ?: return false
        return !unseen
    }
}
