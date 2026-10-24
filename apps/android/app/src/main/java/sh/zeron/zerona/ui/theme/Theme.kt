package sh.zeron.zerona.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors =
    lightColorScheme(
        primary = Color(ZeronaColorTokens.LIGHT_PRIMARY),
        onPrimary = Color(ZeronaColorTokens.LIGHT_ON_PRIMARY),
        primaryContainer = Color(ZeronaColorTokens.LIGHT_PRIMARY_CONTAINER),
        onPrimaryContainer = Color(ZeronaColorTokens.LIGHT_ON_PRIMARY_CONTAINER),
        secondary = Color(ZeronaColorTokens.LIGHT_SECONDARY),
        onSecondary = Color(ZeronaColorTokens.LIGHT_ON_SECONDARY),
        secondaryContainer = Color(ZeronaColorTokens.LIGHT_SECONDARY_CONTAINER),
        onSecondaryContainer = Color(ZeronaColorTokens.LIGHT_ON_SECONDARY_CONTAINER),
        tertiary = Color(ZeronaColorTokens.LIGHT_TERTIARY),
        onTertiary = Color(ZeronaColorTokens.LIGHT_ON_TERTIARY),
        tertiaryContainer = Color(ZeronaColorTokens.LIGHT_TERTIARY_CONTAINER),
        onTertiaryContainer = Color(ZeronaColorTokens.LIGHT_ON_TERTIARY_CONTAINER),
        background = Color(ZeronaColorTokens.LIGHT_BACKGROUND),
        onBackground = Color(ZeronaColorTokens.LIGHT_ON_SURFACE),
        surface = Color(ZeronaColorTokens.LIGHT_SURFACE),
        onSurface = Color(ZeronaColorTokens.LIGHT_ON_SURFACE),
        surfaceVariant = Color(ZeronaColorTokens.LIGHT_SURFACE_VARIANT),
        onSurfaceVariant = Color(ZeronaColorTokens.LIGHT_ON_SURFACE_VARIANT),
        surfaceContainer = Color(ZeronaColorTokens.LIGHT_SURFACE_CONTAINER),
        surfaceContainerHigh = Color(ZeronaColorTokens.LIGHT_SURFACE_CONTAINER_HIGH),
        outline = Color(ZeronaColorTokens.LIGHT_OUTLINE),
        outlineVariant = Color(ZeronaColorTokens.LIGHT_OUTLINE_VARIANT),
        error = Color(ZeronaColorTokens.LIGHT_ERROR),
        onError = Color(ZeronaColorTokens.LIGHT_ON_ERROR),
        errorContainer = Color(ZeronaColorTokens.LIGHT_ERROR_CONTAINER),
        onErrorContainer = Color(ZeronaColorTokens.LIGHT_ON_ERROR_CONTAINER),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(ZeronaColorTokens.DARK_PRIMARY),
        onPrimary = Color(ZeronaColorTokens.DARK_ON_PRIMARY),
        primaryContainer = Color(ZeronaColorTokens.DARK_PRIMARY_CONTAINER),
        onPrimaryContainer = Color(ZeronaColorTokens.DARK_ON_PRIMARY_CONTAINER),
        secondary = Color(ZeronaColorTokens.DARK_SECONDARY),
        onSecondary = Color(ZeronaColorTokens.DARK_ON_SECONDARY),
        secondaryContainer = Color(ZeronaColorTokens.DARK_SECONDARY_CONTAINER),
        onSecondaryContainer = Color(ZeronaColorTokens.DARK_ON_SECONDARY_CONTAINER),
        tertiary = Color(ZeronaColorTokens.DARK_TERTIARY),
        onTertiary = Color(ZeronaColorTokens.DARK_ON_TERTIARY),
        tertiaryContainer = Color(ZeronaColorTokens.DARK_TERTIARY_CONTAINER),
        onTertiaryContainer = Color(ZeronaColorTokens.DARK_ON_TERTIARY_CONTAINER),
        background = Color(ZeronaColorTokens.DARK_BACKGROUND),
        onBackground = Color(ZeronaColorTokens.DARK_ON_SURFACE),
        surface = Color(ZeronaColorTokens.DARK_SURFACE),
        onSurface = Color(ZeronaColorTokens.DARK_ON_SURFACE),
        surfaceVariant = Color(ZeronaColorTokens.DARK_SURFACE_VARIANT),
        onSurfaceVariant = Color(ZeronaColorTokens.DARK_ON_SURFACE_VARIANT),
        surfaceContainer = Color(ZeronaColorTokens.DARK_SURFACE_CONTAINER),
        surfaceContainerHigh = Color(ZeronaColorTokens.DARK_SURFACE_CONTAINER_HIGH),
        outline = Color(ZeronaColorTokens.DARK_OUTLINE),
        outlineVariant = Color(ZeronaColorTokens.DARK_OUTLINE_VARIANT),
        error = Color(ZeronaColorTokens.DARK_ERROR),
        onError = Color(ZeronaColorTokens.DARK_ON_ERROR),
        errorContainer = Color(ZeronaColorTokens.DARK_ERROR_CONTAINER),
        onErrorContainer = Color(ZeronaColorTokens.DARK_ON_ERROR_CONTAINER),
    )

private val ZeronaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

private val ZeronaTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleSmall =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.02.sp,
            ),
        bodyMedium =
            TextStyle(
                fontSize = 15.sp,
                lineHeight = 21.sp,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
    )

@Composable
fun ZeronaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        typography = ZeronaTypography,
        shapes = ZeronaShapes,
    ) {
        ZeronaOrnamentalMotionProvider(content = content)
    }
}
