package sh.zeron.zerona.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.ui.theme.ZeronaMotionPolicy
import sh.zeron.zerona.ui.theme.rememberOrnamentalMotionEnabled

/** Duration of one ornamental loading breathe half-cycle. */
private const val LoadingOrnamentalBreatheMillis = 1_800

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZeronaInitialLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ornamental = rememberOrnamentalMotionEnabled()
    val motion = MaterialTheme.motionScheme
    var entranceArmed by remember(ornamental) { mutableStateOf(!ornamental) }
    LaunchedEffect(ornamental) {
        if (ornamental) {
            entranceArmed = true
        }
    }
    val entrance by animateFloatAsState(
        targetValue = if (!ornamental || entranceArmed) 1f else 0f,
        animationSpec =
            if (ornamental) {
                motion.defaultEffectsSpec()
            } else {
                snap()
            },
        label = "loadingEntrance",
    )
    val breatheScale =
        if (ornamental) {
            val infinite = rememberInfiniteTransition(label = "loadingBreathe")
            infinite
                .animateFloat(
                    initialValue = ZeronaMotionPolicy.LOADING_ORNAMENTAL_MIN_SCALE,
                    targetValue = ZeronaMotionPolicy.LOADING_ORNAMENTAL_MAX_SCALE,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                tween(
                                    durationMillis = LoadingOrnamentalBreatheMillis,
                                ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "loadingBreatheScale",
                )
                .value
        } else {
            ZeronaMotionPolicy.LOADING_ORNAMENTAL_MIN_SCALE
        }
    val entranceScale =
        ZeronaMotionPolicy.LOADING_ENTRANCE_MIN_SCALE +
            (1f - ZeronaMotionPolicy.LOADING_ENTRANCE_MIN_SCALE) * entrance
    Column(
        modifier =
            modifier.graphicsLayer {
                alpha = entrance
                scaleX = entranceScale
                scaleY = entranceScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier.graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                },
            contentAlignment = Alignment.Center,
        ) {
            if (ornamental) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = scheme.primary,
                )
            } else {
                ZeronaStaticWaitDecoration(
                    modifier = Modifier.size(48.dp),
                    color = scheme.primary,
                )
            }
        }
        Text(
            label,
            modifier =
                Modifier
                    .padding(top = 16.dp)
                    .graphicsLayer { alpha = entrance },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
    }
}

/** Non-animated ring used when system animator duration scale is 0 (no indeterminate spinner). */
@Composable
private fun ZeronaStaticWaitDecoration(
    modifier: Modifier,
    color: Color,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(3.dp, color.copy(alpha = 0.5f)),
    ) {}
}
