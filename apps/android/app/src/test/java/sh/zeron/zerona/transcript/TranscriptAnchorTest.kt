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

    @Test
    fun distanceFromBottomAndResettle() {
        assertEquals(250L, TranscriptAnchor.distanceFromBottom(750, 1000))
        assertEquals(0L, TranscriptAnchor.distanceFromBottom(1000, 1000))
        assertEquals(0L, TranscriptAnchor.distanceFromBottom(0, 0))
        assertTrue(TranscriptAnchor.shouldResettleToBottom(500, 1000, 2))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(1000, 1000, 2))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(999, 1000, 2))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(998, 1000, 2))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(0, 0, 2))
    }

    @Test
    fun resettleClampsScrollY() {
        assertFalse(TranscriptAnchor.shouldResettleToBottom(1500, 1000, 2))
        assertEquals(0L, TranscriptAnchor.distanceFromBottom(1500, 1000))
        assertTrue(TranscriptAnchor.shouldResettleToBottom(-100, 1000, 2))
        assertEquals(1000L, TranscriptAnchor.distanceFromBottom(-100, 1000))
    }

    @Test
    fun resettleAndDistanceExtremeInts() {
        val max = Int.MAX_VALUE
        assertEquals(500L, TranscriptAnchor.distanceFromBottom(max - 500, max))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(max, max, 2))
        assertFalse(TranscriptAnchor.shouldResettleToBottom(max - 1, max, 2))
        assertTrue(TranscriptAnchor.shouldResettleToBottom(max - 3, max, 2))
        assertEquals(
            max.toLong(),
            TranscriptAnchor.distanceFromBottom(Int.MIN_VALUE, max),
        )
    }

    @Test
    fun settleActiveWindow() {
        assertFalse(TranscriptAnchor.settleActive(100L, 0L))
        assertTrue(TranscriptAnchor.settleActive(100L, 200L))
        assertFalse(TranscriptAnchor.settleActive(200L, 200L))
        assertFalse(TranscriptAnchor.settleActive(300L, 200L))
    }
}
