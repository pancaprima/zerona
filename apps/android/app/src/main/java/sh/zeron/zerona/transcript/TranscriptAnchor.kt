package sh.zeron.zerona.transcript

/**
 * Pure helpers for follow-tail vs scroll anchoring.
 */
object TranscriptAnchor {
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
}
