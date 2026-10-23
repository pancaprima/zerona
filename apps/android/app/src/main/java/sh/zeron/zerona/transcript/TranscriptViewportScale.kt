package sh.zeron.zerona.transcript

/**
 * Rust body type is 16.5dp at scale 1.0. Android dampens via [ANDROID_BODY_SCALE_MULTIPLIER]
 * times system [fontScale] (accessibility); value is passed to core `setViewport` only — never
 * applied again on the canvas (density is separate).
 */
object TranscriptViewportScale {
    const val ANDROID_BODY_SCALE_MULTIPLIER = 0.88f

    fun viewportTextScale(fontScale: Float): Float =
        ANDROID_BODY_SCALE_MULTIPLIER * fontScale
}
