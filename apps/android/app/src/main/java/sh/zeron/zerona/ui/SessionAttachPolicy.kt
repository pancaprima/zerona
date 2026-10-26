package sh.zeron.zerona.ui

/** Outcome of a transcript attach attempt for session read / notification dismissal. */
data class SessionAttachOutcome(
    val setViewAttached: Boolean,
    val dismissSessionNotification: Boolean,
)

object SessionAttachPolicy {
    fun outcome(transcriptAttached: Boolean): SessionAttachOutcome =
        if (transcriptAttached) {
            SessionAttachOutcome(setViewAttached = true, dismissSessionNotification = true)
        } else {
            SessionAttachOutcome(setViewAttached = false, dismissSessionNotification = false)
        }
}
