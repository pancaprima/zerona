package sh.zeron.zerona.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.zeron.zerona.app.AppModel
import uniffi.zeron_core.BusyPolicy
import uniffi.zeron_core.CoreException
import uniffi.zeron_core.LayoutListener
import uniffi.zeron_core.SendRequest
import uniffi.zeron_core.SendState
import uniffi.zeron_core.SessionHandle
import uniffi.zeron_core.TranscriptView

data class TranscriptRowUi(
    val text: String,
    val index: Int,
)

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
    val loading: Boolean = true,
    val rows: List<TranscriptRowUi> = emptyList(),
    val plainPreview: String = "",
    val error: String? = null,
    val m2Note: String =
        "Full Rust display-list painter ships in milestone M2.",
    val draftText: String = "",
    val composer: ComposerStripState = ComposerStripState(),
)

class SessionViewModel(
    private val appModel: AppModel,
    private val chatId: String,
) : ViewModel() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var handle: SessionHandle? = null
    private var transcriptView: TranscriptView? = null
    private var sendInFlight = false
    private var localQueueError: String? = null

    private val layoutListener =
        object : LayoutListener {
            override fun frameReady(revision: ULong) {
                mainHandler.post { refreshTranscript() }
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
            val sessionHandle = core.openSession(chatId)
            handle = sessionHandle
            sessionHandle.setViewAttached(true)
            val tv = TranscriptView(text, layoutListener)
            transcriptView = tv
            val attached = tv.attach(core, chatId)
            if (!attached) {
                _state.update { it.copy(loading = false, error = "Could not attach transcript") }
                return
            }
            refreshComposer()
            mainHandler.post { refreshTranscript() }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = formatCoreError(e)) }
        }
    }

    fun setViewportWidthPx(widthPx: Float) {
        transcriptView?.setViewport(widthPx, 1f)
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
                c.sendState == SendState.SENDING -> "Sending…" to false
                else -> null to false
            }
        var deliveryMode = prev.deliveryMode
        val canSteer = c.host.capabilities.midTurnSteering == true
        if (!canSteer && deliveryMode == BusyPolicy.STEER) {
            deliveryMode = BusyPolicy.QUEUE
        }
        val queue =
            c.queue.map { q ->
                QueueItemUi(
                    id = q.id,
                    text = q.visibleText.ifBlank { q.text },
                    actionPending = q.actionPending,
                )
            }
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

    fun send() {
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
                refreshTranscript()
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
            refreshTranscript()
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
            refreshTranscript()
        }
    }

    fun refreshTranscript() {
        val tv = transcriptView ?: return
        val frame = tv.frame()
        val count = frame.rowCount().toInt()
        val rows = mutableListOf<TranscriptRowUi>()
        for (i in 0 until count) {
            val text = frame.messageText(i.toUInt()) ?: continue
            if (text.isNotBlank()) {
                rows += TranscriptRowUi(text = text.trim(), index = i)
            }
        }
        val status = handle?.transcriptStatus()
        _state.update {
            it.copy(
                loading = status?.hydrated == false,
                rows = rows,
                plainPreview = if (rows.isEmpty()) frame.plainText().take(4000) else "",
            )
        }
    }

    override fun onCleared() {
        handle?.setViewAttached(false)
        transcriptView?.closeView()
        transcriptView?.close()
        handle = null
        transcriptView = null
        super.onCleared()
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
