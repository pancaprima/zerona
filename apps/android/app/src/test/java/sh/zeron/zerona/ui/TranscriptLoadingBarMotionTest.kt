package sh.zeron.zerona.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptLoadingBarMotionTest {
    @Test
    fun segmentNeverFillsEntireTrack() {
        val offset = TranscriptLoadingBarMotion.segmentOffset(0.5f)
        assertTrue(offset >= 0f)
        assertTrue(offset + TranscriptLoadingBarMotion.FILL_FRAC <= 1f + 1e-4f)
    }
}
