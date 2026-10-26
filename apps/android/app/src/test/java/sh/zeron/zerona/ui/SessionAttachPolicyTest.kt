package sh.zeron.zerona.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionAttachPolicyTest {
    @Test
    fun successfulAttachMarksSeenAndDismissesNotification() {
        val outcome = SessionAttachPolicy.outcome(transcriptAttached = true)
        assertEquals(true, outcome.setViewAttached)
        assertEquals(true, outcome.dismissSessionNotification)
    }

    @Test
    fun failedAttachDoesNotMarkSeenOrDismiss() {
        val outcome = SessionAttachPolicy.outcome(transcriptAttached = false)
        assertFalse(outcome.setViewAttached)
        assertFalse(outcome.dismissSessionNotification)
    }
}
