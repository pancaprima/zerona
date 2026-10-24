package sh.zeron.zerona.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusMarkerTest {
    @Test
    fun sendFailedOverridesWorking() {
        assertEquals(
            SessionStatusKind.FAILED,
            sessionStatusKind(SessionIndicator.WORKING, unseen = false, sendState = SessionSendState.FAILED),
        )
    }

    @Test
    fun sendFailedOverridesCompletedUnseen() {
        assertEquals(
            SessionStatusKind.FAILED,
            sessionStatusKind(SessionIndicator.COMPLETED, unseen = true, sendState = SessionSendState.FAILED),
        )
    }

    @Test
    fun sendingDoesNotOverrideIdle() {
        assertEquals(
            SessionStatusKind.NONE,
            sessionStatusKind(SessionIndicator.IDLE, unseen = false, sendState = SessionSendState.SENDING),
        )
    }

    @Test
    fun queuedDoesNotOverrideAwaitingInput() {
        assertEquals(
            SessionStatusKind.NEEDS_INPUT,
            sessionStatusKind(
                SessionIndicator.AWAITING_INPUT,
                unseen = false,
                sendState = SessionSendState.QUEUED,
            ),
        )
    }

    @Test
    fun workingWithNullSend() {
        assertEquals(
            SessionStatusKind.WORKING,
            sessionStatusKind(SessionIndicator.WORKING, unseen = false, sendState = null),
        )
    }

    @Test
    fun awaitingInput() {
        assertEquals(
            SessionStatusKind.NEEDS_INPUT,
            sessionStatusKind(SessionIndicator.AWAITING_INPUT, unseen = true, sendState = null),
        )
    }

    @Test
    fun errored() {
        assertEquals(
            SessionStatusKind.FAILED,
            sessionStatusKind(SessionIndicator.ERRORED, unseen = false, sendState = null),
        )
    }

    @Test
    fun completedUnseenIsDone() {
        assertEquals(
            SessionStatusKind.DONE,
            sessionStatusKind(SessionIndicator.COMPLETED, unseen = true, sendState = null),
        )
    }

    @Test
    fun completedSeenIsNone() {
        assertEquals(
            SessionStatusKind.NONE,
            sessionStatusKind(SessionIndicator.COMPLETED, unseen = false, sendState = null),
        )
    }

    @Test
    fun idleUnseenIsStillNone() {
        assertEquals(
            SessionStatusKind.NONE,
            sessionStatusKind(SessionIndicator.IDLE, unseen = true, sendState = null),
        )
    }

    @Test
    fun labelsForVisibleKinds() {
        assertEquals("Working", SessionStatusKind.WORKING.label)
        assertEquals("Needs input", SessionStatusKind.NEEDS_INPUT.label)
        assertEquals("Failed", SessionStatusKind.FAILED.label)
        assertEquals("Done", SessionStatusKind.DONE.label)
        assertEquals("", SessionStatusKind.NONE.label)
    }
}
