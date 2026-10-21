package sh.zeron.zerona.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.zeron.zerona.app.AppModel
import uniffi.zeron_core.LayoutListener
import uniffi.zeron_core.SessionHandle
import uniffi.zeron_core.TranscriptView

data class TranscriptRowUi(
    val text: String,
    val index: Int,
)

data class SessionUiState(
    val title: String = "",
    val loading: Boolean = true,
    val rows: List<TranscriptRowUi> = emptyList(),
    val plainPreview: String = "",
    val error: String? = null,
    val m2Note: String =
        "Full Rust display-list painter ships in milestone M2.",
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
            val composer = sessionHandle.composer()
            _state.update { it.copy(title = composer.title, loading = true) }
            mainHandler.post { refreshTranscript() }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
        }
    }

    fun setViewportWidthPx(widthPx: Float) {
        transcriptView?.setViewport(widthPx, 1f)
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
        transcriptView?.close()
        handle = null
        transcriptView = null
        super.onCleared()
    }
}
