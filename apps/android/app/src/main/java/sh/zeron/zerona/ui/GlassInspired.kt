package sh.zeron.zerona.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import sh.zeron.zerona.ui.theme.ZeronaColorTokens

/** Opaque tonal surfaces (no backdrop blur). Edge Dock uses dock tokens directly. */
object GlassInspired {
    @Composable
    fun composerDockColor(): Color {
        val dark = isSystemInDarkTheme()
        return Color(ZeronaColorTokens.composerDockArgb(dark))
    }

    @Composable
    fun composerBackdropColor(): Color = composerDockColor()

    @Composable
    fun tonalDividerColor(): Color = MaterialTheme.colorScheme.outline
}
