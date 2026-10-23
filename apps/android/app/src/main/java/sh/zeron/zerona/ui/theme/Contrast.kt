package sh.zeron.zerona.ui.theme

/**
 * WCAG relative luminance contrast for design-token verification (pure JVM).
 *
 * Callers must pass fully opaque ARGB colours; the alpha channel is ignored.
 */
object Contrast {
    const val AA_NORMAL_TEXT_MIN = 4.5

    fun contrastRatio(
        foregroundArgb: Int,
        backgroundArgb: Int,
    ): Double {
        val l1 = relativeLuminance(foregroundArgb)
        val l2 = relativeLuminance(backgroundArgb)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun meetsAaNormalText(ratio: Double): Boolean = ratio >= AA_NORMAL_TEXT_MIN

    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
