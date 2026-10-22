package sh.zeron.zerona.transcript

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.fonts.Font
import android.os.Build
import androidx.annotation.RequiresApi
import uniffi.zeron_core.mathFontData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.S)
internal object MathGlyphsApi31 {
    private val loaded = ConcurrentHashMap<UByte, Font>()
    private val missing = ConcurrentHashMap.newKeySet<UByte>()

    fun font(index: UByte): Font? {
        if (index in missing) return null
        loaded[index]?.let { return it }
        val bytes = mathFontData(index)
        if (bytes.isEmpty()) {
            missing.add(index)
            return null
        }
        val buffer =
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                flip()
            }
        val font =
            try {
                Font.Builder(buffer).build()
            } catch (_: Exception) {
                missing.add(index)
                return null
            }
        loaded[index] = font
        return font
    }

    fun drawGlyph(
        canvas: Canvas,
        glyphId: Int,
        fontIndex: UByte,
        paint: Paint,
    ) {
        val font = font(fontIndex) ?: return
        canvas.drawGlyphs(
            intArrayOf(glyphId),
            0,
            floatArrayOf(0f, 0f),
            0,
            1,
            font,
            paint,
        )
    }
}
