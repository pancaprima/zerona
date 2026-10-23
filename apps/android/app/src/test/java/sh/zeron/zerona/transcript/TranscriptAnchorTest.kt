package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAnchorTest {
    @Test
    fun contentHeightPx() {
        assertEquals(500, TranscriptAnchor.contentHeightPx(500, 100))
        assertEquals(600, TranscriptAnchor.contentHeightPx(500, 600))
        assertEquals(1, TranscriptAnchor.contentHeightPx(0, 0))
        assertEquals(1, TranscriptAnchor.contentHeightPx(-10, -20))
        assertEquals(42, TranscriptAnchor.contentHeightPx(42, 42))
        assertEquals(Int.MAX_VALUE, TranscriptAnchor.contentHeightPx(Int.MAX_VALUE, 100))
        assertEquals(Int.MAX_VALUE, TranscriptAnchor.contentHeightPx(100, Int.MAX_VALUE))
        assertEquals(
            Int.MAX_VALUE,
            TranscriptAnchor.contentHeightPx(Int.MAX_VALUE, Int.MAX_VALUE),
        )
    }

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

    @Test
    fun layoutWindowDpAtTail() {
        // Pixel 3–like: scrollY=445314, density=2.75, viewport=1483px, overscan=700dp
        val density = 2.75f
        val scrollY = 445314
        val viewportPx = 1483
        val overscanDp = 700f
        val viewHeightDp = viewportPx / density
        val scrollDp = scrollY / density
        val expectedY0 = scrollDp - overscanDp
        val expectedY1 = scrollDp + viewHeightDp + overscanDp
        val window =
            TranscriptAnchor.layoutWindowDp(scrollY, density, viewportPx, overscanDp)
        assertEquals(expectedY0, window.first, 0.01f)
        assertEquals(expectedY1, window.second, 0.01f)
    }

    @Test
    fun layoutWindowDpZeroViewportUsesFallback() {
        val overscanDp = 700f
        val window = TranscriptAnchor.layoutWindowDp(1000, 2f, 0, overscanDp, 400f)
        assertEquals(500f - overscanDp, window.first, 0.01f)
        assertEquals(500f + 400f + overscanDp, window.second, 0.01f)
    }

    @Test
    fun viewportIntersectsRowBandTailBugScenario() {
        val scrollY = 445314
        val viewportPx = 1483
        val vpBottom = scrollY + viewportPx
        val staleBandTop = 366908
        val staleBandBottom = 370696
        assertFalse(
            TranscriptAnchor.viewportIntersectsRowBand(
                scrollY,
                vpBottom,
                staleBandTop,
                staleBandBottom,
            ),
        )
        val tailBandTop = 445000
        val tailBandBottom = 446749
        assertTrue(
            TranscriptAnchor.viewportIntersectsRowBand(
                scrollY,
                vpBottom,
                tailBandTop,
                tailBandBottom,
            ),
        )
    }

    @Test
    fun extendSettleDeadlineInactiveStaysZero() {
        assertEquals(
            0L,
            TranscriptAnchor.extendSettleDeadline(1000L, 500L, 0L, 600L, 5000L),
        )
    }

    @Test
    fun extendSettleDeadlineExtendsToNowPlusExtend() {
        assertEquals(
            1600L,
            TranscriptAnchor.extendSettleDeadline(1000L, 500L, 1200L, 600L, 5000L),
        )
    }

    @Test
    fun extendSettleDeadlineNeverDecreases() {
        assertEquals(
            3000L,
            TranscriptAnchor.extendSettleDeadline(2000L, 500L, 3000L, 600L, 5000L),
        )
    }

    @Test
    fun extendSettleDeadlineCappedAtArmedPlusCap() {
        assertEquals(
            6000L,
            TranscriptAnchor.extendSettleDeadline(5800L, 1000L, 5900L, 600L, 5000L),
        )
    }

    @Test
    fun extendSettleDeadlineCapBelowCurrentDeadline() {
        assertEquals(
            1100L,
            TranscriptAnchor.extendSettleDeadline(2000L, 1000L, 5000L, 600L, 100L),
        )
    }

    @Test
    fun extendSettleDeadlineExtremeLongWithoutOverflow() {
        val now = Long.MAX_VALUE - 10L
        val armed = Long.MAX_VALUE - 100L
        assertEquals(
            Long.MAX_VALUE,
            TranscriptAnchor.extendSettleDeadline(now, armed, 1000L, 600L, 5000L),
        )
        assertEquals(
            Long.MAX_VALUE,
            TranscriptAnchor.extendSettleDeadline(
                Long.MAX_VALUE - 1L,
                0L,
                Long.MAX_VALUE - 100L,
                600L,
                Long.MAX_VALUE,
            ),
        )
    }
}
