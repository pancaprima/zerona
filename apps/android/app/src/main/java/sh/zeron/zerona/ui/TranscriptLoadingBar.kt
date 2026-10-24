package sh.zeron.zerona.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.ui.theme.ZeronaColorTokens

/** Indeterminate transcript hydration bar (desktop `transcript_loading_bar` analogue). */
object TranscriptLoadingBarMotion {
    const val PERIOD_MS = 1400
    const val FILL_FRAC = 0.38f

    fun segmentOffset(phase: Float): Float {
        val raw = phase.coerceIn(0f, 1f)
        val travel = 1f - FILL_FRAC
        return raw * travel
    }
}

@Composable
fun TranscriptLoadingBar(
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val track =
        Color(
            if (dark) {
                ZeronaColorTokens.DARK_PRIMARY_CONTAINER
            } else {
                ZeronaColorTokens.LIGHT_PRIMARY_CONTAINER
            },
        )
    val fill =
        Color(
            if (dark) {
                ZeronaColorTokens.DARK_PRIMARY
            } else {
                ZeronaColorTokens.LIGHT_PRIMARY
            },
        )
    val marker =
        Color(
            if (dark) {
                ZeronaColorTokens.DARK_PRIMARY
            } else {
                ZeronaColorTokens.LIGHT_DECORATIVE_LILAC
            },
        )
    val transition = rememberInfiniteTransition(label = "transcript-load")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(TranscriptLoadingBarMotion.PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "phase",
    )
    val offset = TranscriptLoadingBarMotion.segmentOffset(phase)
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(3.dp)
                .semantics { contentDescription = "Loading conversation" },
    ) {
        val h = size.height
        val w = size.width
        if (w <= 0f || h <= 0f) return@Canvas
        val radius = h / 2f
        drawRoundRect(
            color = track,
            cornerRadius = CornerRadius(radius, radius),
            size = Size(w, h),
        )
        val fillW = w * TranscriptLoadingBarMotion.FILL_FRAC
        val left = offset * (w - fillW)
        drawRoundRect(
            color = fill,
            topLeft = Offset(left, 0f),
            size = Size(fillW, h),
            cornerRadius = CornerRadius(radius, radius),
        )
        val markerSize = h + 2f
        drawCircle(
            color = marker,
            radius = markerSize / 2f,
            center = Offset(w - markerSize / 2f, h / 2f),
        )
    }
}
