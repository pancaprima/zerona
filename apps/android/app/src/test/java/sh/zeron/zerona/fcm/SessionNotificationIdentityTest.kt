package sh.zeron.zerona.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionNotificationIdentityTest {
    @Test
    fun tagMatchesEdgeConvention() {
        assertEquals("zerona.chat.chat-1", SessionNotificationIdentity.notificationTag("chat-1"))
    }

    @Test
    fun distinctChatsGetDistinctTagsEvenWhenHashCodesCollide() {
        val a = "FB"
        val b = "Ea"
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(
            SessionNotificationIdentity.notificationTag(a),
            SessionNotificationIdentity.notificationTag(b),
        )
    }

    @Test
    fun pendingIntentUrisDifferPerChat() {
        val uriA = SessionNotificationIdentity.pendingIntentUriString("chat-a")
        val uriB = SessionNotificationIdentity.pendingIntentUriString("chat-b")
        assertNotNull(uriA)
        assertNotNull(uriB)
        assertNotEquals(uriA, uriB)
        assertEquals("zerona://notification/chat-a", uriA)
        assertEquals("zerona://notification/chat-b", uriB)
    }

    @Test
    fun notificationIdIsStableForSameChat() {
        val id1 = SessionNotificationIdentity.notificationId("stable-id")
        val id2 = SessionNotificationIdentity.notificationId("stable-id")
        assertEquals(id1, id2)
        assert(id1 > 0)
    }

    @Test
    fun legacyUntaggedIdMatchesPreTagPosting() {
        assertEquals("chat-1".hashCode(), SessionNotificationIdentity.legacyUntaggedNotificationId("chat-1"))
        assertNotEquals(
            SessionNotificationIdentity.notificationId("chat-1"),
            SessionNotificationIdentity.legacyUntaggedNotificationId("chat-1"),
        )
    }
}
