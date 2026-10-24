package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.zeron_core.RowKind

class TranscriptMessageEntranceTrackerTest {
    @Test
    fun historyAboveViewportDoesNotStartEntrance() {
        val tracker = TranscriptMessageEntranceTracker()
        tracker.sealReplayBaseline(listOf(1u))
        tracker.noteRow(
            key = 2u,
            yDp = 10f,
            rowBottomDp = 30f,
            kind = RowKind.MARKDOWN,
            scrollTopDp = 100f,
            viewportHeightDp = 400f,
            nowUptimeMs = 0L,
            reduceMotion = false,
        )
        assertTrue(tracker.activeStarts().isEmpty())
    }

    @Test
    fun liveTailRowStartsEntrance() {
        val tracker = TranscriptMessageEntranceTracker()
        tracker.sealReplayBaseline(listOf(1u))
        tracker.noteRow(
            key = 9u,
            yDp = 480f,
            rowBottomDp = 520f,
            kind = RowKind.USER,
            scrollTopDp = 100f,
            viewportHeightDp = 400f,
            nowUptimeMs = 50L,
            reduceMotion = false,
        )
        assertEquals(50L, tracker.activeStarts()[9u])
    }

    @Test
    fun markCompletedWhileIteratingActiveStartsDoesNotThrow() {
        val tracker = TranscriptMessageEntranceTracker()
        tracker.sealReplayBaseline(listOf(1u))
        fun noteLiveTail(key: ULong, nowUptimeMs: Long) {
            tracker.noteRow(
                key = key,
                yDp = 480f,
                rowBottomDp = 520f,
                kind = RowKind.USER,
                scrollTopDp = 100f,
                viewportHeightDp = 400f,
                nowUptimeMs = nowUptimeMs,
                reduceMotion = false,
            )
        }
        noteLiveTail(9u, 50L)
        noteLiveTail(10u, 60L)

        for ((key, _) in tracker.activeStarts()) {
            tracker.markCompleted(key)
        }

        assertTrue(tracker.activeStarts().isEmpty())
    }

    @Test
    fun dropKeyClearsEntranceStateSoRowCanBeNotedAgain() {
        val tracker = TranscriptMessageEntranceTracker()
        tracker.sealReplayBaseline(listOf(1u))
        tracker.noteRow(
            key = 9u,
            yDp = 480f,
            rowBottomDp = 520f,
            kind = RowKind.USER,
            scrollTopDp = 100f,
            viewportHeightDp = 400f,
            nowUptimeMs = 50L,
            reduceMotion = false,
        )
        assertEquals(50L, tracker.activeStarts()[9u])
        tracker.dropKey(9u)
        assertTrue(tracker.activeStarts().isEmpty())
        tracker.noteRow(
            key = 9u,
            yDp = 480f,
            rowBottomDp = 520f,
            kind = RowKind.USER,
            scrollTopDp = 100f,
            viewportHeightDp = 400f,
            nowUptimeMs = 200L,
            reduceMotion = false,
        )
        assertEquals(200L, tracker.activeStarts()[9u])
    }

    @Test
    fun isLiveArrivalRejectsRowsFullyAboveViewport() {
        val tracker = TranscriptMessageEntranceTracker()
        assertFalse(
            tracker.isLiveArrival(
                yDp = 0f,
                rowBottomDp = 40f,
                scrollTopDp = 100f,
                viewportHeightDp = 400f,
            ),
        )
    }
}
