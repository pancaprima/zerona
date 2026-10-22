package sh.zeron.zerona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Readability-first “glass-inspired” surfaces (opaque backing, no backdrop blur). */
object GlassInspired {
    @Composable
    fun composerBackdropColor(): Color {
        val scheme = MaterialTheme.colorScheme
        return if (isSystemInDarkTheme()) {
            scheme.surface
        } else {
            scheme.background
        }
    }

    @Composable
    fun composerCardModifier(): Modifier {
        val dark = isSystemInDarkTheme()
        val scheme = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(20.dp)
        val base = if (dark) scheme.surfaceContainerHigh else scheme.surface
        val sheen =
            if (dark) {
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                        ),
                )
            } else {
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.04f),
                        ),
                )
            }
        val borderColor =
            if (dark) {
                Color.White.copy(alpha = 0.14f)
            } else {
                Color.Black.copy(alpha = 0.08f)
            }
        return Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .background(base, shape)
            .background(sheen, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
    }

    @Composable
    fun GlassCard(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        Box(modifier = modifier.then(composerCardModifier())) {
            content()
        }
    }
}
