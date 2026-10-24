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

    /** Steer chip is shown only when the host allows mid-turn steering. */
    fun showSteerChip(canSteer: Boolean): Boolean = canSteer
}
