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

    @Test
    fun scrollToLatestHiddenAtBottomAndWhenNoOverflow() {
        val threshold = 240f
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(1000, 1000, threshold))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(0, 0, threshold))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(0, 200, threshold))
    }

    @Test
    fun scrollToLatestStrictlyAboveThreshold() {
        val threshold = 240f
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(760, 1000, threshold))
        assertTrue(TranscriptAnchor.shouldShowScrollToLatest(759, 1000, threshold))
        assertTrue(TranscriptAnchor.shouldShowScrollToLatest(0, 500, threshold))
    }

    @Test
    fun scrollToLatestClampsNegativeDistance() {
        val threshold = 10f
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(50, 40, threshold))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(100, 50, threshold))
    }

    @Test
    fun scrollToLatestEdgeIntsAndInvalidThreshold() {
        val threshold = 240f
        val max = Int.MAX_VALUE
        assertTrue(TranscriptAnchor.shouldShowScrollToLatest(max - 500, max, threshold))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(max, max, threshold))
        assertTrue(TranscriptAnchor.shouldShowScrollToLatest(-1000, 1000, 10f))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(0, 1000, Float.NaN))
        assertFalse(TranscriptAnchor.shouldShowScrollToLatest(0, 1000, -1f))
    }
}
