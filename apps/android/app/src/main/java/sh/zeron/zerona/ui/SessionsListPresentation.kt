package sh.zeron.zerona.ui

/**
 * Session list motion gating (pure Kotlin; used by [SessionsScreen]).
 */
object SessionsListPresentation {
    /** True while signed-in live client has not completed its first workspace snapshot. */
    fun showInitialWorkspaceWait(
        signedIn: Boolean,
        isDemo: Boolean,
        workspaceReady: Boolean,
    ): Boolean = signedIn && !isDemo && !workspaceReady

    /**
     * True when the session list should be fully visible (not held at reveal alpha 0).
     * Signed-out and demo lists are immediate; live signed-in waits for [workspaceReady].
     */
    fun showLoadedListReveal(
        signedIn: Boolean,
        isDemo: Boolean,
        workspaceReady: Boolean,
    ): Boolean =
        when {
            !signedIn -> true
            isDemo -> true
            else -> workspaceReady
        }
}
