package sh.zeron.zerona.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import sh.zeron.zerona.transcript.TranscriptViewportScale
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
    val textScale = TranscriptViewportScale.viewportTextScale(configuration.fontScale)
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                title = {
                    Text(
                        state.title.ifBlank { "Session" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
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
                        .clipToBounds()
                        .padding(horizontal = 16.dp),
            ) {
                val viewportWidthDp = maxWidth.value
                LaunchedEffect(viewportWidthDp, textScale) {
                    viewModel.setViewport(viewportWidthDp, textScale)
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
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
                val showLatest = state.showScrollToLatest && state.error == null && !showLoadingShell
                val latestMotionMs = ComposerPresentation.CONTENT_MOTION_DURATION_MS
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLatest,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter =
                        fadeIn(tween(latestMotionMs)) +
                            slideInVertically(tween(latestMotionMs)) { it / 3 },
                    exit = fadeOut(tween(ComposerPresentation.CONTENT_MOTION_DURATION_SHORT_MS)),
                ) {
                    val scrollToLatestLabel = "Scroll to latest"
                    Surface(
                        onClick = viewModel::jumpToLatest,
                        modifier =
                            Modifier
                                .padding(bottom = 12.dp)
                                .semantics { contentDescription = scrollToLatestLabel },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Latest",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
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
