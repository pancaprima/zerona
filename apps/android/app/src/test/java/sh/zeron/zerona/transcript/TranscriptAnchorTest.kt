package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAnchorTest {
    @Test
    fun followWhenNearBottom() {
        assertTrue(TranscriptAnchor.shouldFollowAfterUserScroll(950, 1000, 100f))
        assertFalse(TranscriptAnchor.shouldFollowAfterUserScroll(800, 1000, 100f))
    }

    @Test
    fun anchoredScrollY() {
        assertEquals(120, TranscriptAnchor.anchoredScrollY(100f, 20f))
        assertEquals(0, TranscriptAnchor.anchoredScrollY(-5f, 3f))
    }
}
