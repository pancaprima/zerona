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
}
