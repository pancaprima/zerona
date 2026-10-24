package sh.zeron.zerona.transcript

/**
 * Pure lifecycle rules for transcript row stream reveal and entrance rebinds.
 * Keeps at most one stream veil animation per row key; same-key updates must not
 * reset an in-progress row entrance.
 */
object RowStreamMotionPolicy {
    enum class StreamRevealPhase {
        None,
        Animating,
        Done,
    }

    data class Input(
        val priorKey: ULong,
        val newKey: ULong,
        val grew: Boolean,
        val veilFrom: Int,
        val allowEntrance: Boolean,
        val streamRevealPhase: StreamRevealPhase,
        val streamAnimatorRunning: Boolean
    )

    data class Output(
        val streamRevealPhase: StreamRevealPhase,
        val shouldCancelStreamAnimator: Boolean,
        val shouldStartStreamReveal: Boolean,
        val useStreamVeil: Boolean,
        val shouldPlayRowEntrance: Boolean,
        val shouldClearRowEntrance: Boolean
    )

    fun onConfigure(input: Input): Output {
        val keyChanged = input.priorKey != input.newKey
        val entranceEligible = input.allowEntrance && !input.grew && input.veilFrom == 0
        val growthEligible = input.grew && input.veilFrom > 0

        if (keyChanged) {
            var phase = StreamRevealPhase.None
            val startReveal = growthEligible
            if (startReveal) {
                phase = StreamRevealPhase.Animating
            }
            return Output(
                streamRevealPhase = phase,
                shouldCancelStreamAnimator =
                    input.streamAnimatorRunning ||
                        input.streamRevealPhase != StreamRevealPhase.None,
                shouldStartStreamReveal = startReveal,
                useStreamVeil = growthEligible,
                shouldPlayRowEntrance = entranceEligible,
                shouldClearRowEntrance = true
            )
        }

        var phase = input.streamRevealPhase
        var cancelAnimator = false
        var startReveal = false
        var useVeil = false

        if (growthEligible) {
            when (phase) {
                StreamRevealPhase.None -> {
                    phase = StreamRevealPhase.Animating
                    startReveal = true
                    useVeil = true
                }
                StreamRevealPhase.Animating -> {
                    if (input.streamAnimatorRunning) {
                        useVeil = true
                    } else {
                        phase = StreamRevealPhase.Done
                    }
                }
                StreamRevealPhase.Done -> {
                    useVeil = false
                }
            }
        } else {
            when (phase) {
                StreamRevealPhase.None -> Unit
                StreamRevealPhase.Animating -> {
                    if (input.streamAnimatorRunning) {
                        useVeil = true
                    } else {
                        phase = StreamRevealPhase.Done
                    }
                }
                StreamRevealPhase.Done -> Unit
            }
        }

        return Output(
            streamRevealPhase = phase,
            shouldCancelStreamAnimator = cancelAnimator,
            shouldStartStreamReveal = startReveal,
            useStreamVeil = useVeil,
            shouldPlayRowEntrance = entranceEligible,
            shouldClearRowEntrance = false
        )
    }

    fun onStreamRevealFinished(phase: StreamRevealPhase): StreamRevealPhase =
        when (phase) {
            StreamRevealPhase.Animating -> StreamRevealPhase.Done
            else -> phase
        }
}
