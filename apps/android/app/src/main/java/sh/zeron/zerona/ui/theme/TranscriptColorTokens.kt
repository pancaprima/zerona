package sh.zeron.zerona.ui.theme

/**
 * Transcript markdown colours (opaque ARGB). [sh.zeron.zerona.transcript.Palette] maps
 * fenced-code and inline-code roles from these constants so JVM tests exercise production RGB.
 */
object TranscriptColorTokens {
    /** Assistant prose card (study01 chat); mapped from core `ProseCard` roles. */
    const val LIGHT_PROSE_CARD = 0xFFFFFFFF.toInt()
    const val DARK_PROSE_CARD = 0xFF1C1B22.toInt()
    const val LIGHT_PROSE_CARD_BORDER = 0xFFE2DEE8.toInt()
    const val DARK_PROSE_CARD_BORDER = 0xFF3A3844.toInt()

    fun proseCardArgb(dark: Boolean): Int = if (dark) DARK_PROSE_CARD else LIGHT_PROSE_CARD

    fun proseCardBorderArgb(dark: Boolean): Int =
        if (dark) DARK_PROSE_CARD_BORDER else LIGHT_PROSE_CARD_BORDER

    /** Fenced code blocks use a dark stage in both UI themes (catalog mock). */
    const val CODE_BLOCK_BACKGROUND = 0xFF0E0D12.toInt()
    const val CODE_BLOCK_TEXT = 0xFFECE8F4.toInt()
    const val CODE_BLOCK_BORDER = 0xFF48445A.toInt()

    const val SYNTAX_KEYWORD = 0xFFB8A0F0.toInt()
    const val SYNTAX_STRING = 0xFF34D399.toInt()
    const val SYNTAX_COMMENT = 0xFF92929A.toInt()
    const val SYNTAX_NUMBER = 0xFFFACC15.toInt()
    const val SYNTAX_FUNCTION = 0xFF60A5FA.toInt()
    const val SYNTAX_TYPE = 0xFFC084FC.toInt()
    const val SYNTAX_VARIABLE = 0xFFE8E8EA.toInt()
    const val SYNTAX_PROPERTY = 0xFF22D3EE.toInt()
    const val SYNTAX_OPERATOR = 0xFFA1A1AA.toInt()
    const val SYNTAX_TAG = 0xFFF472B6.toInt()
    const val SYNTAX_ATTRIBUTE = 0xFFF87171.toInt()
    const val SYNTAX_ESCAPE = 0xFF22D3EE.toInt()

    const val LIGHT_INLINE_CODE_TEXT = 0xFF2D1F52.toInt()
    const val DARK_INLINE_CODE_TEXT = 0xFFECE8F4.toInt()
    const val LIGHT_INLINE_CODE_BACKGROUND = 0xFFEEEAF4.toInt()
    const val DARK_INLINE_CODE_BACKGROUND = 0xFF26242E.toInt()

    fun inlineCodeTextArgb(dark: Boolean): Int =
        if (dark) DARK_INLINE_CODE_TEXT else LIGHT_INLINE_CODE_TEXT

    fun inlineCodeBackgroundArgb(dark: Boolean): Int =
        if (dark) DARK_INLINE_CODE_BACKGROUND else LIGHT_INLINE_CODE_BACKGROUND

    /** Readable labels on prose and chip surfaces (not disabled-state grey). */
    const val LIGHT_TEXT_TERTIARY = ZeronaColorTokens.LIGHT_ON_SURFACE_VARIANT
    const val DARK_TEXT_TERTIARY = ZeronaColorTokens.DARK_ON_SURFACE_VARIANT
    const val LIGHT_TEXT_FAINT = 0xFF5A5960.toInt()
    const val DARK_TEXT_FAINT = 0xFF9A9AA0.toInt()

    fun textTertiaryArgb(dark: Boolean): Int =
        if (dark) DARK_TEXT_TERTIARY else LIGHT_TEXT_TERTIARY

    fun textFaintArgb(dark: Boolean): Int =
        if (dark) DARK_TEXT_FAINT else LIGHT_TEXT_FAINT
}
