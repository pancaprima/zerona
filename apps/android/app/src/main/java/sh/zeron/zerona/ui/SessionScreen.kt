package sh.zeron.zerona.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.zeron.zerona.app.AppModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    appModel: AppModel,
    chatId: String,
    onBack: () -> Unit,
    viewModel: SessionViewModel = viewModel(key = chatId) { SessionViewModel(appModel, chatId) },
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    DisposableEffect(screenWidthDp) {
        val widthPx = screenWidthDp * density.density
        viewModel.setViewportWidthPx(widthPx)
        viewModel.refreshTranscript()
        onDispose { }
    }

    val refreshTick by appModel.refreshTick.collectAsState()
    LaunchedEffect(refreshTick) {
        viewModel.refreshComposer()
        viewModel.refreshTranscript()
    }

    val appState by appModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Session" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            SessionComposerBar(
                composer = state.composer,
                draftText = state.draftText,
                demoLocked = appState.isDemo,
                onDraftChange = viewModel::updateDraft,
                onDeliveryModeChange = viewModel::setDeliveryMode,
                onSend = viewModel::send,
                onRemoveQueued = viewModel::removeQueued,
                onDeliverQueuedNow = viewModel::deliverQueuedNow,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                state.m2Note,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.error != null) {
                Text(
                    state.error!!,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (state.loading && state.rows.isEmpty()) {
                Text("Loading transcript…", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.rows, key = { it.index }) { row ->
                        Text(
                            row.text,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        HorizontalDivider()
                    }
                    if (state.rows.isEmpty() && state.plainPreview.isNotBlank()) {
                        item("plain") {
                            Text(
                                state.plainPreview,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
