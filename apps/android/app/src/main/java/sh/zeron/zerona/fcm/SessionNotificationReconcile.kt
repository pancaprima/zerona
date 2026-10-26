package sh.zeron.zerona.fcm

import uniffi.zeron_core.SessionRow
import uniffi.zeron_core.WorkspaceSnapshot

/** Pure workspace → read-state helpers for session notification reconciliation. */
object SessionNotificationReconcile {
    fun unseenByChatId(ws: WorkspaceSnapshot): Map<String, Boolean> {
        val out = linkedMapOf<String, Boolean>()
        fun add(row: SessionRow) {
            NotificationChatId.validate(row.id)?.let { id ->
                out[id] = row.unseen
            }
        }
        for (row in ws.front.pinned) add(row)
        for (section in ws.front.sections) {
            for (row in section.sessions) add(row)
        }
        for (row in ws.front.recent) add(row)
        for (row in ws.projectless) add(row)
        for (row in ws.archived) add(row)
        for (project in ws.projects) {
            for (row in project.sessions) add(row)
        }
        return out
    }

    fun readChatIdsForReconcile(
        ws: WorkspaceSnapshot,
        unseenByChatId: Map<String, Boolean> = unseenByChatId(ws),
    ): Set<String> = SessionNotificationReconcilePolicy.readChatIds(unseenByChatId, ws.synced)

    fun shouldCancelForChat(
        chatId: String,
        unseenByChatId: Map<String, Boolean>,
        workspaceSynced: Boolean,
    ): Boolean =
        SessionNotificationReconcilePolicy.shouldCancelForChat(chatId, unseenByChatId, workspaceSynced)
}
