package sh.zeron.zerona.ui.theme

/**
 * Motion accessibility policy shared by the Compose shell and future native controls.
 *
 * Compose [androidx.compose.ui.MotionDurationScale] already scales animation durations; never
 * multiply durations again or replace that coroutine context. Ornamental gating reads
 * [android.provider.Settings.Global.ANIMATOR_DURATION_SCALE] at the theme root only.
 */
object ZeronaMotionPolicy {
    const val NORMAL_SYSTEM_MOTION_SCALE = 1f

    const val TACTILE_REST_SCALE = 1f
    const val TACTILE_PRESSED_SCALE = 0.98f

    /** Draw-only send icon nudge while the host accepts the message (no touch-target change). */
    const val SEND_IN_FLIGHT_ICON_NUDGE_FRACTION = 0.14f

    /** Subtle ornamental breathe on initial loading indicator (scale only, layout-stable). */
    const val LOADING_ORNAMENTAL_MIN_SCALE = 1f
    const val LOADING_ORNAMENTAL_MAX_SCALE = 1.045f

    const val LOADING_ENTRANCE_MIN_SCALE = 0.96f

    fun ornamentalMotionEnabled(systemMotionDurationScale: Float): Boolean {
        if (!systemMotionDurationScale.isFinite()) return false
        return systemMotionDurationScale > 0f
    }

    /** Native bridge contract: match [ornamentalMotionEnabled] using Settings animator scale. */
    fun nativeOrnamentalMotionEnabled(systemAnimatorDurationScale: Float): Boolean =
        ornamentalMotionEnabled(systemAnimatorDurationScale)
}
