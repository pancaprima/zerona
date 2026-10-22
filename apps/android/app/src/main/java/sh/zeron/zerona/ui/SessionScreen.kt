package sh.zeron.zerona.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.transcript.TranscriptLab
import sh.zeron.zerona.transcript.TranscriptListView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    appModel: AppModel,
    chatId: String,
    onBack: () -> Unit,
    viewModel: SessionViewModel = viewModel(key = chatId) { SessionViewModel(appModel, chatId) },
) {
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val textScale = configuration.fontScale
    val dark = isSystemInDarkTheme()
    val isLab = chatId == TranscriptLab.CHAT_ID

    val refreshTick by appModel.refreshTick.collectAsState()
    LaunchedEffect(refreshTick) {
        viewModel.refreshComposer()
        viewModel.refreshHydration()
    }

    val appState by appModel.state.collectAsState()
    val showLoadingShell = state.loading && state.frameTick == 0L && state.error == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Session" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isLab) {
                        val streaming = state.composer.turnRunning
                        TextButton(
                            onClick = {
                                if (streaming) {
                                    viewModel.stopLabStreaming()
                                } else {
                                    viewModel.replayLabStreaming()
                                }
                            },
                        ) {
                            Text(if (streaming) "Stop" else "Replay streaming")
                        }
                    }
                },
            )
        },
        bottomBar = {
            SessionComposerBar(
                composer = state.composer,
                draftText = state.draftText,
                demoLocked = appState.isDemo || isLab,
                onDraftChange = viewModel::updateDraft,
                onDeliveryModeChange = viewModel::setDeliveryMode,
                onSend = viewModel::send,
                onRemoveQueued = viewModel::removeQueued,
                onDeliverQueuedNow = viewModel::deliverQueuedNow,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                Text(
                    state.error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                val viewportWidthDp = maxWidth.value
                LaunchedEffect(viewportWidthDp, textScale) {
                    viewModel.setViewport(viewportWidthDp, textScale)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TranscriptListView(ctx).also { host ->
                            host.darkTheme = dark
                            host.layoutWidthDp = viewportWidthDp
                            viewModel.attachTranscriptHost(host)
                        }
                    },
                    update = { host ->
                        val themeChanged = host.darkTheme != dark
                        host.darkTheme = dark
                        host.layoutWidthDp = viewportWidthDp
                        if (themeChanged) {
                            host.applyLatestFrame()
                        }
                    },
                    onRelease = { host ->
                        host.shutdown()
                        viewModel.clearTranscriptHost(host)
                    },
                )
                if (state.error == null && showLoadingShell) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                        Text("Loading transcript…")
                    }
                } else if (state.error == null && state.loading) {
                    Text(
                        "Loading earlier messages…",
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
