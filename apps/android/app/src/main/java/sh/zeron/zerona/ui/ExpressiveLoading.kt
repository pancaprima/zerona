package sh.zeron.zerona.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.ui.theme.rememberOrnamentalMotionEnabled

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZeronaInitialLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ornamental = rememberOrnamentalMotionEnabled()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
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
        Text(
            label,
            modifier = Modifier.padding(top = 16.dp),
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
