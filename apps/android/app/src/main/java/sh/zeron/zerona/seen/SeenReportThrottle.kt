package sh.zeron.zerona.seen

/**
 * Dedup and rate-limit for `POST /seen` (debounce handled by the caller).
 */
object SeenReportPolicy {
    /**
     * @return candidate to report, or null if not newer than [lastReported].
     */
    fun candidateToReport(
        visible: VisibleSeenMessage?,
        lastReported: VisibleSeenMessage?,
    ): VisibleSeenMessage? {
        if (visible == null) return null
        if (lastReported != null && visible.messageAtMs <= lastReported.messageAtMs) {
            return null
        }
        return visible
    }

    /**
     * At most one network call per [minIntervalMs] unless [candidate] is newer than [lastReported].
     */
    fun mayEmitNow(
        nowMs: Long,
        candidate: VisibleSeenMessage,
        lastReported: VisibleSeenMessage?,
        lastEmitAtMs: Long?,
        minIntervalMs: Long = 5_000L,
    ): Boolean {
        if (lastReported != null && candidate.messageAtMs <= lastReported.messageAtMs) {
            return false
        }
        val lastEmit = lastEmitAtMs ?: return true
        if (nowMs - lastEmit >= minIntervalMs) {
            return true
        }
        return lastReported != null && candidate.messageAtMs > lastReported.messageAtMs
    }
}
