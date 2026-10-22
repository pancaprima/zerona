package sh.zeron.zerona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.app.SessionRowUi
import sh.zeron.zerona.transcript.TranscriptLab

private val sessionRowPressShape = RoundedCornerShape(12.dp)
private val labRowShape = RoundedCornerShape(12.dp)
private val sessionTimeMinWidth = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    appModel: AppModel,
    onSessionClick: (String) -> Unit,
) {
    val state by appModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                title = {
                    Text(
                        "Zerona",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (state.isOffline && !state.isDemo) {
                        StatusBadge(
                            label = "Offline",
                            container = MaterialTheme.colorScheme.secondary,
                            content = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    StatusBadge(
                        label = if (state.isDemo) "Demo" else "Live",
                        container =
                            if (state.isDemo) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        content =
                            if (state.isDemo) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiary
                            },
                        modifier = Modifier.padding(end = 2.dp),
                    )
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Account menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Core ${state.coreVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        if (state.isDemo) {
                            DropdownMenuItem(
                                text = { Text("Sign in") },
                                onClick = {
                                    menuOpen = false
                                    appModel.leaveDemoForSignIn()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menuOpen = false
                                    appModel.signOut()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (state.isDemo) {
                item(key = TranscriptLab.CHAT_ID) {
                    TranscriptLabEntryRow(
                        onClick = { onSessionClick(TranscriptLab.CHAT_ID) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            state.sections.forEach { section ->
                val sessions = section.sessions
                if (sessions.isEmpty()) return@forEach
                item(key = "h-${section.id}") {
                    SectionHeader(section.header)
                }
                items(
                    count = sessions.size,
                    key = { sessions[it].id },
                ) { index ->
                    val row = sessions[index]
                    SessionRowItem(
                        row,
                        onClick = { onSessionClick(row.id) },
                        showDivider = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptLabEntryRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val rowColor =
        if (pressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(labRowShape)
                .background(rowColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(12.dp),
    ) {
        Text(
            "Transcript lab (fixture markdown)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Offline fixture",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBadge(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(Locale.getDefault()),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SessionRowItem(
    row: SessionRowUi,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressColor =
        if (pressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(sessionRowPressShape)
                    .background(pressColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick,
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.title,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.timeLabel,
                modifier = Modifier.widthIn(min = sessionTimeMinWidth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}
