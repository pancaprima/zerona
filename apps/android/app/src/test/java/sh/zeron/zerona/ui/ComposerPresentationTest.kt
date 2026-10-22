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
}
