package sh.zeron.zerona.transcript

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import uniffi.zeron_core.BoxStyle
import uniffi.zeron_core.Decoration
import uniffi.zeron_core.FadeEdge
import uniffi.zeron_core.RowDisplay

/**
 * Precomputed draw data for one [RowDisplay] at a fixed width (built on main thread).
 */
class RowDrawModel(
    val display: RowDisplay,
    private val fonts: StyleFonts,
    private val dark: Boolean,
) {
    data class PreparedRun(
        val index: Int,
        val text: String,
        val paint: Paint,
        val decoration: Decoration,
        val width: Float,
    )

    val runsByLayer: List<List<Int>>
    val boxesByLayer: List<List<Int>>
    val glyphsByLayer: List<List<Int>>
    val fadesByLayer: List<List<Pair<Int, List<Int>>>>
    private val fadedRuns: Set<Int>
    val preparedRuns: List<PreparedRun?>

    init {
        val layers = display.scrollers.size + 1
        val runs = Array(layers) { mutableListOf<Int>() }
        val boxes = Array(layers) { mutableListOf<Int>() }
        val glyphs = Array(layers) { mutableListOf<Int>() }
        val prepared = ArrayList<PreparedRun?>(display.runs.size)

        for ((i, run) in display.runs.withIndex()) {
            val slice =
                if (TranscriptUtf16.isValidRange(display.text, run.start.toInt(), run.len.toInt())) {
                    TranscriptUtf16.slice(display.text, run.start.toInt(), run.len.toInt())
                } else {
                    ""
                }
            val color = Palette.color(run.color, dark)
            val paint = fonts.paintFor(run.style, color)
            prepared.add(
                if (paint != null && slice.isNotEmpty()) {
                    PreparedRun(i, slice, paint, run.decoration, run.width)
                } else {
                    null
                },
            )
            val layer = run.scroller?.let { it.toInt() + 1 } ?: 0
            runs[layer].add(i)
        }
        for ((i, box) in display.boxes.withIndex()) {
            val layer = box.scroller?.let { it.toInt() + 1 } ?: 0
            boxes[layer].add(i)
        }
        for ((i, g) in display.glyphs.withIndex()) {
            val layer = g.scroller?.let { it.toInt() + 1 } ?: 0
            glyphs[layer].add(i)
        }
        preparedRuns = prepared
        runsByLayer = runs.map { it.toList() }
        boxesByLayer = boxes.map { it.toList() }
        glyphsByLayer = glyphs.map { it.toList() }

        val fades = Array(layers) { mutableListOf<Pair<Int, List<Int>>>() }
        val faded = mutableSetOf<Int>()
        for ((fi, f) in display.fades.withIndex()) {
            val layer = f.scroller?.let { it.toInt() + 1 } ?: 0
            val masked =
                runs[layer].filter { ri ->
                    val r = display.runs[ri]
                    if (faded.contains(ri)) return@filter false
                    if (r.baseline <= f.y || r.baseline > f.y + f.h + 0.5f) return@filter false
                    f.edge != FadeEdge.TRAILING || r.x < f.x + f.w
                }
            faded.addAll(masked)
            fades[layer].add(fi to masked)
        }
        fadesByLayer = fades.map { it.toList() }
        fadedRuns = faded
    }

    enum class Pass {
        ALL,
        SETTLED,
        FRESH,
    }

    fun draw(
        canvas: Canvas,
        layer: Int,
        hairline: Float,
        pass: Pass = Pass.ALL,
        veilFrom: Int = 0,
    ) {
        drawBoxes(canvas, layer, hairline, pass, veilFrom)
        drawRuns(canvas, layer, hairline, pass, veilFrom)
        drawGlyphs(canvas, layer, pass, veilFrom)
        if (pass != Pass.FRESH) {
            drawFades(canvas, layer, hairline)
        }
    }

    private fun drawBoxes(
        canvas: Canvas,
        layer: Int,
        hairline: Float,
        pass: Pass,
        veilFrom: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in boxesByLayer[layer]) {
            val b = display.boxes[i]
            when (pass) {
                Pass.ALL -> Unit
                Pass.SETTLED -> {
                    val start = b.start?.toInt()
                    if (start != null && start >= veilFrom) continue
                }
                Pass.FRESH -> {
                    val start = b.start?.toInt()
                    if (start == null || start < veilFrom) continue
                }
            }
            val rect = RectF(b.x, b.y, b.x + b.w, b.y + b.h)
            paint.color = Palette.color(b.color, dark)
            when (b.style) {
                BoxStyle.FILL -> {
                    if (b.radius > 0f) {
                        canvas.drawRoundRect(rect, b.radius, b.radius, paint)
                    } else {
                        canvas.drawRect(rect, paint)
                    }
                }
                BoxStyle.HAIRLINE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = hairline
                    val inset = hairline / 2f
                    val r = (b.radius - hairline / 2f).coerceAtLeast(0f)
                    rect.inset(inset, inset)
                    canvas.drawRoundRect(rect, r, r, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    private fun drawRuns(
        canvas: Canvas,
        layer: Int,
        hairline: Float,
        pass: Pass,
        veilFrom: Int,
    ) {
        for (ri in runsByLayer[layer]) {
            if (fadedRuns.contains(ri)) continue
            val run = display.runs[ri]
            val prepared = preparedRuns[ri] ?: continue
            when (pass) {
                Pass.SETTLED -> if (run.start.toInt() >= veilFrom) continue
                Pass.FRESH -> if (run.start.toInt() + run.len.toInt() <= veilFrom) continue
                Pass.ALL -> Unit
            }
            canvas.save()
            when (pass) {
                Pass.SETTLED ->
                    if (run.start.toInt() + run.len.toInt() > veilFrom && run.start.toInt() < veilFrom) {
                        val fraction =
                            (veilFrom - run.start.toInt()).toFloat() / run.len.toFloat().coerceAtLeast(1f)
                        canvas.clipRect(
                            run.x,
                            -10_000f,
                            run.x + prepared.width * fraction.coerceIn(0f, 1f),
                            10_000f,
                        )
                    }
                Pass.FRESH ->
                    if (run.start.toInt() < veilFrom && run.start.toInt() + run.len.toInt() > veilFrom) {
                        val fraction =
                            (veilFrom - run.start.toInt()).toFloat() / run.len.toFloat().coerceAtLeast(1f)
                        canvas.clipRect(
                            run.x + prepared.width * fraction.coerceIn(0f, 1f),
                            -10_000f,
                            run.x + 10_000f,
                            10_000f,
                        )
                    }
                Pass.ALL -> Unit
            }
            canvas.drawText(prepared.text, run.x, run.baseline, prepared.paint)
            when (prepared.decoration) {
                Decoration.UNDERLINE -> {
                    val y = run.baseline + 2f
                    canvas.drawRect(run.x, y, run.x + prepared.width, y + hairline.coerceAtLeast(1f), prepared.paint)
                }
                Decoration.STRIKETHROUGH -> {
                    val y = run.baseline - 5f
                    canvas.drawRect(run.x, y, run.x + prepared.width, y + hairline.coerceAtLeast(1f), prepared.paint)
                }
                Decoration.NONE -> Unit
            }
            canvas.restore()
        }
    }

    private fun drawGlyphs(
        canvas: Canvas,
        layer: Int,
        pass: Pass,
        veilFrom: Int,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                color = Palette.color(uniffi.zeron_core.ColorRole.TEXT_TERTIARY, dark)
            }
        for (gi in glyphsByLayer[layer]) {
            val g = display.glyphs[gi]
            when (pass) {
                Pass.SETTLED -> if (g.start.toInt() >= veilFrom) continue
                Pass.FRESH -> if (g.start.toInt() < veilFrom) continue
                Pass.ALL -> Unit
            }
            paint.color = Palette.color(g.color, dark)
            if (MathFonts.canDrawGlyphs()) {
                val scale = g.size / MathFonts.MATH_REFERENCE_SIZE
                canvas.save()
                canvas.translate(g.x, g.y)
                canvas.scale(scale, scale)
                paint.typeface = null
                paint.textSize = MathFonts.MATH_REFERENCE_SIZE
                MathFonts.drawGlyph(canvas, g.glyph.toInt(), g.font, paint)
                canvas.restore()
            } else {
                val w = g.size * 0.75f
                val h = g.size
                val top = g.y - h
                paint.style = Paint.Style.FILL
                paint.alpha = 40
                canvas.drawRoundRect(g.x, top, g.x + w, g.y, 2f, 2f, paint)
                paint.alpha = 255
                canvas.drawText("Math", g.x + 2f, g.y - 2f, labelPaint)
            }
        }
    }

    private fun drawFades(
        canvas: Canvas,
        layer: Int,
        hairline: Float,
    ) {
        for ((fi, runs) in fadesByLayer[layer]) {
            if (runs.isEmpty()) continue
            val f = display.fades[fi]
            val save = canvas.saveLayer(
                f.x - 40f,
                f.y - 40f,
                f.x + f.w + 40f,
                f.y + f.h + 80f,
                null,
            )
            for (ri in runs) {
                val run = display.runs[ri]
                val prepared = preparedRuns[ri] ?: continue
                canvas.drawText(prepared.text, run.x, run.baseline, prepared.paint)
            }
            val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
            when (f.edge) {
                FadeEdge.TRAILING -> {
                    erase.shader =
                        LinearGradient(
                            f.x,
                            0f,
                            f.x + f.w,
                            0f,
                            intArrayOf(0x00000000, 0xFF000000.toInt()),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    canvas.drawRect(f.x, f.y - 40f, f.x + f.w, f.y + f.h + 80f, erase)
                }
                FadeEdge.BOTTOM -> {
                    erase.shader =
                        LinearGradient(
                            0f,
                            f.y,
                            0f,
                            f.y + f.h,
                            intArrayOf(0x00000000, 0xEB000000.toInt()),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    canvas.drawRect(f.x - 40f, f.y, f.x + f.w + 40f, f.y + f.h + 40f, erase)
                }
            }
            canvas.restoreToCount(save)
        }
    }
}
