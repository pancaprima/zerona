package sh.zeron.zerona.transcript

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import kotlin.math.ceil
import uniffi.zeron_core.AssistantMarkdownStyle
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
            private const val SCROLL_TO_LATEST_THRESHOLD_DP = 240f
            private const val JUMP_SETTLE_WINDOW_MS = 1500L
            private const val TAIL_SETTLE_EXTEND_MS = 600L
            private const val TAIL_SETTLE_MAX_MS = 5000L
            private const val JUMP_SETTLE_EPSILON_PX = 2
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
        private var bindGeneration = 0
        private var renderEpoch = 0

        /** Viewport width in logical dp (matches Rust layout units). */
        var layoutWidthDp: Float = 0f
        var darkTheme: Boolean = false
        private var lastDarkTheme: Boolean = false
        var overscanDp: Float = 700f
        private var following = true
        private var programmaticScroll = false
        private var programmaticScrollGeneration = 0
        private val density: Float = resources.displayMetrics.density
        private val scrollToLatestThresholdPx: Float = SCROLL_TO_LATEST_THRESHOLD_DP * density
        private var scrollToLatestVisible = false
        private var readyForScrollToLatestUi = false
        private var pendingJumpAfterLayout = false
        private var jumpRowSyncInProgress = false
        private var jumpRowSyncGeneration = 0
        private var pendingAutoTailAfterLayout = false
        private var pendingAutoTailFromEmpty = false
        private var pendingMarkReadyAfterAutoTail = false
        private var replayScrollToLatestGeneration = 0
        private var jumpSettleDeadlineMs = 0L
        private var jumpSettleArmedAtMs = 0L
        var onScrollToLatestVisibilityChanged: ((Boolean) -> Unit)? = null

        private val scrollToLatestEvalRunnable =
            Runnable {
                if (destroyed) return@Runnable
                evaluateScrollToLatestVisibility()
            }

        init {
            isFillViewport = true
            clipChildren = true
            clipToPadding = true
            content.clipChildren = true
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            setOnScrollChangeListener { _, _, _, _, _ ->
                if (!programmaticScroll) {
                    val threshold = 70f * density
                    val wasFollowing = following
                    following =
                        TranscriptAnchor.shouldFollowAfterUserScroll(
                            scrollY,
                            computeMaxScroll(),
                            threshold,
                        )
                    if (wasFollowing && !following) {
                        Log.d(
                            "ZScroll",
                            "follow=false scrollY=$scrollY max=${computeMaxScroll()}",
                        )
                    }
                }
                scheduleScrollToLatestEvaluation()
                layoutRows()
            }
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                clearJumpSettle()
                cancelPendingJump()
                cancelPendingAutoTail()
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun dispatchDraw(canvas: Canvas) {
            val save = canvas.save()
            val clip = ImageAttachmentSupport.scrollViewportClipRect(scrollX, scrollY, width, height)
            canvas.clipRect(clip.left, clip.top, clip.right, clip.bottom)
            super.dispatchDraw(canvas)
            canvas.restoreToCount(save)
        }

        fun reloadImages() {
            for (row in visible.values) {
                row.reloadImageWidgets()
            }
        }

        /**
         * Lazily resolves whole-message source for a row key against the current layout frame.
         * Must run on the UI thread; does not retain [LayoutFrame] beyond the call.
         */
        fun wholeMessageTextForRow(rowKey: ULong): String? {
            if (destroyed) return null
            val frame = current ?: return null
            val idx =
                runCatching { frame.indexOf(rowKey) }.getOrNull()
                    ?: return null
            return runCatching { frame.messageText(idx) }.getOrNull()
        }

        fun bind(
            transcriptView: TranscriptView?,
            transcriptActions: TranscriptActions?,
        ) {
            clearJumpSettle()
            engine = transcriptView
            transcriptView?.setAssistantMarkdownStyle(AssistantMarkdownStyle.STUDY01_PROSE_CARD)
            actions = transcriptActions
        }

        fun unbind() {
            bindGeneration++
            replayScrollToLatestGeneration++
            removeCallbacks(scrollToLatestEvalRunnable)
            clearJumpSettle()
            cancelPendingJump()
            cancelPendingAutoTail()
            readyForScrollToLatestUi = false
            scrollToLatestVisible = false
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
            removeCallbacks(scrollToLatestEvalRunnable)
            onScrollToLatestVisibilityChanged = null
            cancelPendingJump()
            unbind()
        }

        fun replayScrollToLatestVisibility() {
            val bindGen = bindGeneration
            val replayGen = ++replayScrollToLatestGeneration
            post {
                if (destroyed || bindGen != bindGeneration || replayGen != replayScrollToLatestGeneration) {
                    return@post
                }
                onScrollToLatestVisibilityChanged?.invoke(scrollToLatestVisible)
            }
        }

        fun jumpToLatest() {
            if (destroyed) return
            Log.d("ZScroll", "jump")
            cancelPendingAutoTail()
            jumpRowSyncInProgress = true
            pendingJumpAfterLayout = true
            armJumpSettle()
            requestLayout()
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
                scheduleScrollToLatestEvaluation()
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
            val totalDp = frame.totalHeight()
            val totalPx = (totalDp * density).toInt()
            val lastRowBottomPx = lastRowBottomPx(frame)
            val finalHeightPx =
                TranscriptAnchor.contentHeightPx(totalPx, lastRowBottomPx)
            content.layoutParams.height = finalHeightPx
            content.minimumHeight = finalHeightPx
            content.requestLayout()
            if (first) {
                armJumpSettle()
                scheduleAutoTailAfterLayout(markReady = true, fromEmpty = true)
            } else {
                extendJumpSettleIfActive()
                if (following || jumpRowSyncInProgress) {
                    scheduleAutoTailAfterLayout(markReady = !jumpRowSyncInProgress)
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
            }
            layoutRows()
            scheduleScrollToLatestEvaluation()
            maybeResettleJumpBottom()
            Log.d(
                "ZScroll",
                "applyFrame rev=$revision rowCount=${frame.rowCount()} totalDp=$totalDp " +
                    "totalPx=$totalPx lastRowBottomPx=$lastRowBottomPx finalHeightPx=$finalHeightPx " +
                    "following=$following scrollY=$scrollY first=$first settleDeadline=$jumpSettleDeadlineMs",
            )
        }

        private fun lastRowBottomPx(frame: LayoutFrame): Int {
            val rowCount = frame.rowCount()
            if (rowCount == 0u) return 0
            val p = frame.placement(rowCount - 1u) ?: return 0
            return ceil((p.y + p.height) * density).toInt()
        }

        private fun invalidateDrawCache() {
            renderEpoch++
            cache.clear()
            cacheOrder.clear()
        }

        private fun scrollToBottom(animated: Boolean) {
            val max = computeMaxScroll()
            val gen = ++programmaticScrollGeneration
            programmaticScroll = true
            if (animated) {
                smoothScrollTo(0, max)
            } else {
                scrollTo(0, max)
            }
            following = true
            val bindGen = bindGeneration
            post {
                if (destroyed || bindGen != bindGeneration) return@post
                if (gen == programmaticScrollGeneration) {
                    programmaticScroll = false
                }
                scheduleScrollToLatestEvaluation()
            }
        }

        private fun markReadyForScrollToLatestUi() {
            readyForScrollToLatestUi = true
            evaluateScrollToLatestVisibility()
        }

        private fun scheduleScrollToLatestEvaluation() {
            removeCallbacks(scrollToLatestEvalRunnable)
            post(scrollToLatestEvalRunnable)
        }

        private fun evaluateScrollToLatestVisibility() {
            if (!readyForScrollToLatestUi) {
                emitScrollToLatestVisibility(false)
                return
            }
            if (jumpRowSyncInProgress) {
                emitScrollToLatestVisibility(true)
                return
            }
            val show =
                TranscriptAnchor.shouldShowScrollToLatest(
                    scrollY,
                    computeMaxScroll(),
                    scrollToLatestThresholdPx,
                )
            emitScrollToLatestVisibility(show)
        }

        private fun emitScrollToLatestVisibility(show: Boolean) {
            if (scrollToLatestVisible == show) return
            scrollToLatestVisible = show
            val listener = onScrollToLatestVisibilityChanged
            if (listener == null) return
            val bindGen = bindGeneration
            post {
                if (destroyed || bindGen != bindGeneration) return@post
                onScrollToLatestVisibilityChanged?.invoke(scrollToLatestVisible)
            }
        }

        private fun clearJumpSettle() {
            jumpSettleDeadlineMs = 0L
            jumpSettleArmedAtMs = 0L
        }

        private fun armJumpSettle() {
            val now = SystemClock.uptimeMillis()
            jumpSettleArmedAtMs = now
            jumpSettleDeadlineMs = now + JUMP_SETTLE_WINDOW_MS
        }

        private fun extendJumpSettleIfActive() {
            if (jumpSettleDeadlineMs == 0L) return
            val now = SystemClock.uptimeMillis()
            jumpSettleDeadlineMs =
                TranscriptAnchor.extendSettleDeadline(
                    now,
                    jumpSettleArmedAtMs,
                    jumpSettleDeadlineMs,
                    TAIL_SETTLE_EXTEND_MS,
                    TAIL_SETTLE_MAX_MS,
                )
        }

        private fun cancelPendingJump() {
            pendingJumpAfterLayout = false
            jumpRowSyncInProgress = false
            jumpRowSyncGeneration++
        }

        private fun cancelPendingAutoTail() {
            pendingAutoTailAfterLayout = false
            pendingAutoTailFromEmpty = false
            pendingMarkReadyAfterAutoTail = false
        }

        private fun scheduleAutoTailAfterLayout(
            markReady: Boolean,
            fromEmpty: Boolean = false,
        ) {
            pendingAutoTailAfterLayout = true
            pendingAutoTailFromEmpty = fromEmpty
            if (markReady) {
                pendingMarkReadyAfterAutoTail = true
            }
            requestLayout()
        }

        private fun drainPendingScrollsAfterLayout() {
            if (destroyed || current == null) {
                pendingJumpAfterLayout = false
                cancelPendingAutoTail()
                return
            }
            if (pendingJumpAfterLayout) {
                pendingJumpAfterLayout = false
                pendingAutoTailAfterLayout = false
                pendingAutoTailFromEmpty = false
                pendingMarkReadyAfterAutoTail = false
                val child = getChildAt(0)
                Log.d(
                    "ZScroll",
                    "drain max=${computeMaxScroll()} scrollY=$scrollY child.height=${child?.height} " +
                        "content.layoutParams.height=${content.layoutParams.height}",
                )
                programmaticScrollToMax()
                scheduleJumpRowSyncAfterScroll()
                return
            }
            if (pendingAutoTailAfterLayout) {
                pendingAutoTailAfterLayout = false
                val markReady = pendingMarkReadyAfterAutoTail
                pendingMarkReadyAfterAutoTail = false
                val fromEmpty = pendingAutoTailFromEmpty
                pendingAutoTailFromEmpty = false
                if (following || fromEmpty) {
                    scrollToBottom(false)
                }
                if (markReady) {
                    markReadyForScrollToLatestUi()
                }
            }
            maybeResettleJumpBottom()
        }

        private fun programmaticScrollToMax() {
            val max = computeMaxScroll()
            val gen = ++programmaticScrollGeneration
            val bindGen = bindGeneration
            programmaticScroll = true
            scrollTo(0, max)
            if (!jumpRowSyncInProgress) {
                following = true
            }
            Log.d("ZScroll", "scrollToMax target=$max scrollYAfter=$scrollY")
            post {
                if (destroyed || bindGen != bindGeneration) return@post
                if (gen == programmaticScrollGeneration) {
                    programmaticScroll = false
                }
                scheduleScrollToLatestEvaluation()
            }
        }

        private fun scheduleJumpRowSyncAfterScroll() {
            val gen = ++jumpRowSyncGeneration
            val bindGen = bindGeneration
            post {
                if (destroyed || bindGen != bindGeneration || gen != jumpRowSyncGeneration) {
                    return@post
                }
                runJumpRowSyncPass(gen, bindGen, pass = 1) {
                    postOnAnimation {
                        if (destroyed || bindGen != bindGeneration || gen != jumpRowSyncGeneration) {
                            return@postOnAnimation
                        }
                        runJumpRowSyncPass(gen, bindGen, pass = 2) {
                            finishJumpRowSync(gen, bindGen, forced = true)
                        }
                    }
                }
            }
        }

        private fun runJumpRowSyncPass(
            gen: Int,
            bindGen: Int,
            pass: Int,
            onIncomplete: () -> Unit,
        ) {
            if (destroyed || bindGen != bindGeneration || gen != jumpRowSyncGeneration) return
            val max = computeMaxScroll()
            programmaticScroll = true
            scrollTo(0, max)
            programmaticScroll = false
            layoutRows()
            logLayoutRowsSnapshot("jumpSync pass=$pass")
            if (isTailViewportSatisfied()) {
                finishJumpRowSync(gen, bindGen, forced = false)
            } else {
                onIncomplete()
            }
        }

        private fun finishJumpRowSync(
            gen: Int,
            bindGen: Int,
            forced: Boolean,
        ) {
            if (destroyed || bindGen != bindGeneration || gen != jumpRowSyncGeneration) return
            val max = computeMaxScroll()
            programmaticScroll = true
            scrollTo(0, max)
            programmaticScroll = false
            layoutRows()
            val tailOk = isTailViewportSatisfied()
            jumpRowSyncInProgress = false
            following = tailOk
            if (tailOk || forced) {
                markReadyForScrollToLatestUi()
            }
            val bounds = visibleRowVerticalBoundsPx()
            val window = TranscriptAnchor.layoutWindowDp(scrollY, density, height, overscanDp)
            Log.d(
                "ZScroll",
                "jumpDone forced=$forced scrollY=$scrollY max=$max laid=${visible.size} " +
                    "window=${window.first.toInt()}..${window.second.toInt()}dp " +
                    "rowsPx=${bounds?.first}..${bounds?.last} tailOk=$tailOk",
            )
            if (!tailOk) {
                emitScrollToLatestVisibility(true)
            }
            scheduleScrollToLatestEvaluation()
            maybeResettleJumpBottom()
        }

        private fun isTailViewportSatisfied(): Boolean {
            val max = computeMaxScroll()
            if (
                TranscriptAnchor.shouldResettleToBottom(
                    scrollY,
                    max,
                    JUMP_SETTLE_EPSILON_PX,
                )
            ) {
                return false
            }
            val bounds = visibleRowVerticalBoundsPx() ?: return false
            val vpBottom = scrollY + height
            return TranscriptAnchor.viewportIntersectsRowBand(
                scrollY,
                vpBottom,
                bounds.first,
                bounds.last,
            )
        }

        private fun visibleRowVerticalBoundsPx(): IntRange? {
            if (visible.isEmpty()) return null
            var minTop = Int.MAX_VALUE
            var maxBottom = Int.MIN_VALUE
            for (view in visible.values) {
                val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
                val top = lp.topMargin
                val bottom = top + lp.height
                if (top < minTop) minTop = top
                if (bottom > maxBottom) maxBottom = bottom
            }
            if (minTop == Int.MAX_VALUE) return null
            return minTop..maxBottom
        }

        private fun logLayoutRowsSnapshot(reason: String) {
            val frame = current ?: return
            val window = TranscriptAnchor.layoutWindowDp(scrollY, density, height, overscanDp)
            val placementCount = frame.rowsIn(window.first, window.second).size
            val bounds = visibleRowVerticalBoundsPx()
            Log.d(
                "ZScroll",
                "$reason scrollY=$scrollY max=${computeMaxScroll()} placements=$placementCount " +
                    "attached=${visible.size} window=${window.first.toInt()}..${window.second.toInt()}dp " +
                    "rowsPx=${bounds?.first}..${bounds?.last}",
            )
        }

        private fun maybeResettleJumpBottom() {
            if (destroyed) return
            if (!following) return
            val now = SystemClock.uptimeMillis()
            if (!TranscriptAnchor.settleActive(now, jumpSettleDeadlineMs)) {
                if (jumpSettleDeadlineMs != 0L) {
                    clearJumpSettle()
                }
                return
            }
            if (
                !TranscriptAnchor.shouldResettleToBottom(
                    scrollY,
                    computeMaxScroll(),
                    JUMP_SETTLE_EPSILON_PX,
                )
            ) {
                return
            }
            val child = getChildAt(0)
            Log.d(
                "ZScroll",
                "resettle max=${computeMaxScroll()} scrollY=$scrollY child.height=${child?.height} " +
                    "content.layoutParams.height=${content.layoutParams.height}",
            )
            programmaticScrollToMax()
            layoutRows()
            logLayoutRowsSnapshot("resettle")
            requestLayout()
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
                scheduleScrollToLatestEvaluation()
            }
        }

        override fun onLayout(
            changed: Boolean,
            l: Int,
            t: Int,
            r: Int,
            b: Int,
        ) {
            super.onLayout(changed, l, t, r, b)
            if (content.height != content.layoutParams.height) {
                Log.d(
                    "ZScroll",
                    "layoutMismatch measured=${content.height} params=${content.layoutParams.height}",
                )
            }
            drainPendingScrollsAfterLayout()
            if (
                current != null &&
                    !pendingAutoTailAfterLayout &&
                    !pendingJumpAfterLayout &&
                    !jumpRowSyncInProgress
            ) {
                readyForScrollToLatestUi = true
            }
            scheduleScrollToLatestEvaluation()
        }

        private fun layoutRows() {
            val frame = current ?: return
            if (layoutWidthDp <= 0f) return
            val (y0, y1) =
                TranscriptAnchor.layoutWindowDp(
                    scrollY,
                    density,
                    height,
                    overscanDp,
                    overscanDp,
                )
            val placements = frame.rowsIn(y0, y1)
            var maxPlacementBottomPx = 0
            for (p in placements) {
                val bottomPx = ceil((p.y + p.height) * density).toInt()
                if (bottomPx > maxPlacementBottomPx) {
                    maxPlacementBottomPx = bottomPx
                }
            }
            val currentContentHeight = content.layoutParams.height
            if (maxPlacementBottomPx > currentContentHeight) {
                Log.d(
                    "ZScroll",
                    "raiseHeight old=$currentContentHeight new=$maxPlacementBottomPx",
                )
                content.layoutParams.height = maxPlacementBottomPx
                content.minimumHeight = maxPlacementBottomPx
                content.requestLayout()
            }
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
