package sh.zeron.zerona.ui

/**
 * Edge Dock composer presentation rules (pure Kotlin; mirrored in [SessionComposerBar]).
 */
object ComposerPresentation {
    const val MAX_DRAFT_LINES = 6
    const val MAX_QUEUED_PREVIEW_LINES = 3
    const val SEND_TOUCH_TARGET_DP = 48

    fun showDeliveryModeChips(
        turnRunning: Boolean,
        demoLocked: Boolean,
        draftNonBlank: Boolean,
    ): Boolean = turnRunning && !demoLocked && draftNonBlank

    fun canSend(
        draftNonBlank: Boolean,
        sendInFlight: Boolean,
    ): Boolean = draftNonBlank && !sendInFlight

    enum class SendButtonVisual {
        Disabled,
        Ready,
        Pressed,
        InFlight,
    }

    fun sendButtonVisual(
        draftNonBlank: Boolean,
        sendInFlight: Boolean,
        pressed: Boolean,
    ): SendButtonVisual =
        when {
            sendInFlight -> SendButtonVisual.InFlight
            !draftNonBlank -> SendButtonVisual.Disabled
            pressed -> SendButtonVisual.Pressed
            else -> SendButtonVisual.Ready
        }

    /** Steer chip is shown only when the host allows mid-turn steering. */
    fun showSteerChip(canSteer: Boolean): Boolean = canSteer

    /**
     * Stable key for banner [androidx.compose.animation.AnimatedContent] (null = hidden).
     * Prefix encodes severity so error ↔ progress transitions stay distinct.
     */
    fun sendStatusBannerKey(
        banner: String?,
        bannerIsError: Boolean,
    ): String? {
        if (banner == null) return null
        val kind = if (bannerIsError) "error" else "info"
        return "$kind|$banner"
    }
}
