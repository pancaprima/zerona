package sh.zeron.zerona.transcript

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
            ColorRole.TEXT -> dynamic(0x27272C, 0xE8E8EA)
            ColorRole.TEXT_SECONDARY -> dynamic(0x62626A, 0xA9A9AE)
            ColorRole.TEXT_TERTIARY -> dynamic(0x97979F, 0x6B6B72)
            ColorRole.LINK, ColorRole.ACCENT -> dynamic(0x5B43E8, 0x8B7CF6)
            ColorRole.DANGER -> dynamic(0xDC2626, 0xF87171)
            ColorRole.SUCCESS -> dynamic(0x15803D, 0x34D399)
            ColorRole.WARNING -> dynamic(0xA16207, 0xFACC15)
            ColorRole.INLINE_CODE_TEXT -> dynamic(0x3F3F46, 0xDCDCE0)
            ColorRole.INLINE_CODE_BACKGROUND -> dynamic(0xE9E9ED, 0x1A1A1E)
            ColorRole.CODE_TEXT -> dynamic(0x303035, 0xE8E8EA)
            ColorRole.CODE_BACKGROUND -> dynamic(0xFAFAFB, 0x0B0B0D)
            ColorRole.CODE_BORDER -> dynamic(0xE4E4E8, 0x1F1F23)
            ColorRole.QUOTE_BAR -> dynamic(0x5B43E8, 0x8B7CF6, 0.45f)
            ColorRole.RULE -> dynamic(0xE2E2E6, 0x1E1E22)
            ColorRole.TABLE_BORDER -> dynamic(0xE2E2E6, 0x232327)
            ColorRole.TABLE_HEADER_BACKGROUND -> dynamic(0xF3F3F5, 0x121215)
            ColorRole.USER_BUBBLE -> dynamic(0xFFFFFF, 0x19191C)
            ColorRole.CHIP_BACKGROUND -> dynamic(0xE7E7EB, 0x1C1C20)
            ColorRole.SYNTAX_KEYWORD -> dynamic(0x5B43E8, 0x8B7CF6)
            ColorRole.SYNTAX_STRING -> dynamic(0x15803D, 0x34D399)
            ColorRole.SYNTAX_COMMENT -> dynamic(0x6B7280, 0x92929A)
            ColorRole.SYNTAX_NUMBER, ColorRole.SYNTAX_CONSTANT -> dynamic(0xA16207, 0xFACC15)
            ColorRole.SYNTAX_FUNCTION -> dynamic(0x2563EB, 0x60A5FA)
            ColorRole.SYNTAX_TYPE -> dynamic(0x7E22CE, 0xC084FC)
            ColorRole.SYNTAX_VARIABLE -> dynamic(0x303035, 0xE8E8EA)
            ColorRole.SYNTAX_PROPERTY -> dynamic(0x0E7490, 0x22D3EE)
            ColorRole.SYNTAX_OPERATOR, ColorRole.SYNTAX_PUNCTUATION -> dynamic(0x52525B, 0xA1A1AA)
            ColorRole.SYNTAX_TAG -> dynamic(0xBE185D, 0xF472B6)
            ColorRole.SYNTAX_ATTRIBUTE -> dynamic(0xB91C1C, 0xF87171)
            ColorRole.SYNTAX_ESCAPE -> dynamic(0x0E7490, 0x22D3EE)
            ColorRole.TEXT_FAINT -> dynamic(0x797981, 0x85858A)
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
                if (dark) Color.argb(20, 0x8B, 0x7C, 0xF6) else Color.argb(18, 0x5B, 0x43, 0xE8)
        }
    }

    fun background(dark: Boolean): Int = if (dark) 0xFF060606.toInt() else 0xFFF3F3F5.toInt()

    @Composable
    fun isDark(): Boolean = isSystemInDarkTheme()
}
