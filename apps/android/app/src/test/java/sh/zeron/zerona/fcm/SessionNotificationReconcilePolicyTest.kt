package sh.zeron.zerona.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNotificationReconcilePolicyTest {
    @Test
    fun cancelsOnlySyncedReadChatsPresentInSnapshot() {
        val map = mapOf("read-chat" to false, "unread-chat" to true)
        assertTrue(
            SessionNotificationReconcilePolicy.shouldCancelForChat("read-chat", map, workspaceSynced = true),
        )
        assertFalse(
            SessionNotificationReconcilePolicy.shouldCancelForChat("unread-chat", map, workspaceSynced = true),
        )
        assertFalse(
            SessionNotificationReconcilePolicy.shouldCancelForChat("missing-chat", map, workspaceSynced = true),
        )
    }

    @Test
    fun neverCancelsWhenWorkspaceNotSynced() {
        val map = mapOf("read-chat" to false)
        assertFalse(
            SessionNotificationReconcilePolicy.shouldCancelForChat("read-chat", map, workspaceSynced = false),
        )
        assertEquals(emptySet<String>(), SessionNotificationReconcilePolicy.readChatIds(map, workspaceSynced = false))
    }

    @Test
    fun readChatIdsPreservesUnrelatedUnread() {
        val map = mapOf("a" to false, "b" to true)
        assertEquals(setOf("a"), SessionNotificationReconcilePolicy.readChatIds(map, workspaceSynced = true))
    }

    @Test
    fun reconnectSnapshotClearsOnlyKnownReadRows() {
        val afterSync = mapOf("c1" to false, "c2" to true)
        val read = SessionNotificationReconcilePolicy.readChatIds(afterSync, workspaceSynced = true)
        assertEquals(setOf("c1"), read)
        assertFalse(SessionNotificationReconcilePolicy.shouldCancelForChat("c2", afterSync, true))
        assertFalse(SessionNotificationReconcilePolicy.shouldCancelForChat("c3", afterSync, true))
    }

    @Test
    fun parsesChatIdFromNotificationTag() {
        assertEquals(
            "chat-9",
            SessionNotificationReconcilePolicy.chatIdFromNotificationTag("zerona.chat.chat-9"),
        )
        assertEquals(null, SessionNotificationReconcilePolicy.chatIdFromNotificationTag("other.tag"))
    }
}
