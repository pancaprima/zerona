package sh.zeron.zerona.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowStreamMotionPolicyTest {
    private val keyA = 1uL
    private val keyB = 2uL

    private fun input(
        priorKey: ULong = keyA,
        newKey: ULong = keyA,
        grew: Boolean = false,
        veilFrom: Int = 0,
        allowEntrance: Boolean = false,
        phase: RowStreamMotionPolicy.StreamRevealPhase = RowStreamMotionPolicy.StreamRevealPhase.None,
        animatorRunning: Boolean = false
    ) = RowStreamMotionPolicy.Input(
        priorKey = priorKey,
        newKey = newKey,
        grew = grew,
        veilFrom = veilFrom,
        allowEntrance = allowEntrance,
        streamRevealPhase = phase,
        streamAnimatorRunning = animatorRunning
    )

    @Test
    fun sameKeyStreamingPrefixDoesNotCancelEntranceOrRestartReveal() {
        val first =
            RowStreamMotionPolicy.onConfigure(
                input(grew = true, veilFrom = 3, phase = RowStreamMotionPolicy.StreamRevealPhase.None)
            )
        assertTrue(first.shouldStartStreamReveal)
        assertTrue(first.useStreamVeil)
        assertFalse(first.shouldClearRowEntrance)

        val second =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = true,
                    veilFrom = 8,
                    phase = first.streamRevealPhase,
                    animatorRunning = true
                )
            )
        assertFalse(second.shouldStartStreamReveal)
        assertFalse(second.shouldCancelStreamAnimator)
        assertFalse(second.shouldClearRowEntrance)
        assertTrue(second.useStreamVeil)
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Animating, second.streamRevealPhase)
    }

    @Test
    fun afterRevealDoneFurtherGrowthSkipsVeil() {
        val out =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = true,
                    veilFrom = 12,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Done
                )
            )
        assertFalse(out.shouldStartStreamReveal)
        assertFalse(out.useStreamVeil)
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Done, out.streamRevealPhase)
    }

    @Test
    fun sameKeyNonGrowthDuringActiveAnimationPreservesReveal() {
        val out =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = false,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Animating,
                    animatorRunning = true
                )
            )
        assertFalse(out.shouldCancelStreamAnimator)
        assertFalse(out.shouldStartStreamReveal)
        assertTrue(out.useStreamVeil)
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Animating, out.streamRevealPhase)
    }

    @Test
    fun nonGrowthDuringActiveAnimationThenLaterGrowthDoesNotRestartReveal() {
        val started =
            RowStreamMotionPolicy.onConfigure(
                input(grew = true, veilFrom = 5, phase = RowStreamMotionPolicy.StreamRevealPhase.None)
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Animating, started.streamRevealPhase)

        val versionOrWidthRebind =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = false,
                    phase = started.streamRevealPhase,
                    animatorRunning = true
                )
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Animating, versionOrWidthRebind.streamRevealPhase)
        assertFalse(versionOrWidthRebind.shouldCancelStreamAnimator)
        assertTrue(versionOrWidthRebind.useStreamVeil)

        val laterGrowth =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = true,
                    veilFrom = 20,
                    phase = versionOrWidthRebind.streamRevealPhase,
                    animatorRunning = true
                )
            )
        assertFalse(laterGrowth.shouldStartStreamReveal)
        assertTrue(laterGrowth.useStreamVeil)
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Animating, laterGrowth.streamRevealPhase)
    }

    @Test
    fun animatingPhaseWithoutRunningAnimatorSettlesDoneOnRebind() {
        val nonGrowth =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = false,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Animating,
                    animatorRunning = false
                )
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Done, nonGrowth.streamRevealPhase)
        assertFalse(nonGrowth.useStreamVeil)
        assertFalse(nonGrowth.shouldCancelStreamAnimator)

        val growth =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = true,
                    veilFrom = 10,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Animating,
                    animatorRunning = false
                )
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Done, growth.streamRevealPhase)
        assertFalse(growth.useStreamVeil)
        assertFalse(growth.shouldStartStreamReveal)
    }

    @Test
    fun donePhaseNeverRestartsRevealOnGrowth() {
        val out =
            RowStreamMotionPolicy.onConfigure(
                input(
                    grew = true,
                    veilFrom = 30,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Done,
                    animatorRunning = false
                )
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Done, out.streamRevealPhase)
        assertFalse(out.shouldStartStreamReveal)
        assertFalse(out.useStreamVeil)
    }

    @Test
    fun sameKeyNonGrowthInNoneOrDoneLeavesPhaseUnchanged() {
        val inNone =
            RowStreamMotionPolicy.onConfigure(
                input(grew = false, phase = RowStreamMotionPolicy.StreamRevealPhase.None)
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.None, inNone.streamRevealPhase)
        assertFalse(inNone.useStreamVeil)

        val inDone =
            RowStreamMotionPolicy.onConfigure(
                input(grew = false, phase = RowStreamMotionPolicy.StreamRevealPhase.Done)
            )
        assertEquals(RowStreamMotionPolicy.StreamRevealPhase.Done, inDone.streamRevealPhase)
        assertFalse(inDone.useStreamVeil)
    }

    @Test
    fun keyChangeClearsEntranceAndStartsFreshReveal() {
        val out =
            RowStreamMotionPolicy.onConfigure(
                input(
                    priorKey = keyA,
                    newKey = keyB,
                    grew = true,
                    veilFrom = 4,
                    phase = RowStreamMotionPolicy.StreamRevealPhase.Animating,
                    animatorRunning = true
                )
            )
        assertTrue(out.shouldClearRowEntrance)
        assertTrue(out.shouldCancelStreamAnimator)
        assertTrue(out.shouldStartStreamReveal)
    }

    @Test
    fun entranceOnlyOnFirstBindWithoutGrowth() {
        val out =
            RowStreamMotionPolicy.onConfigure(
                input(allowEntrance = true, grew = false, veilFrom = 0)
            )
        assertTrue(out.shouldPlayRowEntrance)
        assertFalse(out.shouldClearRowEntrance)
    }

    @Test
    fun finishRevealMovesAnimatingToDone() {
        assertEquals(
            RowStreamMotionPolicy.StreamRevealPhase.Done,
            RowStreamMotionPolicy.onStreamRevealFinished(
                RowStreamMotionPolicy.StreamRevealPhase.Animating
            )
        )
    }
}
