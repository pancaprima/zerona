package sh.zeron.zerona.transcript

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import sh.zeron.zerona.ui.theme.TranscriptColorTokens
import sh.zeron.zerona.ui.theme.ZeronaColorTokens
import uniffi.zeron_core.ColorRole

object Palette {
    fun color(
        role: ColorRole,
        dark: Boolean,
    ): Int {
        fun dynamic(
            light: Int,
            darkHex: Int,
            alpha: Float = 1f,
        ): Int {
            val rgb = if (dark) darkHex else light
            val a = (alpha * 255).toInt().coerceIn(0, 255)
            return Color.argb(a, (rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
        }

        fun tokenRgb(argb: Int): Int = argb and 0xFFFFFF

        fun dual(
            lightAlpha: Float,
            darkAlpha: Float,
        ): Int =
            if (dark) {
                Color.argb((darkAlpha * 255).toInt(), 255, 255, 255)
            } else {
                Color.argb((lightAlpha * 255).toInt(), 0, 0, 0)
            }

        return when (role) {
            ColorRole.TEXT -> dynamic(tokenRgb(ZeronaColorTokens.LIGHT_ON_SURFACE), tokenRgb(ZeronaColorTokens.DARK_ON_SURFACE))
            ColorRole.TEXT_SECONDARY ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_ON_SURFACE_VARIANT),
                    tokenRgb(ZeronaColorTokens.DARK_ON_SURFACE_VARIANT),
                )
            ColorRole.TEXT_TERTIARY ->
                dynamic(
                    tokenRgb(TranscriptColorTokens.LIGHT_TEXT_TERTIARY),
                    tokenRgb(TranscriptColorTokens.DARK_TEXT_TERTIARY),
                )
            ColorRole.LINK, ColorRole.ACCENT ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_PRIMARY),
                    tokenRgb(ZeronaColorTokens.DARK_PRIMARY),
                )
            ColorRole.DANGER ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_ERROR),
                    tokenRgb(ZeronaColorTokens.DARK_ERROR),
                )
            ColorRole.SUCCESS ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_SUCCESS),
                    tokenRgb(ZeronaColorTokens.DARK_SUCCESS),
                )
            ColorRole.WARNING -> dynamic(0xA16207, 0xFACC15)
            ColorRole.INLINE_CODE_TEXT ->
                dynamic(
                    tokenRgb(TranscriptColorTokens.LIGHT_INLINE_CODE_TEXT),
                    tokenRgb(TranscriptColorTokens.DARK_INLINE_CODE_TEXT),
                )
            ColorRole.INLINE_CODE_BACKGROUND ->
                dynamic(
                    tokenRgb(TranscriptColorTokens.LIGHT_INLINE_CODE_BACKGROUND),
                    tokenRgb(TranscriptColorTokens.DARK_INLINE_CODE_BACKGROUND),
                )
            ColorRole.CODE_TEXT -> TranscriptColorTokens.CODE_BLOCK_TEXT
            ColorRole.CODE_BACKGROUND -> TranscriptColorTokens.CODE_BLOCK_BACKGROUND
            ColorRole.CODE_BORDER -> TranscriptColorTokens.CODE_BLOCK_BORDER
            ColorRole.QUOTE_BAR -> dynamic(0x7B5FD4, 0xB8A0F0, 0.45f)
            ColorRole.RULE -> dynamic(tokenRgb(ZeronaColorTokens.LIGHT_OUTLINE), tokenRgb(ZeronaColorTokens.DARK_OUTLINE))
            ColorRole.TABLE_BORDER ->
                dynamic(tokenRgb(ZeronaColorTokens.LIGHT_OUTLINE), tokenRgb(ZeronaColorTokens.DARK_OUTLINE))
            ColorRole.TABLE_HEADER_BACKGROUND ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_SURFACE_VARIANT),
                    tokenRgb(ZeronaColorTokens.DARK_SURFACE_VARIANT),
                )
            ColorRole.USER_BUBBLE ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_PRIMARY_CONTAINER),
                    tokenRgb(ZeronaColorTokens.DARK_PRIMARY_CONTAINER),
                )
            ColorRole.CHIP_BACKGROUND ->
                dynamic(
                    tokenRgb(ZeronaColorTokens.LIGHT_SURFACE_VARIANT),
                    tokenRgb(ZeronaColorTokens.DARK_SURFACE_CONTAINER_HIGH),
                )
            ColorRole.SYNTAX_KEYWORD -> TranscriptColorTokens.SYNTAX_KEYWORD
            ColorRole.SYNTAX_STRING -> TranscriptColorTokens.SYNTAX_STRING
            ColorRole.SYNTAX_COMMENT -> TranscriptColorTokens.SYNTAX_COMMENT
            ColorRole.SYNTAX_NUMBER, ColorRole.SYNTAX_CONSTANT -> TranscriptColorTokens.SYNTAX_NUMBER
            ColorRole.SYNTAX_FUNCTION -> TranscriptColorTokens.SYNTAX_FUNCTION
            ColorRole.SYNTAX_TYPE -> TranscriptColorTokens.SYNTAX_TYPE
            ColorRole.SYNTAX_VARIABLE -> TranscriptColorTokens.SYNTAX_VARIABLE
            ColorRole.SYNTAX_PROPERTY -> TranscriptColorTokens.SYNTAX_PROPERTY
            ColorRole.SYNTAX_OPERATOR, ColorRole.SYNTAX_PUNCTUATION -> TranscriptColorTokens.SYNTAX_OPERATOR
            ColorRole.SYNTAX_TAG -> TranscriptColorTokens.SYNTAX_TAG
            ColorRole.SYNTAX_ATTRIBUTE -> TranscriptColorTokens.SYNTAX_ATTRIBUTE
            ColorRole.SYNTAX_ESCAPE -> TranscriptColorTokens.SYNTAX_ESCAPE
            ColorRole.TEXT_FAINT ->
                dynamic(
                    tokenRgb(TranscriptColorTokens.LIGHT_TEXT_FAINT),
                    tokenRgb(TranscriptColorTokens.DARK_TEXT_FAINT),
                )
            ColorRole.TEXT_SOFT -> dynamic(0x303035, 0xE8E8EA, 0.85f)
            ColorRole.TOOL_RAIL -> dual(0.162f, 0.12f)
            ColorRole.TOOL_BADGE -> dual(0.06f, 0.06f)
            ColorRole.TOOL_WELL -> if (dark) Color.argb(41, 0, 0, 0) else Color.argb(41, 255, 255, 255)
            ColorRole.AGENT_CARD -> dual(0.03f, 0.03f)
            ColorRole.AGENT_CARD_BORDER -> dual(0.0945f, 0.07f)
            ColorRole.AGENT_TILE -> dual(0.08f, 0.08f)
            ColorRole.DIFF_ADD_WASH -> dynamic(0x15803D, 0x34D399, 0.055f)
            ColorRole.DIFF_DEL_WASH -> dynamic(0xDC2626, 0xF87171, 0.055f)
            ColorRole.DIFF_ADD_BAR -> dynamic(0x15803D, 0x34D399, 0.55f)
            ColorRole.DIFF_DEL_BAR -> dynamic(0xDC2626, 0xF87171, 0.55f)
            ColorRole.DIFF_HUNK ->
                if (dark) Color.argb(20, 0xB8, 0xA0, 0xF0) else Color.argb(18, 0x7B, 0x5F, 0xD4)
        }
    }

    fun background(dark: Boolean): Int = ZeronaColorTokens.backgroundArgb(dark)

    @Composable
    fun isDark(): Boolean = isSystemInDarkTheme()
}
