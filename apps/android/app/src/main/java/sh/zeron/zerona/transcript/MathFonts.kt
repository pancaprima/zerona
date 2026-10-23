package sh.zeron.zerona.transcript

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build

/**
 * Math faces from [`mathFontData`], built at [MATH_REFERENCE_SIZE] (matches iOS/core layout).
 * API 31+ glyph drawing lives in [MathGlyphsApi31] (loaded only on S+).
 */
object MathFonts {
    /** Layout paints math glyphs at this em size before scaling to [GlyphDisplay.size]. */
    const val MATH_REFERENCE_SIZE = 16f

    fun canDrawGlyphs(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun drawGlyph(
        canvas: Canvas,
        glyphId: Int,
        fontIndex: UByte,
        paint: Paint,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MathGlyphsApi31.drawGlyph(canvas, glyphId, fontIndex, paint)
        }
    }
}
