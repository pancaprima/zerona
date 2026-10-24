package sh.zeron.zerona.transcript

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import kotlin.math.ceil
import uniffi.zeron_core.LinkHit
import uniffi.zeron_core.RowDisplay
import uniffi.zeron_core.RowKind

class RowView(
    context: Context,
) : FrameLayout(context) {
    private data class ScrollerSlot(
        val scroll: HorizontalScrollView,
        val inner: FrameLayout,
        val canvas: RowCanvasView,
    )

    private val canvas = RowCanvasView(context)
    private val freshCanvas = RowCanvasView(context)
    private val scrollers = mutableListOf<ScrollerSlot>()
    private val widgetViews = mutableListOf<View>()
    private val linkOverlayViews = mutableListOf<View>()
    private var linkHits: List<LinkHit> = emptyList()
    private var scrollOffsets = mutableMapOf<Int, Int>()
    private var longPressActive = false
    private var streamAnimator: ValueAnimator? = null
    private var streamAnimGeneration = 0
    private var streamRevealPhase = RowStreamMotionPolicy.StreamRevealPhase.None

    var model: RowDrawModel? = null
    var rowKey: ULong = 0u
    var version: ULong = 0u
    var kind: RowKind = RowKind.MARKDOWN
    var renderEpoch: Int = 0
    var actions: TranscriptActions? = null
    var dark: Boolean = false
    var layoutDensity: Float = 1f

    private val gesture =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    longPressActive = true
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    presentMessageActions(preferredLinkUrl = null)
                }
            },
        )

    init {
        addView(canvas)
        addView(freshCanvas)
        freshCanvas.visibility = GONE
        clipChildren = true
        clipToPadding = true
        isClickable = true
        setOnTouchListener { _, event ->
            gesture.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                if (longPressActive) {
                    longPressActive = false
                    return@setOnTouchListener true
                }
                val link = hitLink(event.x, event.y)
                if (link != null) {
                    openLink(link.url)
                    return@setOnTouchListener true
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                longPressActive = false
            }
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressActive = false
            }
            false
        }
    }

    fun configure(
        drawModel: RowDrawModel,
        rowKind: RowKind,
        previousText: String?,
        presentationEpoch: Int,
        allowEntrance: Boolean = false,
    ): Boolean {
        val d = drawModel.display
        val priorKey = rowKey
        if (priorKey != d.key) {
            scrollOffsets.clear()
            for (slot in scrollers) {
                slot.scroll.scrollTo(0, 0)
            }
        } else {
            for ((idx, slot) in scrollers.withIndex()) {
                scrollOffsets[idx] = slot.scroll.scrollX
            }
        }
        val grew =
            previousText != null &&
                d.key == priorKey &&
                d.text.startsWith(previousText) &&
                d.text.length > previousText.length
        val veilFrom = if (grew) previousText!!.length else 0

        val motion =
            RowStreamMotionPolicy.onConfigure(
                RowStreamMotionPolicy.Input(
                    priorKey = priorKey,
                    newKey = d.key,
                    grew = grew,
                    veilFrom = veilFrom,
                    allowEntrance = allowEntrance,
                    streamRevealPhase = streamRevealPhase,
                    streamAnimatorRunning = streamAnimator?.isRunning == true,
                ),
            )
        if (motion.shouldCancelStreamAnimator) {
            cancelStreamAnimation()
        }
        streamRevealPhase = motion.streamRevealPhase
        if (motion.shouldClearRowEntrance) {
            ZeronaNativeMotion.clearRowEntrance(this)
        }

        model = drawModel
        rowKey = d.key
        version = d.version
        kind = rowKind
        renderEpoch = presentationEpoch
        linkHits = d.links

        val density = layoutDensity.coerceAtLeast(1f)
        val wPx = ceil(d.width * density).toInt().coerceAtLeast(1)
        val hPx = ceil(d.height * density).toInt().coerceAtLeast(1)
        canvas.layoutParams = LayoutParams(wPx, hPx)
        freshCanvas.layoutParams = LayoutParams(wPx, hPx)
        canvas.layoutDensity = density
        freshCanvas.layoutDensity = density

        if (motion.useStreamVeil) {
            canvas.pass = RowDrawModel.Pass.SETTLED
            canvas.veilFrom = veilFrom
            freshCanvas.pass = RowDrawModel.Pass.FRESH
            freshCanvas.veilFrom = veilFrom
            freshCanvas.model = drawModel
            freshCanvas.dark = dark
            freshCanvas.visibility = VISIBLE
            if (motion.shouldStartStreamReveal) {
                freshCanvas.alpha = 0f
                val generation = ++streamAnimGeneration
                val revealMs = ZeronaNativeMotion.streamRevealDurationMs(context)
                streamAnimator =
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = revealMs
                        addUpdateListener { anim ->
                            if (generation != streamAnimGeneration) return@addUpdateListener
                            freshCanvas.alpha = anim.animatedValue as Float
                        }
                        addListener(
                            object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (generation != streamAnimGeneration) return
                                    finishStreamReveal()
                                }

                                override fun onAnimationCancel(animation: Animator) {
                                    if (generation != streamAnimGeneration) return
                                }
                            },
                        )
                        if (revealMs == 0L) {
                            freshCanvas.alpha = 1f
                            finishStreamReveal()
                        } else {
                            start()
                        }
                    }
            }
        } else {
            canvas.pass = RowDrawModel.Pass.ALL
            canvas.veilFrom = 0
            freshCanvas.visibility = GONE
        }
        if (motion.shouldPlayRowEntrance) {
            ZeronaNativeMotion.playRowEntrance(this, rowKind)
        }
        canvas.model = drawModel
        canvas.dark = dark
        canvas.layerIndex = 0
        canvas.invalidate()

        layoutScrollers(drawModel, density, streamingBody = motion.useStreamVeil)
        layoutWidgets(d, density)
        layoutLinkOverlays(d, density)
        contentDescription = rowAccessibilityLabel(d, rowKind)
        return motion.shouldPlayRowEntrance
    }

    private fun finishStreamReveal() {
        streamRevealPhase = RowStreamMotionPolicy.onStreamRevealFinished(streamRevealPhase)
        canvas.pass = RowDrawModel.Pass.ALL
        canvas.veilFrom = 0
        canvas.invalidate()
        freshCanvas.visibility = GONE
        freshCanvas.alpha = 1f
        for (slot in scrollers) {
            slot.canvas.pass = RowDrawModel.Pass.ALL
            slot.canvas.veilFrom = 0
            slot.canvas.invalidate()
        }
        streamAnimator = null
    }

    private fun cancelStreamAnimation() {
        streamAnimGeneration++
        streamAnimator?.removeAllListeners()
        streamAnimator?.removeAllUpdateListeners()
        streamAnimator?.cancel()
        streamAnimator = null
    }

    private fun rowAccessibilityLabel(
        d: RowDisplay,
        rowKind: RowKind,
    ): String {
        val body = d.copyText.ifEmpty { d.text }
        return when (rowKind) {
            RowKind.USER -> "You: $body"
            else -> body
        }
    }

    private fun layoutScrollers(
        drawModel: RowDrawModel,
        density: Float,
        streamingBody: Boolean,
    ) {
        val d = drawModel.display
        while (scrollers.size < d.scrollers.size) {
            val inner = FrameLayout(context).apply { clipChildren = true }
            val innerCanvas = RowCanvasView(context)
            inner.addView(innerCanvas, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            val scroll =
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = OVER_SCROLL_NEVER
                    clipChildren = true
                    addView(inner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
                }
            addView(scroll, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            scrollers.add(ScrollerSlot(scroll, inner, innerCanvas))
        }
        val scrollerPass =
            if (streamingBody) {
                RowDrawModel.Pass.ALL
            } else {
                canvas.pass
            }
        for (i in scrollers.indices) {
            val slot = scrollers[i]
            if (i >= d.scrollers.size) {
                slot.scroll.visibility = GONE
                continue
            }
            val info = d.scrollers[i]
            slot.scroll.visibility = VISIBLE
            val wPx = ceil(info.w * density).toInt().coerceAtLeast(1)
            val hPx = ceil(info.h * density).toInt().coerceAtLeast(1)
            slot.scroll.layoutParams =
                LayoutParams(wPx, hPx).apply {
                    leftMargin = ceil(info.x * density).toInt()
                    topMargin = ceil(info.y * density).toInt()
                }
            val contentW = ceil(info.contentWidth * density).toInt().coerceAtLeast(wPx)
            slot.inner.layoutParams =
                LayoutParams(contentW, hPx)
            slot.canvas.layoutParams = LayoutParams(contentW, hPx)
            slot.canvas.layerIndex = i + 1
            slot.canvas.model = drawModel
            slot.canvas.dark = dark
            slot.canvas.layoutDensity = density
            slot.canvas.pass = scrollerPass
            slot.canvas.veilFrom = if (streamingBody) 0 else canvas.veilFrom
            slot.canvas.invalidate()
            val saved = scrollOffsets[i]
            if (saved != null) {
                slot.scroll.post {
                    if (slot.scroll.scrollX != saved) {
                        slot.scroll.scrollX = saved
                    }
                }
            }
            slot.scroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
                scrollOffsets[i] = scrollX
            }
        }
    }

    private fun layoutWidgets(
        d: RowDisplay,
        density: Float,
    ) {
        clearHostedViews(widgetViews)
        val act = actions ?: return
        for (w in d.widgets) {
            val host = widgetHost(w.scroller?.toInt()) ?: this
            val view =
                buildWidgetView(
                    context,
                    w.kind,
                    w.payload,
                    d.key,
                    dark,
                    act,
                ) { detail, open ->
                    act.toggleDetail(d.key, detail, open)
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            view.layoutParams =
                LayoutParams(
                    ceil(w.w * density).toInt().coerceAtLeast(1),
                    ceil(w.h * density).toInt().coerceAtLeast(1),
                ).apply {
                    leftMargin = ceil(w.x * density).toInt()
                    topMargin = ceil(w.y * density).toInt()
                }
            host.addView(view)
            widgetViews.add(view)
        }
    }

    private fun layoutLinkOverlays(
        d: RowDisplay,
        density: Float,
    ) {
        clearHostedViews(linkOverlayViews)
        for (link in d.links) {
            val host = widgetHost(link.scroller?.toInt()) ?: this
            val linkUrl = link.url
            val view =
                View(context).apply {
                    contentDescription = "Link, $linkUrl"
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                    isClickable = true
                    isLongClickable = true
                    setOnClickListener {
                        openLink(linkUrl)
                    }
                    setOnLongClickListener {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        presentMessageActions(preferredLinkUrl = linkUrl)
                        true
                    }
                }
            view.layoutParams =
                LayoutParams(
                    ceil(link.w * density).toInt().coerceAtLeast(1),
                    ceil(link.h * density).toInt().coerceAtLeast(1),
                ).apply {
                    leftMargin = ceil(link.x * density).toInt()
                    topMargin = ceil(link.y * density).toInt()
                }
            host.addView(view)
            linkOverlayViews.add(view)
        }
    }

    private fun widgetHost(scrollerIndex: Int?): ViewGroup? {
        if (scrollerIndex == null) return this
        if (scrollerIndex < 0 || scrollerIndex >= scrollers.size) return null
        val slot = scrollers[scrollerIndex]
        if (slot.scroll.visibility != VISIBLE) return null
        return slot.inner
    }

    private fun clearHostedViews(views: MutableList<View>) {
        for (v in views) {
            (v.parent as? ViewGroup)?.removeView(v)
        }
        views.clear()
    }

    private fun hitLink(
        x: Float,
        y: Float,
    ): LinkHit? {
        val density = layoutDensity.coerceAtLeast(1f)
        val logicalX = x / density
        val logicalY = y / density
        val frames =
            scrollers.mapIndexed { index, slot ->
                val info = model?.display?.scrollers?.getOrNull(index)
                ScrollerFrame(
                    viewportLeft = slot.scroll.left / density,
                    viewportTop = slot.scroll.top / density,
                    viewportWidth = info?.w ?: 0f,
                    viewportHeight = info?.h ?: 0f,
                    scrollX = slot.scroll.scrollX / density,
                    visible = slot.scroll.visibility == VISIBLE,
                )
            }
        return TranscriptHitTest.linkAtInRow(linkHits, logicalX, logicalY, frames)
    }

    private fun openLink(url: String) {
        val previewRef = ImageAttachmentSupport.previewImageReferenceFromUrl(url)
        if (previewRef != null) {
            actions?.showImagePreview(context, previewRef, null)
            return
        }
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return
        actions?.openUrl(context, url)
    }

    private fun presentMessageActions(preferredLinkUrl: String?) {
        val d = model?.display ?: return
        val act = actions ?: return
        val fallback = d.copyText.ifEmpty { d.text }
        val links = d.links.map { TranscriptMessageActionsSupport.linkDescriptor(it.url) }
        act.showMessageActions(
            context,
            rowKey,
            fallback,
            links,
            preferredLinkUrl,
            rowCopyText = d.copyText,
            rowDisplayText = d.text,
        )
    }

    fun reloadImageWidgets() {
        for (v in widgetViews) {
            if (v is TranscriptImageWidget) {
                actions?.imageLoader()?.cancelLoad(v)
                v.clearForRebind()
                actions?.imageLoader()?.loadInto(v)
            }
        }
    }

    fun resetForPool() {
        longPressActive = false
        ZeronaNativeMotion.clearRowEntrance(this)
        streamRevealPhase = RowStreamMotionPolicy.StreamRevealPhase.None
        cancelStreamAnimation()
        for (v in widgetViews) {
            if (v is TranscriptImageWidget) {
                actions?.imageLoader()?.cancelLoad(v)
            }
        }
        model = null
        actions = null
        visibility = GONE
        canvas.model = null
        freshCanvas.model = null
        clearHostedViews(widgetViews)
        clearHostedViews(linkOverlayViews)
        scrollOffsets.clear()
        for (slot in scrollers) {
            slot.canvas.model = null
            slot.canvas.pass = RowDrawModel.Pass.ALL
            slot.scroll.scrollTo(0, 0)
        }
    }
}
