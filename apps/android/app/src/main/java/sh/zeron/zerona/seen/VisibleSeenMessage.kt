package sh.zeron.zerona.seen

/**
 * Newest rendered transcript message eligible for a read receipt.
 * [messageAtMs] is `SessionMessageEntry.createdAt` (same domain as chat `lastMessageAt`).
 */
data class VisibleSeenMessage(
    val messageId: String,
    val messageAtMs: Long,
)

/** Row band in layout dp (same coordinates as [uniffi.zeron_core.RowPlacement]). */
data class PlacementBounds(
    val index: Int,
    val y: Float,
    val height: Float,
)

/**
 * Pick the newest visible message from row placements (max [VisibleSeenMessage.messageAtMs]).
 */
object VisibleSeenMessageSelector {
    /**
     * Indices of rows that intersect the viewport by at least [minVisibleDp], or the full row
     * height when the row is shorter than [minVisibleDp].
     */
    fun filterVisible(
        placements: List<PlacementBounds>,
        viewportTop: Float,
        viewportBottom: Float,
        minVisibleDp: Float = 24f,
    ): List<Int> =
        placements.mapNotNull { p ->
            val rowBottom = p.y + p.height
            val visibleHeight =
                (minOf(rowBottom, viewportBottom) - maxOf(p.y, viewportTop)).coerceAtLeast(0f)
            val threshold = minOf(minVisibleDp, p.height)
            if (visibleHeight >= threshold) p.index else null
        }

    fun newest(
        placementIndices: List<Int>,
        entryIdForIndex: (Int) -> String?,
        createdAtMs: (String) -> Long?,
    ): VisibleSeenMessage? {
        var best: VisibleSeenMessage? = null
        for (index in placementIndices) {
            val entryId = entryIdForIndex(index)?.takeIf { it.isNotEmpty() } ?: continue
            val at = createdAtMs(entryId) ?: continue
            val candidate = VisibleSeenMessage(entryId, at)
            if (best == null || candidate.messageAtMs > best.messageAtMs) {
                best = candidate
            }
        }
        return best
    }
}
