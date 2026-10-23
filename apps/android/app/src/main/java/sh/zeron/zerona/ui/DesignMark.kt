package sh.zeron.zerona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Neutral decorative mark (not launcher icon). */
@Composable
fun ZeronaDecorMark(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 40.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(sizeDp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(scheme.primary, scheme.tertiary),
                    ),
                ),
    )
}
