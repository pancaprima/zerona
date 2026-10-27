package sh.zeron.zerona.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.transcript.FrameRelay
import sh.zeron.zerona.transcript.ImageAttachmentSupport
import sh.zeron.zerona.transcript.MessageActionsController
import sh.zeron.zerona.transcript.MessageLinkDescriptor
import sh.zeron.zerona.transcript.SessionImageLoader
import sh.zeron.zerona.transcript.TranscriptActions
import sh.zeron.zerona.transcript.TranscriptLab
import sh.zeron.zerona.transcript.TranscriptListView
import sh.zeron.zerona.transcript.TranscriptMessageActionsSupport
import sh.zeron.zerona.transcript.TranscriptRowKey
import sh.zeron.zerona.transcript.findActivity
import sh.zeron.zerona.transcript.isUsableForUi
import uniffi.zeron_core.BusyPolicy
import uniffi.zeron_core.CoreException
import uniffi.zeron_core.DebugEntry
import uniffi.zeron_core.SendRequest
import uniffi.zeron_core.SendState
import uniffi.zeron_core.SessionHandle
import uniffi.zeron_core.TranscriptView

private const val SESSION_VIEW_MODEL_LOG_TAG = "SessionViewModel"

data class QueueItemUi(
    val id: String,
    val text: String,
    val actionPending: Boolean,
)

data class ComposerStripState(
    val turnRunning: Boolean = false,
    val canSteer: Boolean = false,
    val deliveryMode: BusyPolicy = BusyPolicy.QUEUE,
    val banner: String? = null,
    val bannerIsError: Boolean = false,
    val sendFailure: String? = null,
    val queueError: String? = null,
    val queue: List<QueueItemUi> = emptyList(),
    val sendInFlight: Boolean = false,
)

data class SessionUiState(
    val title: String = "",
    val modelLabel: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val draftText: String = "",
    val composer: ComposerStripState = ComposerStripState(),
    val viewportWidthDp: Float = 0f,
    val textScale: Float = 1f,
    val frameTick: Long = 0L,
    val showScrollToLatest: Boolean = false,
)

class SessionViewModel(
    private val appModel: AppModel,
    private val chatId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var handle: SessionHandle? = null
    private var transcriptView: TranscriptView? = null
    private var frameRelay: FrameRelay? = null
    private var transcriptHost: TranscriptListView? = null
    private var sendInFlight = false
    private var localQueueError: String? = null
    private val isLab = chatId == TranscriptLab.CHAT_ID
    private val labEntries = mutableListOf<DebugEntry>()
    private var labStreamJob: Job? = null
    private var modelLabelRefreshJob: Job? = null
    @Volatile
    private var engineLive = false

    private val sessionImageLoader = SessionImageLoader(viewModelScope)
    private var messageActionsController: MessageActionsController? = null

    private val transcriptActions =
        object : TranscriptActions {
            override fun toggleRow(key: ULong) {
                transcriptView?.toggle(key)
            }

            override fun toggleDetail(
                row: ULong,
                detail: ULong,
                open: Boolean,
            ) {
                transcriptView?.toggleDetail(row, detail, open)
            }

            override fun imageLoader(): SessionImageLoader = sessionImageLoader

            override fun showImagePreview(
                context: Context,
                reference: String,
                thumbnail: Bitmap?,
            ) {
                if (!ImageAttachmentSupport.isAllowedAttachmentReference(reference, allowLabFixture = isLab)) {
                    return
                }
                sessionImageLoader.showPreview(context, reference, thumbnail)
            }

            override fun showMessageActions(
                context: Context,
                rowKey: ULong,
                fallbackRowText: String,
                links: List<MessageLinkDescriptor>,
                preferredLink: String?,
                rowCopyText: String,
                rowDisplayText: String,
            ) {
                val activity = context.findActivity()
                if (activity == null || !activity.isUsableForUi()) return
                val host = transcriptHost
                val whole =
                    if (host != null && transcriptHost === host) {
                        host.wholeMessageTextForRow(rowKey)
                    } else {
                        null
                    }
                val menuState =
                    TranscriptMessageActionsSupport.buildMenuState(
                        fallbackRowText = fallbackRowText,
                        rowCopyText = rowCopyText,
                        rowDisplayText = rowDisplayText,
                        wholeMessageText = whole,
                        links = links,
                        preferredLinkUrl = preferredLink,
                    )
                if (!menuState.copyMessageEnabled &&
                    !menuState.selectTextEnabled &&
                    !menuState.copyLinkEnabled
                ) {
                    return
                }
                messageActionsController?.close()
                val controller =
                    MessageActionsController(
                        activity = activity,
                        menuState = menuState,
                        onClosed = { closed ->
                            if (messageActionsController === closed) {
                                messageActionsController = null
                            }
                        },
                    )
                messageActionsController = controller
                controller.show()
            }

            override fun openUrl(
                context: Context,
                url: String,
            ) {
                val previewRef = ImageAttachmentSupport.previewImageReferenceFromUrl(url)
                if (previewRef != null) {
                    if (ImageAttachmentSupport.isAllowedAttachmentReference(previewRef, allowLabFixture = isLab)) {
                        showImagePreview(context, previewRef, null)
                    }
                    return
                }
                val uri = Uri.parse(url)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    val activity = context.findActivity()
                    if (activity != null) {
                        activity.startActivity(intent)
                    } else {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ContextCompat.startActivity(context.applicationContext, intent, null)
                    }
                } catch (_: ActivityNotFoundException) {
                } catch (_: SecurityException) {
                }
            }
        }

    init {
        viewModelScope.launch {
            openSession()
        }
    }

    private fun openSession() {
        val core = appModel.client
        val text = appModel.textSystemOrNull()
        if (core == null || text == null) {
            _state.update { it.copy(loading = false, error = "Core not ready") }
            return
        }
        try {
            val relay =
                FrameRelay {
                    if (!engineLive || transcriptView == null) return@FrameRelay
                    refreshHydration()
                    _state.update { it.copy(frameTick = it.frameTick + 1) }
                    transcriptHost?.applyLatestFrame()
                }
            frameRelay = relay
            val tv = TranscriptView(text, relay)
            transcriptView = tv
            if (isLab) {
                labEntries.clear()
                labEntries.addAll(TranscriptLab.initialEntries())
                tv.setDebugEntries(labEntries.toList(), working = false)
                engineLive = true
                sessionImageLoader.bindSession(null, "", labMode = true)
                _state.update {
                    it.copy(
                        loading = false,
                        title = "Transcript lab",
                    )
                }
            } else {
                val sessionHandle = core.openSession(chatId)
                handle = sessionHandle
                sessionHandle.setViewAttached(true)
                val attached = tv.attach(core, chatId)
                if (!attached) {
                    releaseTranscriptEngine(tv, relay)
                    _state.update { it.copy(loading = false, error = "Could not attach transcript") }
                    return
                }
                engineLive = true
                refreshComposer()
                refreshHydration()
                refreshModelLabel()
            }
            applyViewportIfReady()
            transcriptHost?.bind(tv, transcriptActions)
            transcriptHost?.applyLatestFrame()
        } catch (e: Exception) {
            engineLive = false
            transcriptView?.let { tv ->
                frameRelay?.shutdown()
                frameRelay = null
                runCatching { tv.closeView() }
                runCatching { tv.close() }
            }
            transcriptView = null
            handle?.setViewAttached(false)
            handle = null
            _state.update { it.copy(loading = false, error = formatCoreError(e)) }
        }
    }

    fun refreshModelLabel() {
        if (isLab) return
        val core = appModel.client ?: return
        modelLabelRefreshJob?.cancel()
        modelLabelRefreshJob =
            viewModelScope.launch {
                try {
                    val label =
                        withContext(Dispatchers.Default) {
                            val row = core.sessionRow(chatId)
                            formatModelLabel(
                                row?.modelLabel,
                                row?.model,
                                row?.harnessLabel,
                                row?.reasoning,
                            )
                        }
                    if (handle == null) return@launch
                    _state.update { it.copy(modelLabel = label) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(
                        SESSION_VIEW_MODEL_LOG_TAG,
                        "Session model label refresh failed (" +
                            "${formatModelLabelRefreshFailureKind(e)})",
                    )
                }
            }
    }

    private fun releaseTranscriptEngine(
        tv: TranscriptView,
        relay: FrameRelay,
    ) {
        sessionImageLoader.bindSession(null, "", labMode = false)
        relay.shutdown()
        frameRelay = null
        runCatching { tv.closeView() }
        runCatching { tv.close() }
        transcriptView = null
        val h = handle
        if (h != null) {
            handle = null
            runCatching { h.setViewAttached(false) }
            runCatching { h.close() }
        }
    }

    fun attachTranscriptHost(host: TranscriptListView) {
        transcriptHost?.onScrollToLatestVisibilityChanged = null
        transcriptHost = host
        syncTranscriptHostPresentation()
        host.onScrollToLatestVisibilityChanged = { visible ->
            if (transcriptHost === host) {
                _state.update { it.copy(showScrollToLatest = visible) }
            }
        }
        host.replayScrollToLatestVisibility()
        val tv = transcriptView
        if (tv != null && engineLive) {
            host.bind(tv, transcriptActions)
            applyViewportIfReady()
            host.applyLatestFrame()
        }
    }

    fun clearTranscriptHost(host: TranscriptListView) {
        if (transcriptHost === host) {
            host.onScrollToLatestVisibilityChanged = null
            transcriptHost = null
            messageActionsController?.close()
            messageActionsController = null
            _state.update { it.copy(showScrollToLatest = false) }
        }
    }

    fun jumpToLatest() {
        transcriptHost?.jumpToLatest()
    }

    fun setViewport(
        widthDp: Float,
        textScale: Float,
    ) {
        _state.update { it.copy(viewportWidthDp = widthDp, textScale = textScale) }
        applyViewportIfReady()
    }

    private fun applyViewportIfReady() {
        val w = _state.value.viewportWidthDp
        if (w <= 0f) return
        transcriptView?.setViewport(w, _state.value.textScale)
        transcriptHost?.layoutWidthDp = w
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draftText = text) }
    }

    fun setDeliveryMode(mode: BusyPolicy) {
        _state.update { it.copy(composer = it.composer.copy(deliveryMode = mode)) }
    }

    fun refreshComposer() {
        val h = handle ?: return
        val c = h.composer()
        val hostLabel = c.host.name?.takeIf { it.isNotBlank() } ?: "Host"
        val prev = _state.value.composer
        val sendFailure = prev.sendFailure
        val (banner, bannerIsError) =
            when {
                sendFailure != null -> sendFailure to true
                c.sendState == SendState.FAILED -> "Not delivered" to true
                c.sendState == SendState.QUEUED ->
                    "$hostLabel is offline, will send when it's back" to false
                c.sendState == SendState.SENDING -> null to false
                else -> null to false
            }
        var deliveryMode = prev.deliveryMode
        val canSteer = c.host.capabilities.midTurnSteering == true
        if (!canSteer && deliveryMode == BusyPolicy.STEER) {
            deliveryMode = BusyPolicy.QUEUE
        }
        val hostId = c.host.deviceId
        val bindingChanged =
            sessionImageLoader.bindSession(
                appModel.client,
                hostId,
                labMode = false,
            )
        if (bindingChanged) {
            transcriptHost?.reloadImages()
        }
        val queue =
            c.queue.map { q ->
                QueueItemUi(
                    id = q.id,
                    text = q.visibleText.ifBlank { q.text },
                    actionPending = q.actionPending,
                )
            }
        syncTranscriptHostPresentation()
        _state.update {
            it.copy(
                title = c.title,
                composer =
                    ComposerStripState(
                        turnRunning = c.live.turnRunning,
                        canSteer = canSteer,
                        deliveryMode = deliveryMode,
                        banner = banner,
                        bannerIsError = bannerIsError,
                        sendFailure = sendFailure,
                        queueError = c.queueError ?: localQueueError,
                        queue = queue,
                        sendInFlight = sendInFlight,
                    ),
            )
        }
    }

    fun refreshHydration() {
        if (isLab) return
        val status = handle?.transcriptStatus()
        _state.update { it.copy(loading = status?.hydrated == false) }
        syncTranscriptHostPresentation()
    }

    private fun collectPendingSendRowKeys(): Set<ULong> {
        if (isLab) return emptySet()
        val h = handle ?: return emptySet()
        val c = h.composer()
        return c.pendingSends
            .filter { it.state == SendState.SENDING }
            .map { TranscriptRowKey.userMessage(it.messageId) }
            .toSet()
    }

    private fun syncTranscriptHostPresentation() {
        val host = transcriptHost ?: return
        host.transcriptReplayHydrated = isLab || !_state.value.loading
        host.setPendingSendStatusRowKeys(collectPendingSendRowKeys())
    }

    fun send() {
        if (isLab) {
            return
        }
        val text = _state.value.draftText.trim()
        if (text.isBlank() || sendInFlight) return
        val h = handle ?: return
        sendInFlight = true
        _state.update { it.copy(composer = it.composer.copy(sendInFlight = true)) }
        viewModelScope.launch {
            try {
                val busy =
                    withContext(Dispatchers.Default) {
                        val c = h.composer()
                        if (c.live.turnRunning) {
                            _state.value.composer.deliveryMode
                        } else {
                            BusyPolicy.QUEUE
                        }
                    }
                withContext(Dispatchers.Default) {
                    h.send(
                        SendRequest(
                            text = text,
                            attachments = emptyList(),
                            worktree = null,
                            busy = busy,
                        ),
                    )
                }
                _state.update {
                    it.copy(
                        draftText = "",
                        composer = it.composer.copy(sendFailure = null),
                    )
                }
                refreshComposer()
                refreshHydration()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Couldn't send: ${formatCoreError(e)}"
                _state.update {
                    it.copy(composer = it.composer.copy(sendFailure = msg))
                }
                refreshComposer()
            } finally {
                sendInFlight = false
                _state.update { it.copy(composer = it.composer.copy(sendInFlight = false)) }
            }
        }
    }

    fun replayLabStreaming() {
        if (!isLab || !engineLive) return
        if (labStreamJob?.isActive == true) {
            stopLabStream()
            return
        }
        startLabFixtureStream()
    }

    fun stopLabStreaming() {
        stopLabStream()
    }

    private fun startLabFixtureStream() {
        stopLabStream()
        val streamId = TranscriptLab.STREAM_ASSISTANT_ID
        val existing = labEntries.indexOfFirst { it.id == streamId }
        if (existing >= 0) {
            labEntries[existing] =
                DebugEntry(id = streamId, user = false, text = "", streaming = true)
        } else {
            labEntries.add(DebugEntry(id = streamId, user = false, text = "", streaming = true))
        }
        transcriptView?.setDebugEntries(labEntries.toList(), working = true)
        _state.update { it.copy(composer = it.composer.copy(turnRunning = true)) }
        val targets = TranscriptLab.fixtureStreamingTargets()
        labStreamJob =
            viewModelScope.launch {
                for ((i, prefix) in targets.withIndex()) {
                    if (!isActive) break
                    delay(45)
                    val streaming = i < targets.lastIndex
                    val idx = labEntries.indexOfFirst { it.id == streamId }
                    if (idx < 0) break
                    labEntries[idx] =
                        DebugEntry(
                            id = streamId,
                            user = false,
                            text = prefix,
                            streaming = streaming,
                        )
                    transcriptView?.setDebugEntries(labEntries.toList(), working = streaming)
                }
                stopLabStream()
            }
    }

    private fun stopLabStream() {
        labStreamJob?.cancel()
        labStreamJob = null
        val idx = labEntries.indexOfFirst { it.streaming }
        if (idx >= 0) {
            val entry = labEntries[idx]
            labEntries[idx] =
                DebugEntry(
                    id = entry.id,
                    user = entry.user,
                    text = entry.text,
                    streaming = false,
                )
            transcriptView?.setDebugEntries(labEntries.toList(), working = false)
        }
        _state.update { it.copy(composer = it.composer.copy(turnRunning = false)) }
    }

    fun removeQueued(id: String) {
        val h = handle ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { h.removeQueued(id) }
                localQueueError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localQueueError = formatCoreError(e)
            }
            refreshComposer()
            refreshHydration()
        }
    }

    fun deliverQueuedNow(id: String) {
        val h = handle ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { h.deliverQueuedNow(id) }
                localQueueError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localQueueError = formatCoreError(e)
            }
            refreshComposer()
            refreshHydration()
        }
    }

    override fun onCleared() {
        engineLive = false
        modelLabelRefreshJob?.cancel()
        modelLabelRefreshJob = null
        messageActionsController?.close()
        messageActionsController = null
        sessionImageLoader.close()
        stopLabStream()
        transcriptHost?.onScrollToLatestVisibilityChanged = null
        transcriptHost?.shutdown()
        transcriptHost = null
        _state.update { it.copy(showScrollToLatest = false) }
        frameRelay?.shutdown()
        frameRelay = null
        handle?.setViewAttached(false)
        transcriptView?.let { tv ->
            runCatching { tv.closeView() }
            runCatching { tv.close() }
        }
        handle = null
        transcriptView = null
        super.onCleared()
    }

    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun formatCoreError(e: Exception): String =
        when (e) {
            is CoreException.Auth -> e.detail
            is CoreException.NotFound -> e.detail
            is CoreException.InvalidArgument -> e.detail
            is CoreException.HostUnavailable -> e.detail
            is CoreException.Unsupported -> e.detail
            is CoreException.Network -> e.detail
            is CoreException.HostException -> e.detail
            is CoreException.Storage -> e.detail
            is CoreException.NotImplemented -> e.detail
            is CoreException.Closed -> "Client closed"
            is CoreException.Internal -> e.detail
            is CoreException -> e.message ?: e.toString()
            else -> e.message ?: e.toString()
        }
}
