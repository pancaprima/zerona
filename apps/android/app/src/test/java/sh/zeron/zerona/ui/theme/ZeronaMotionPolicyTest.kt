package sh.zeron.zerona.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeronaMotionPolicyTest {
    @Test
    fun ornamentalMotionDisabledAtZeroScale() {
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(0f))
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(-0f))
    }

    @Test
    fun ornamentalMotionEnabledAboveZero() {
        assertTrue(ZeronaMotionPolicy.ornamentalMotionEnabled(0.0001f))
        assertTrue(ZeronaMotionPolicy.ornamentalMotionEnabled(1f))
        assertTrue(ZeronaMotionPolicy.ornamentalMotionEnabled(2f))
    }

    @Test
    fun nativeBridgeUsesSameZeroEndpoint() {
        assertFalse(ZeronaMotionPolicy.nativeOrnamentalMotionEnabled(0f))
        assertTrue(ZeronaMotionPolicy.nativeOrnamentalMotionEnabled(1f))
    }

    @Test
    fun invalidAnimatorScalesDisableOrnamentalMotion() {
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(Float.NaN))
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(Float.POSITIVE_INFINITY))
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(Float.NEGATIVE_INFINITY))
        assertFalse(ZeronaMotionPolicy.ornamentalMotionEnabled(-1f))
        assertFalse(ZeronaMotionPolicy.nativeOrnamentalMotionEnabled(Float.NaN))
    }

    @Test
    fun tactilePressRestScaleIsOne() {
        assertEquals(1f, ZeronaMotionPolicy.TACTILE_REST_SCALE, 0.0001f)
    }

    @Test
    fun tactilePressCompressionIsAboutTwoPercent() {
        val delta = ZeronaMotionPolicy.TACTILE_REST_SCALE - ZeronaMotionPolicy.TACTILE_PRESSED_SCALE
        assertTrue(delta >= 0.01f)
        assertTrue(delta <= 0.02f)
    }

    @Test
    fun sendInFlightNudgeIsSubtleDrawOnly() {
        assertTrue(ZeronaMotionPolicy.SEND_IN_FLIGHT_ICON_NUDGE_FRACTION > 0f)
        assertTrue(ZeronaMotionPolicy.SEND_IN_FLIGHT_ICON_NUDGE_FRACTION <= 0.2f)
    }

    @Test
    fun loadingBreatheStaysNearUnity() {
        assertEquals(1f, ZeronaMotionPolicy.LOADING_ORNAMENTAL_MIN_SCALE, 0.0001f)
        val delta =
            ZeronaMotionPolicy.LOADING_ORNAMENTAL_MAX_SCALE -
                ZeronaMotionPolicy.LOADING_ORNAMENTAL_MIN_SCALE
        assertTrue(delta > 0f)
        assertTrue(delta <= 0.08f)
    }
}
