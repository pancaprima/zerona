package sh.zeron.zerona.seen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenReportPolicyTest {
    private val first = VisibleSeenMessage("m1", 1_000L)
    private val second = VisibleSeenMessage("m2", 2_000L)

    @Test
    fun candidateOnlyWhenNewerThanLastReported() {
        assertEquals(first, SeenReportPolicy.candidateToReport(first, null))
        assertNull(SeenReportPolicy.candidateToReport(first, first))
        assertEquals(second, SeenReportPolicy.candidateToReport(second, first))
        assertNull(SeenReportPolicy.candidateToReport(first, second))
    }

    @Test
    fun rateLimitAllowsFirstAndAfterInterval() {
        assertTrue(SeenReportPolicy.mayEmitNow(0L, first, null, null))
        assertFalse(
            SeenReportPolicy.mayEmitNow(
                1_000L,
                second,
                first,
                lastEmitAtMs = 0L,
            ),
        )
        assertTrue(
            SeenReportPolicy.mayEmitNow(
                5_000L,
                second,
                first,
                lastEmitAtMs = 0L,
            ),
        )
    }

    @Test
    fun rateLimitBypassForNewerMessage() {
        assertTrue(
            SeenReportPolicy.mayEmitNow(
                1_000L,
                second,
                first,
                lastEmitAtMs = 0L,
            ),
        )
    }
}
