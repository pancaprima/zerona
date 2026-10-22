package sh.zeron.zerona.transcript

/**
 * Pure helpers for follow-tail vs scroll anchoring.
 */
object TranscriptAnchor {
    fun contentHeightPx(
        totalPx: Int,
        lastRowBottomPx: Int,
    ): Int {
        val result = maxOf(1L, totalPx.toLong(), lastRowBottomPx.toLong())
        return if (result > Int.MAX_VALUE) Int.MAX_VALUE else result.toInt()
    }

    fun shouldFollowAfterUserScroll(
        scrollY: Int,
        maxScroll: Int,
        thresholdPx: Float,
    ): Boolean = scrollY >= maxScroll - thresholdPx

    fun anchoredScrollY(
        placementY: Float,
        anchorDelta: Float,
    ): Int = (placementY + anchorDelta).toInt().coerceAtLeast(0)

    /** True when content overflows and the viewport is more than [thresholdPx] above the bottom. */
    fun shouldShowScrollToLatest(
        scrollY: Int,
        maxScroll: Int,
        thresholdPx: Float,
    ): Boolean {
        if (maxScroll <= 0) return false
        if (thresholdPx < 0f || thresholdPx.isNaN()) return false
        val clampedScrollY = scrollY.coerceIn(0, maxScroll)
        val distanceFromBottom =
            (maxScroll.toLong() - clampedScrollY.toLong()).coerceAtLeast(0L)
        return distanceFromBottom > thresholdPx
    }

    fun distanceFromBottom(
        scrollY: Int,
        maxScroll: Int,
    ): Long {
        if (maxScroll <= 0) return 0L
        val clampedScrollY = scrollY.coerceIn(0, maxScroll)
        return (maxScroll.toLong() - clampedScrollY.toLong()).coerceAtLeast(0L)
    }

    fun shouldResettleToBottom(
        scrollY: Int,
        maxScroll: Int,
        epsilonPx: Int,
    ): Boolean {
        if (maxScroll <= 0) return false
        val clampedScrollY = scrollY.coerceIn(0, maxScroll)
        return clampedScrollY < maxScroll - epsilonPx
    }

    fun settleActive(
        nowMs: Long,
        deadlineMs: Long,
    ): Boolean {
        if (deadlineMs == 0L) return false
        return nowMs < deadlineMs
    }

    fun extendSettleDeadline(
        nowMs: Long,
        armedAtMs: Long,
        currentDeadlineMs: Long,
        extendMs: Long,
        capMs: Long,
    ): Long {
        if (currentDeadlineMs == 0L) return 0L
        val capDeadline =
            if (capMs >= Long.MAX_VALUE - armedAtMs) {
                Long.MAX_VALUE
            } else {
                armedAtMs + capMs
            }
        val extended =
            if (extendMs >= Long.MAX_VALUE - nowMs) {
                Long.MAX_VALUE
            } else {
                nowMs + extendMs
            }
        val candidate = maxOf(currentDeadlineMs, extended)
        return minOf(candidate, capDeadline)
    }
}
