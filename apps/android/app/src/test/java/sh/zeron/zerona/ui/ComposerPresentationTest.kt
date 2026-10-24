package sh.zeron.zerona.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerPresentationTest {
    @Test
    fun chipsHiddenWhenDraftBlank() {
        assertFalse(
            ComposerPresentation.showDeliveryModeChips(
                turnRunning = true,
                demoLocked = false,
                draftNonBlank = false,
            ),
        )
    }

    @Test
    fun chipsHiddenWhenDemoLocked() {
        assertFalse(
            ComposerPresentation.showDeliveryModeChips(
                turnRunning = true,
                demoLocked = true,
                draftNonBlank = true,
            ),
        )
    }

    @Test
    fun chipsVisibleWhenTurnRunningAndDraft() {
        assertTrue(
            ComposerPresentation.showDeliveryModeChips(
                turnRunning = true,
                demoLocked = false,
                draftNonBlank = true,
            ),
        )
    }

    @Test
    fun sendDisabledWhenInFlight() {
        assertFalse(ComposerPresentation.canSend(draftNonBlank = true, sendInFlight = true))
    }

    @Test
    fun sendVisualInFlightOverridesPressedAndDraft() {
        assertEquals(
            ComposerPresentation.SendButtonVisual.InFlight,
            ComposerPresentation.sendButtonVisual(
                draftNonBlank = true,
                sendInFlight = true,
                pressed = true,
            ),
        )
    }

    @Test
    fun sendVisualDisabledWhenDraftBlank() {
        assertEquals(
            ComposerPresentation.SendButtonVisual.Disabled,
            ComposerPresentation.sendButtonVisual(
                draftNonBlank = false,
                sendInFlight = false,
                pressed = false,
            ),
        )
    }

    @Test
    fun sendVisualReadyAndPressed() {
        assertEquals(
            ComposerPresentation.SendButtonVisual.Ready,
            ComposerPresentation.sendButtonVisual(
                draftNonBlank = true,
                sendInFlight = false,
                pressed = false,
            ),
        )
        assertEquals(
            ComposerPresentation.SendButtonVisual.Pressed,
            ComposerPresentation.sendButtonVisual(
                draftNonBlank = true,
                sendInFlight = false,
                pressed = true,
            ),
        )
    }

    @Test
    fun sendDisabledWhenDraftEmpty() {
        assertFalse(ComposerPresentation.canSend(draftNonBlank = false, sendInFlight = false))
    }

    @Test
    fun maxDraftLinesIsSix() {
        assertEquals(6, ComposerPresentation.MAX_DRAFT_LINES)
    }

    @Test
    fun sendTouchTargetAtLeastFortyEightDp() {
        assertTrue(ComposerPresentation.SEND_TOUCH_TARGET_DP >= 48)
    }

    @Test
    fun steerChipGatedByCapability() {
        assertFalse(ComposerPresentation.showSteerChip(canSteer = false))
        assertTrue(ComposerPresentation.showSteerChip(canSteer = true))
    }

    @Test
    fun sendStatusBannerKeyEncodesSeverityAndText() {
        assertEquals(
            "info|Sending…",
            ComposerPresentation.sendStatusBannerKey("Sending…", bannerIsError = false),
        )
        assertEquals(
            "error|Not delivered",
            ComposerPresentation.sendStatusBannerKey("Not delivered", bannerIsError = true),
        )
        assertEquals(null, ComposerPresentation.sendStatusBannerKey(null, bannerIsError = false))
    }
}
