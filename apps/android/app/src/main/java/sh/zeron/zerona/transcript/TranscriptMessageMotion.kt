package sh.zeron.zerona.transcript

import android.content.Context
import android.provider.Settings
import kotlin.math.sin

/**
 * Message-row entrance math aligned with desktop `crates/ui/src/motion.rs`.
 */
object TranscriptMessageMotion {
    const val ASSISTANT_ENTRANCE_MS = 420L
    const val USER_ENTRANCE_MS = 480L
    const val USER_START_SCALE = 0.96f

    data class EntrancePaint(
        val opacity: Float,
        val translationXDp: Float,
        val translationYDp: Float,
        val scale: Float,
    )

    fun reduceMotion(context: Context): Boolean {
        val resolver = context.contentResolver
        val animator =
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition =
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        return animator == 0f || transition == 0f
    }

    fun entranceProgress(
        elapsedMs: Long,
        user: Boolean,
        reduceMotion: Boolean,
    ): Float {
        if (reduceMotion) return 1f
        val duration = if (user) USER_ENTRANCE_MS else ASSISTANT_ENTRANCE_MS
        if (duration <= 0L) return 1f
        return (elapsedMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    fun entrancePaint(
        progress: Float,
        user: Boolean,
    ): EntrancePaint {
        val t = progress.coerceIn(0f, 1f)
        if (user) {
            val eased = easeOutQuint(t)
            val bounce = userEntranceBounce(t)
            val scale = USER_START_SCALE + (1f - USER_START_SCALE) * eased
            return EntrancePaint(
                opacity = eased.coerceAtMost(1f),
                translationXDp = 12f * (1f - eased),
                translationYDp = 8f * (1f - eased) + bounce,
                scale = scale,
            )
        }
        val eased = easeOutExpo(t)
        return EntrancePaint(
            opacity = eased,
            translationXDp = -14f * (1f - eased),
            translationYDp = 0f,
            scale = 1f,
        )
    }

    fun easeOutExpo(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return cubicBezier(x, 0.16f, 1f, 0.3f, 1f)
    }

    fun easeOutQuint(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return cubicBezier(x, 0.22f, 1f, 0.36f, 1f)
    }

    fun userEntranceBounce(t: Float): Float {
        if (t <= 0.75f || t >= 1f) return 0f
        val tail = (t - 0.75f) / 0.25f
        return (-1.75f * sin(Math.PI.toFloat() * tail) * (1f - tail))
    }

    /**
     * De Casteljau cubic Bézier for axis-aligned control points (y1 == y2 for our curves).
     */
    fun cubicBezier(
        x: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        for (i in 0 until 8) {
            val xAtT = bezierComponent(t, 0f, x1, x2, 1f)
            val slope = bezierDerivative(t, 0f, x1, x2, 1f)
            if (slope == 0f) break
            t -= (xAtT - x) / slope
            t = t.coerceIn(0f, 1f)
        }
        return bezierComponent(t, 0f, y1, y2, 1f).coerceIn(0f, 1f)
    }

    private fun bezierComponent(
        t: Float,
        p0: Float,
        p1: Float,
        p2: Float,
        p3: Float,
    ): Float {
        val u = 1f - t
        return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
    }

    private fun bezierDerivative(
        t: Float,
        p0: Float,
        p1: Float,
        p2: Float,
        p3: Float,
    ): Float {
        val u = 1f - t
        return 3f * u * u * (p1 - p0) + 6f * u * t * (p2 - p1) + 3f * t * t * (p3 - p2)
    }
}
