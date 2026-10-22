package sh.zeron.zerona.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.app.SessionRowUi
import sh.zeron.zerona.transcript.TranscriptLab

private val sessionGroupCornerRadius = 14.dp

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZeronaDecorMark(
                            modifier = Modifier.padding(end = 8.dp),
                            sizeDp = 22.dp,
                        )
                        Text(
                            "Zerona",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.isOffline && !state.isDemo) {
                        StatusBadge(
                            label = "Offline",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    StatusBadge(
                        label = if (state.isDemo) "Demo" else "Live",
                        container =
                            if (state.isDemo) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                        content =
                            if (state.isDemo) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
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
        ) {
            if (state.isDemo) {
                item(key = TranscriptLab.CHAT_ID) {
                    SessionGroupRow(
                        shape = RoundedCornerShape(sessionGroupCornerRadius),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        SessionRowItem(
                            SessionRowUi(
                                id = TranscriptLab.CHAT_ID,
                                title = "Transcript lab (fixture markdown)",
                                timeLabel = "Offline fixture",
                            ),
                            onClick = { onSessionClick(TranscriptLab.CHAT_ID) },
                            showDivider = false,
                        )
                    }
                }
            }
            state.sections.forEach { section ->
                val sessions = section.sessions
                if (sessions.isEmpty()) return@forEach
                item(key = "h-${section.id}") {
                    SessionGroupRow(
                        shape =
                            RoundedCornerShape(
                                topStart = sessionGroupCornerRadius,
                                topEnd = sessionGroupCornerRadius,
                            ),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
                    ) {
                        SectionHeader(section.header)
                    }
                }
                items(
                    count = sessions.size,
                    key = { sessions[it].id },
                ) { index ->
                    val row = sessions[index]
                    val isLast = index == sessions.size - 1
                    val shape: Shape =
                        if (isLast) {
                            RoundedCornerShape(
                                bottomStart = sessionGroupCornerRadius,
                                bottomEnd = sessionGroupCornerRadius,
                            )
                        } else {
                            RectangleShape
                        }
                    SessionGroupRow(
                        shape = shape,
                        modifier =
                            Modifier.padding(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = if (isLast) 6.dp else 0.dp,
                            ),
                    ) {
                        SessionRowItem(
                            row,
                            onClick = { onSessionClick(row.id) },
                            showDivider = !isLast,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionGroupRow(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
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
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SessionRowItem(
    row: sh.zeron.zerona.app.SessionRowUi,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            row.timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
