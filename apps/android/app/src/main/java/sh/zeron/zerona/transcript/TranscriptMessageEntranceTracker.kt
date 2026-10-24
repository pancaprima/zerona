package sh.zeron.zerona.transcript

import uniffi.zeron_core.RowKind

/**
 * Decides which row keys earn a one-shot entrance (live tail arrivals only).
 */
class TranscriptMessageEntranceTracker {
    private val knownKeys = mutableSetOf<ULong>()
    private var replaySealed = false
    private val startUptimeMs = mutableMapOf<ULong, Long>()
    private val completedKeys = mutableSetOf<ULong>()

    fun reset() {
        knownKeys.clear()
        replaySealed = false
        startUptimeMs.clear()
        completedKeys.clear()
    }

    fun sealReplayBaseline(keys: Collection<ULong>) {
        knownKeys.addAll(keys)
        replaySealed = true
    }

    /** Hydrated replay with an empty transcript — enables entrances for the first live rows. */
    fun sealReplayEmpty() {
        replaySealed = true
    }

    fun noteRow(
        key: ULong,
        yDp: Float,
        rowBottomDp: Float,
        kind: RowKind,
        scrollTopDp: Float,
        viewportHeightDp: Float,
        nowUptimeMs: Long,
        reduceMotion: Boolean,
    ) {
        if (!animates(kind)) return
        if (key in completedKeys) return
        if (key in startUptimeMs) return

        val firstSight = key !in knownKeys
        knownKeys.add(key)

        if (!replaySealed) return
        if (!firstSight) return

        if (!isLiveArrival(yDp, rowBottomDp, scrollTopDp, viewportHeightDp)) {
            completedKeys.add(key)
            return
        }

        if (reduceMotion) {
            completedKeys.add(key)
            return
        }

        startUptimeMs[key] = nowUptimeMs
    }

    fun isLiveArrival(
        yDp: Float,
        rowBottomDp: Float,
        scrollTopDp: Float,
        viewportHeightDp: Float,
    ): Boolean {
        if (rowBottomDp < scrollTopDp - HISTORY_MARGIN_DP) return false
        val viewportBottom = scrollTopDp + viewportHeightDp
        if (yDp >= viewportBottom - TAIL_LIVE_MARGIN_DP) return true
        return rowBottomDp >= viewportBottom - TAIL_LIVE_MARGIN_DP
    }

    fun activeStarts(): Map<ULong, Long> = startUptimeMs.toMap()

    fun markCompleted(key: ULong) {
        startUptimeMs.remove(key)
        completedKeys.add(key)
    }

    fun dropKey(key: ULong) {
        startUptimeMs.remove(key)
        knownKeys.remove(key)
        completedKeys.remove(key)
    }

    companion object {
        private const val TAIL_LIVE_MARGIN_DP = 48f
        private const val HISTORY_MARGIN_DP = 8f

        fun animates(kind: RowKind): Boolean =
            kind == RowKind.USER || kind == RowKind.MARKDOWN
    }
}
