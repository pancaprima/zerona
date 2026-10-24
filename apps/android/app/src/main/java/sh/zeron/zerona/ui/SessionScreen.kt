package sh.zeron.zerona.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import sh.zeron.zerona.transcript.Palette
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
import sh.zeron.zerona.ui.theme.rememberOrnamentalMotionEnabled

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

    val workspaceTick by appModel.workspaceTick.collectAsState()
    LaunchedEffect(workspaceTick) {
        viewModel.refreshModelLabel()
    }

    val appState by appModel.state.collectAsState()
    val showLoadingShell = state.loading && state.frameTick == 0L && state.error == null
    val ornamental = rememberOrnamentalMotionEnabled()
    val motion = MaterialTheme.motionScheme
    val transcriptReveal by animateFloatAsState(
        targetValue = if (showLoadingShell) 0f else 1f,
        animationSpec =
            if (ornamental) {
                motion.defaultEffectsSpec()
            } else {
                snap()
            },
        label = "transcriptReveal",
    )
    val transcriptScale by animateFloatAsState(
        targetValue = if (showLoadingShell) 0.98f else 1f,
        animationSpec =
            if (ornamental) {
                motion.defaultSpatialSpec()
            } else {
                snap()
            },
        label = "transcriptScale",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                title = {
                    Column {
                        Text(
                            state.title.ifBlank { "Session" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!isLab) {
                            val modelLabel = state.modelLabel
                            if (modelLabel != null) {
                                Text(
                                    modelLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "Model: $modelLabel"
                                        },
                                )
                            }
                        }
                    }
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
                        .clipToBounds(),
            ) {
                val viewportWidthDp = maxWidth.value
                LaunchedEffect(viewportWidthDp, textScale) {
                    viewModel.setViewport(viewportWidthDp, textScale)
                }
                AndroidView(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = transcriptReveal
                                scaleX = transcriptScale
                                scaleY = transcriptScale
                            }
                            .background(Color(Palette.background(dark))),
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLatest,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter =
                        if (ornamental) {
                            fadeIn(motion.fastEffectsSpec()) +
                                slideInVertically(motion.fastSpatialSpec()) { it / 3 }
                        } else {
                            EnterTransition.None
                        },
                    exit =
                        if (ornamental) {
                            fadeOut(motion.fastEffectsSpec()) +
                                slideOutVertically(motion.fastSpatialSpec()) { it / 4 }
                        } else {
                            ExitTransition.None
                        },
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
                        Icon(
                            imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (state.error == null && showLoadingShell) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZeronaInitialLoading(
                            label = "Loading transcript…",
                            modifier = Modifier.padding(24.dp),
                        )
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
