package sh.zeron.zerona.transcript

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import kotlin.math.ceil
import uniffi.zeron_core.LayoutFrame
import uniffi.zeron_core.RowPlacement
import uniffi.zeron_core.TranscriptView

class TranscriptListView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : NestedScrollView(context, attrs) {
        companion object {
            private const val MAX_ROW_POOL_SIZE = 32
        }

        private val content = FrameLayout(context)
        private val fonts = StyleFonts()
        private var engine: TranscriptView? = null
        private var actions: TranscriptActions? = null
        private var current: LayoutFrame? = null
        private var lastRevision: ULong? = null
        private val visible = mutableMapOf<ULong, RowView>()
        private val pool = ArrayDeque<RowView>()
        private val cache = mutableMapOf<ModelKey, RowDrawModel>()
        private val cacheOrder = ArrayDeque<ModelKey>()
        private var destroyed = false
        private var renderEpoch = 0

        /** Viewport width in logical dp (matches Rust layout units). */
        var layoutWidthDp: Float = 0f
        var darkTheme: Boolean = false
        private var lastDarkTheme: Boolean = false
        var overscanDp: Float = 700f
        private var following = true
        private var programmaticScroll = false
        private val density: Float = resources.displayMetrics.density

        init {
            isFillViewport = true
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            setOnScrollChangeListener { _, _, _, _, _ ->
                if (!programmaticScroll) {
                    val threshold = 70f * density
                    following =
                        TranscriptAnchor.shouldFollowAfterUserScroll(
                            scrollY,
                            computeMaxScroll(),
                            threshold,
                        )
                }
                layoutRows()
            }
        }

        fun bind(
            transcriptView: TranscriptView?,
            transcriptActions: TranscriptActions?,
        ) {
            engine = transcriptView
            actions = transcriptActions
        }

        fun unbind() {
            engine = null
            actions = null
            closeCurrentFrame()
            val keys = visible.keys.toList()
            for (key in keys) {
                val view = visible.remove(key) ?: continue
                recycleRowView(view)
            }
            content.removeAllViews()
            visible.clear()
            pool.forEach { it.resetForPool() }
            pool.clear()
            cache.clear()
            cacheOrder.clear()
            lastRevision = null
        }

        fun shutdown() {
            if (destroyed) return
            destroyed = true
            unbind()
        }

        fun applyLatestFrame() {
            if (destroyed) return
            val tv = engine ?: return
            val frame = tv.frame()
            val revision = frame.revision()
            val themeChanged = darkTheme != lastDarkTheme
            val stylesChanged = fonts.wouldChange(frame.styles())

            if (revision == lastRevision && !themeChanged && !stylesChanged) {
                runCatching { frame.close() }
                return
            }

            val old = current
            val first = old == null || old.rowCount() == 0u

            if (revision == lastRevision && (themeChanged || stylesChanged)) {
                val styles = frame.styles()
                runCatching { frame.close() }
                if (stylesChanged) {
                    fonts.update(styles)
                }
                if (themeChanged) {
                    lastDarkTheme = darkTheme
                }
                invalidateDrawCache()
                layoutRows()
                return
            }

            var anchorKey: ULong? = null
            var anchorDelta = 0f
            if (!following && old != null) {
                val y = scrollY / density
                val idx = old.indexAt(y)
                if (idx != null) {
                    val p = old.placement(idx)
                    if (p != null) {
                        anchorKey = p.key
                        anchorDelta = y - p.y
                    }
                }
            }
            if (old != null) {
                runCatching { old.close() }
            }
            if (fonts.update(frame.styles())) {
                invalidateDrawCache()
            }
            if (themeChanged) {
                lastDarkTheme = darkTheme
                invalidateDrawCache()
            }
            current = frame
            lastRevision = revision
            val heightPx = (frame.totalHeight() * density).toInt().coerceAtLeast(1)
            content.layoutParams.height = heightPx
            content.requestLayout()
            if (first) {
                post { scrollToBottom(false) }
            } else if (following) {
                post { scrollToBottom(false) }
            } else if (anchorKey != null) {
                val idx = frame.indexOf(anchorKey)
                if (idx != null) {
                    val p = frame.placement(idx)
                    if (p != null) {
                        val yPx = TranscriptAnchor.anchoredScrollY(p.y, anchorDelta) * density
                        programmaticScroll = true
                        scrollTo(0, yPx.toInt())
                        programmaticScroll = false
                    }
                }
            }
            layoutRows()
        }

        private fun invalidateDrawCache() {
            renderEpoch++
            cache.clear()
            cacheOrder.clear()
        }

        private fun scrollToBottom(animated: Boolean) {
            val max = computeMaxScroll()
            programmaticScroll = true
            if (animated) {
                smoothScrollTo(0, max)
            } else {
                scrollTo(0, max)
            }
            following = true
            post { programmaticScroll = false }
        }

        private fun computeMaxScroll(): Int {
            val child = getChildAt(0) ?: return 0
            return (child.height - height).coerceAtLeast(0)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (h != oldh || w != oldw) {
                layoutRows()
            }
        }

        private fun layoutRows() {
            val frame = current ?: return
            if (layoutWidthDp <= 0f) return
            val viewHeightDp = if (height > 0) height / density else overscanDp
            val y0 = scrollY / density - overscanDp
            val y1 = scrollY / density + viewHeightDp + overscanDp
            val placements = frame.rowsIn(y0, y1)
            val seen = mutableSetOf<ULong>()
            val widthDp = frame.width()
            val epoch = renderEpoch
            for (p in placements) {
                seen.add(p.key)
                val row =
                    visible[p.key] ?: run {
                        val v = pool.removeLastOrNull() ?: RowView(context)
                        v.actions = actions
                        v.dark = darkTheme
                        v.layoutDensity = density
                        visible[p.key] = v
                        val wPx = ceil(widthDp * density).toInt()
                        val hPx = ceil(p.height * density).toInt().coerceAtLeast(1)
                        content.addView(v, FrameLayout.LayoutParams(wPx, hPx))
                        v
                    }
                row.actions = actions
                row.dark = darkTheme
                row.layoutDensity = density
                row.visibility = View.VISIBLE
                val stale =
                    row.model == null ||
                        row.renderEpoch != epoch ||
                        row.version != p.version ||
                        row.model?.display?.width != widthDp ||
                        row.rowKey != p.key
                val previousText = if (stale && row.rowKey == p.key) row.model?.display?.text else null
                if (stale) {
                    val model = modelFor(p, frame)
                    row.layoutParams =
                        FrameLayout.LayoutParams(
                            ceil(widthDp * density).toInt(),
                            ceil(p.height * density).toInt().coerceAtLeast(1),
                        ).apply {
                            topMargin = ceil(p.y * density).toInt()
                        }
                    row.configure(model, p.kind, previousText, epoch)
                } else {
                    row.layoutParams =
                        (row.layoutParams as FrameLayout.LayoutParams).apply {
                            topMargin = ceil(p.y * density).toInt()
                            height = ceil(p.height * density).toInt().coerceAtLeast(1)
                        }
                }
            }
            val toRemove = visible.keys.filter { !seen.contains(it) }
            for (key in toRemove) {
                val view = visible.remove(key) ?: continue
                recycleRowView(view)
            }
        }

        private fun recycleRowView(view: RowView) {
            view.resetForPool()
            content.removeView(view)
            trimRowPool()
            if (pool.size < MAX_ROW_POOL_SIZE) {
                pool.addLast(view)
            }
        }

        private fun trimRowPool() {
            while (pool.size > MAX_ROW_POOL_SIZE) {
                pool.removeFirst()
            }
        }

        private fun modelFor(
            p: RowPlacement,
            frame: LayoutFrame,
        ): RowDrawModel {
            val k = ModelKey(p.key, p.version, frame.width())
            cache[k]?.let { return it }
            val display = frame.display(p.index)!!
            val model = RowDrawModel(display, fonts, darkTheme)
            store(model, k)
            return model
        }

        private fun store(
            model: RowDrawModel,
            key: ModelKey,
        ) {
            if (!cache.containsKey(key)) cacheOrder.addLast(key)
            cache[key] = model
            while (cacheOrder.size > 500) {
                repeat(100) {
                    val old = cacheOrder.removeFirst()
                    cache.remove(old)
                }
            }
        }

        private fun closeCurrentFrame() {
            val old = current
            current = null
            lastRevision = null
            if (old != null) {
                runCatching { old.close() }
            }
        }
    }
