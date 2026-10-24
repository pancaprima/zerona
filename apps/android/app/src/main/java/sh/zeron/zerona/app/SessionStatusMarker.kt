package sh.zeron.zerona.app

/** Session list corner marker kind (mirrors iOS `SessionCell.corner` precedence). */
enum class SessionStatusKind {
    NONE,
    WORKING,
    NEEDS_INPUT,
    FAILED,
    DONE,
    ;

    val label: String
        get() =
            when (this) {
                NONE -> ""
                WORKING -> "Working"
                NEEDS_INPUT -> "Needs input"
                FAILED -> "Failed"
                DONE -> "Done"
            }
}

enum class SessionIndicator {
    WORKING,
    AWAITING_INPUT,
    ERRORED,
    COMPLETED,
    IDLE,
}

enum class SessionSendState {
    SENDING,
    QUEUED,
    FAILED,
}

/**
 * Pure precedence: failed send beats live indicator; COMPLETED shows Done only when [unseen].
 * Matches iOS `SessionCell.corner` (including COMPLETED + unseen).
 */
fun sessionStatusKind(
    indicator: SessionIndicator,
    unseen: Boolean,
    sendState: SessionSendState?,
): SessionStatusKind {
    if (sendState == SessionSendState.FAILED) {
        return SessionStatusKind.FAILED
    }
    return when (indicator) {
        SessionIndicator.WORKING -> SessionStatusKind.WORKING
        SessionIndicator.AWAITING_INPUT -> SessionStatusKind.NEEDS_INPUT
        SessionIndicator.ERRORED -> SessionStatusKind.FAILED
        SessionIndicator.COMPLETED ->
            if (unseen) {
                SessionStatusKind.DONE
            } else {
                SessionStatusKind.NONE
            }
        SessionIndicator.IDLE -> SessionStatusKind.NONE
    }
}
