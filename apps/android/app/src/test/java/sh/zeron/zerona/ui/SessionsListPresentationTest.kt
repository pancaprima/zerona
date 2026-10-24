package sh.zeron.zerona.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionsListPresentationTest {
    @Test
    fun initialWaitOnlyForSignedInLiveBeforeWorkspace() {
        assertTrue(
            SessionsListPresentation.showInitialWorkspaceWait(
                signedIn = true,
                isDemo = false,
                workspaceReady = false,
            ),
        )
        assertFalse(
            SessionsListPresentation.showInitialWorkspaceWait(
                signedIn = true,
                isDemo = false,
                workspaceReady = true,
            ),
        )
    }

    @Test
    fun demoSkipsInitialWorkspaceWait() {
        assertFalse(
            SessionsListPresentation.showInitialWorkspaceWait(
                signedIn = true,
                isDemo = true,
                workspaceReady = false,
            ),
        )
    }

    @Test
    fun signedOutListVisibleWithoutWorkspace() {
        assertTrue(
            SessionsListPresentation.showLoadedListReveal(
                signedIn = false,
                isDemo = false,
                workspaceReady = false,
            ),
        )
        assertTrue(
            SessionsListPresentation.showLoadedListReveal(
                signedIn = false,
                isDemo = false,
                workspaceReady = true,
            ),
        )
    }

    @Test
    fun demoListVisibleBeforeWorkspaceReady() {
        assertTrue(
            SessionsListPresentation.showLoadedListReveal(
                signedIn = true,
                isDemo = true,
                workspaceReady = false,
            ),
        )
    }

    @Test
    fun liveSignedInRevealFollowsWorkspaceReady() {
        assertFalse(
            SessionsListPresentation.showLoadedListReveal(
                signedIn = true,
                isDemo = false,
                workspaceReady = false,
            ),
        )
        assertTrue(
            SessionsListPresentation.showLoadedListReveal(
                signedIn = true,
                isDemo = false,
                workspaceReady = true,
            ),
        )
    }
}
