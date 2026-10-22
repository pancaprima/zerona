package sh.zeron.zerona.transcript

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.app.AlertDialog
import uniffi.zeron_core.ColorRole
import uniffi.zeron_core.WidgetKind

internal fun buildWidgetView(
    context: Context,
    kind: WidgetKind,
    payload: String?,
    rowKey: ULong,
    dark: Boolean,
    actions: TranscriptActions,
    detailToggle: (ULong, Boolean) -> Unit,
): View {
    return when (kind) {
        WidgetKind.CopyCode -> CopyCodeButton(context, payload ?: "")
        is WidgetKind.Disclosure ->
            DisclosureTapView(context, expanded = kind.expanded) {
                actions.toggleRow(rowKey)
            }
        is WidgetKind.ToolStatus -> ToolStatusView(context, kind.running, kind.failed)
        is WidgetKind.Image -> ImagePlaceholderView(context, kind.reference)
        is WidgetKind.Working -> WorkingLabelView(context, kind.sinceMs, kind.streaming)
        WidgetKind.Spinner -> ProgressBar(context).apply { isIndeterminate = true }
        is WidgetKind.Detail ->
            DetailTapView(context, kind.title, payload ?: "") { body ->
                showDetailDialog(context, kind.title, body)
            }
        is WidgetKind.Icon -> IconPlaceholderView(context, kind.name, kind.color, dark)
        is WidgetKind.Chevron -> ChevronView(context, kind.expanded)
        is WidgetKind.ToolRail ->
            ToolRailView(
                context,
                kind.trunkX,
                kind.bend,
                kind.branchEnd,
                kind.rowMid,
                kind.tops,
                kind.heights,
                dark,
            )
        is WidgetKind.ToolToggle ->
            ToolToggleView(context, kind.open) {
                detailToggle(kind.detail, !kind.open)
            }
        WidgetKind.Shimmer -> ShimmerOverlayView(context)
        is WidgetKind.Visual -> VisualPlaceholderView(context, kind.id)
    }
}

private fun showDetailDialog(
    context: Context,
    title: String,
    body: String,
) {
    val activity = context.findActivity() ?: return
    AlertDialog.Builder(activity)
        .setTitle(title)
        .setMessage(body)
        .setPositiveButton("Copy") { _, _ ->
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(title, body))
        }
        .setNegativeButton("Close", null)
        .show()
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private class CopyCodeButton(
    context: Context,
    private val code: String,
) : ImageButton(context) {
    init {
        setImageResource(android.R.drawable.ic_menu_save)
        contentDescription = "Copy code"
        setOnClickListener {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code", code))
        }
    }
}

private class DisclosureTapView(
    context: Context,
    expanded: Boolean,
    onTap: () -> Unit,
) : View(context) {
    init {
        contentDescription = if (expanded) "Collapse" else "Expand"
        setOnClickListener { onTap() }
        isClickable = true
    }
}

private class ToolStatusView(
    context: Context,
    running: Boolean,
    failed: Boolean,
) : FrameLayout(context) {
    init {
        if (running) {
            addView(ProgressBar(context).apply { isIndeterminate = true })
        } else {
            val tv =
                TextView(context).apply {
                    text = if (failed) "✕" else "✓"
                    gravity = Gravity.CENTER
                }
            addView(tv)
        }
    }
}

private class ImagePlaceholderView(
    context: Context,
    reference: String,
) : TextView(context) {
    init {
        text = "Image"
        contentDescription = reference
        gravity = Gravity.CENTER
    }
}

private class WorkingLabelView(
    context: Context,
    sinceMs: Long?,
    streaming: Boolean,
) : TextView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val since = sinceMs
    private val isStreaming = streaming

    private val tick =
        object : Runnable {
            override fun run() {
                updateLabel()
                handler.postDelayed(this, 1000L)
            }
        }

    init {
        updateLabel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun updateLabel() {
        val base = if (isStreaming) "Streaming" else "Working"
        text =
            if (since != null && since > 0L) {
                val elapsed = ((System.currentTimeMillis() - since) / 1000).coerceAtLeast(0)
                "$base… ${elapsed}s"
            } else {
                "$base…"
            }
        contentDescription = text
    }
}

private class DetailTapView(
    context: Context,
    title: String,
    private val body: String,
    private val onShow: (String) -> Unit,
) : View(context) {
    init {
        contentDescription = "$title details"
        isClickable = true
        setOnClickListener { onShow(body) }
    }
}

private class IconPlaceholderView(
    context: Context,
    name: String,
    color: ColorRole,
    dark: Boolean,
) : ImageView(context) {
    init {
        setImageResource(android.R.drawable.ic_menu_info_details)
        setColorFilter(Palette.color(color, dark))
        contentDescription = name
    }
}

private class ChevronView(
    context: Context,
    expanded: Boolean,
) : ImageView(context) {
    init {
        setImageResource(android.R.drawable.arrow_down_float)
        rotation = if (expanded) 0f else -90f
        contentDescription = if (expanded) "Expanded" else "Collapsed"
    }
}

private class ToolToggleView(
    context: Context,
    open: Boolean,
    onTap: () -> Unit,
) : View(context) {
    init {
        contentDescription = if (open) "Hide details" else "Show details"
        setOnClickListener { onTap() }
        isClickable = true
    }
}

private class VisualPlaceholderView(
    context: Context,
    id: String,
) : TextView(context) {
    init {
        text = "Agent visual"
        contentDescription = "Visual $id (not available on Android in this milestone)"
        gravity = Gravity.CENTER
    }
}

private class ToolRailView(
    context: Context,
    private val trunkX: Float,
    private val bend: Float,
    private val branchEnd: Float,
    private val rowMid: Float,
    private val tops: List<Float>,
    private val heights: List<Float>,
    dark: Boolean,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.color(ColorRole.TOOL_RAIL, dark)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
    private val path = Path()

    init {
        isClickable = false
        for (i in tops.indices) {
            val top = tops[i]
            val mid = top + rowMid
            path.moveTo(trunkX, top)
            path.lineTo(trunkX, mid - bend)
            path.quadTo(trunkX, mid, trunkX + bend, mid)
            path.lineTo(branchEnd, mid)
            if (i + 1 < tops.size) {
                path.moveTo(trunkX, mid - bend)
                path.lineTo(trunkX, top + heights[i])
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(density, density)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

private class ShimmerOverlayView(
    context: Context,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1200L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        phase = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        paint.alpha = (40 + 60 * phase).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
