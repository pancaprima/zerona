package sh.zeron.zerona.fcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationChatIdTest {
    @Test
    fun acceptsTypicalId() {
        assertEquals("abc-123", NotificationChatId.validate("abc-123"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("chat1", NotificationChatId.validate("  chat1  "))
    }

    @Test
    fun rejectsBlank() {
        assertNull(NotificationChatId.validate(""))
        assertNull(NotificationChatId.validate("   "))
        assertNull(NotificationChatId.validate(null))
    }

    @Test
    fun rejectsTooLong() {
        assertNull(NotificationChatId.validate("a".repeat(65)))
    }

    @Test
    fun rejectsInvalidCharacters() {
        assertNull(NotificationChatId.validate("chat/id"))
        assertNull(NotificationChatId.validate("chat id"))
    }
}
