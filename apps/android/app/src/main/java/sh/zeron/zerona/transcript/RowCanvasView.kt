package sh.zeron.zerona.transcript

import android.content.Context
import android.graphics.Canvas
import android.view.View

class RowCanvasView(
    context: Context,
) : View(context) {
    var model: RowDrawModel? = null
    var layerIndex: Int = 0
    var pass: RowDrawModel.Pass = RowDrawModel.Pass.ALL
    var veilFrom: Int = 0
    var dark: Boolean = false
    /** Logical layout density (screen density); canvas draws in Rust dp units. */
    var layoutDensity: Float = 1f

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val m = model ?: return
        val density = layoutDensity.coerceAtLeast(1f)
        canvas.save()
        canvas.scale(density, density)
        val hairline = 1f / density
        m.draw(canvas, layerIndex, hairline, pass, veilFrom)
        canvas.restore()
    }
}
