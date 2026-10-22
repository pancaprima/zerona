package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptViewportScaleTest {
    @Test
    fun defaultFontScaleUsesAndroidMultiplier() {
        val scale = TranscriptViewportScale.viewportTextScale(1f)
        assertEquals(0.88f, scale, 0.0001f)
    }

    @Test
    fun accessibilityFontScaleIsNotClamped() {
        val scale = TranscriptViewportScale.viewportTextScale(1.3f)
        assertEquals(0.88f * 1.3f, scale, 0.0001f)
    }
}
