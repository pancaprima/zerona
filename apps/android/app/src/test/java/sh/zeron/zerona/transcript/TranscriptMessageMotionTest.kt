package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptMessageMotionTest {
    @Test
    fun assistantEntranceStartsOffsetLeftAndFadesIn() {
        val start = TranscriptMessageMotion.entrancePaint(0f, user = false)
        assertEquals(0f, start.opacity, 0.001f)
        assertEquals(-14f, start.translationXDp, 0.001f)
        val end = TranscriptMessageMotion.entrancePaint(1f, user = false)
        assertEquals(1f, end.opacity, 0.001f)
        assertEquals(0f, end.translationXDp, 0.001f)
    }

    @Test
    fun userEntranceSettlesAtRestWithSubtleScale() {
        val start = TranscriptMessageMotion.entrancePaint(0f, user = true)
        assertTrue(start.translationXDp > 0f)
        assertTrue(start.translationYDp > 0f)
        assertEquals(TranscriptMessageMotion.USER_START_SCALE, start.scale, 0.001f)
        val end = TranscriptMessageMotion.entrancePaint(1f, user = true)
        assertEquals(1f, end.scale, 0.001f)
        assertEquals(0f, end.translationXDp, 0.001f)
        assertEquals(0f, end.translationYDp, 0.05f)
    }

    @Test
    fun entranceProgressSnapsWhenReducedMotion() {
        assertEquals(1f, TranscriptMessageMotion.entranceProgress(10L, user = true, reduceMotion = true))
    }
}
