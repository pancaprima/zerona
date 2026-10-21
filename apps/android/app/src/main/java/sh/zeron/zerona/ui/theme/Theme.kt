package sh.zeron.zerona.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors =
    lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    )

private val DarkColors =
    darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFE8E8E8),
    )

@Composable
fun ZeronaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
