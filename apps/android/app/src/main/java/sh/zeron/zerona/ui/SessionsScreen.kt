package sh.zeron.zerona.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import sh.zeron.zerona.app.AppModel
import sh.zeron.zerona.app.AppUiState
import sh.zeron.zerona.app.SessionRowUi
import sh.zeron.zerona.transcript.TranscriptLab

private val sessionListHorizontalInset = 24.dp
private val sessionGroupCorner = 20.dp
private val sessionRowPressShape = RoundedCornerShape(16.dp)
private val labRowShape = RoundedCornerShape(20.dp)
private val sessionTimeMinWidth = 48.dp
private val sessionTimeMaxWidth = 80.dp
private const val sessionsListHeaderKey = "sessions-list-header"
private val accountButtonVisualSize = 40.dp
private val accountButtonTouchSize = 48.dp
private val brandMarkSize = 22.dp

private enum class GroupRowPosition {
    Only,
    Top,
    Middle,
    Bottom,
}

@Composable
fun SessionsScreen(
    appModel: AppModel,
    onSessionClick: (String) -> Unit,
) {
    val state by appModel.state.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val screenBackground = scheme.background
    val groupFill = sessionGroupFillColor()
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(screenBackground)
                .safeDrawingPadding(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = sessionsListHeaderKey) {
            SessionsListHeader(
                state = state,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onSignIn = {
                    menuOpen = false
                    appModel.leaveDemoForSignIn()
                },
                onSignOut = {
                    menuOpen = false
                    appModel.signOut()
                },
            )
        }
        if (state.isDemo) {
            item(key = TranscriptLab.CHAT_ID) {
                TranscriptLabEntryRow(
                    onClick = { onSessionClick(TranscriptLab.CHAT_ID) },
                    modifier =
                        Modifier
                            .padding(horizontal = sessionListHorizontalInset)
                            .padding(bottom = 16.dp),
                    groupFill = groupFill,
                )
            }
        }
        state.sections.forEach { section ->
            val sessions = section.sessions
            if (sessions.isEmpty()) return@forEach
            val accent = projectAccentForSection(section.id, scheme)
            item(key = "h-${section.id}") {
                SectionHeader(
                    title = section.header,
                    modifier =
                        Modifier
                            .padding(horizontal = sessionListHorizontalInset)
                            .padding(top = if (section == state.sections.first { it.sessions.isNotEmpty() }) 0.dp else 8.dp),
                )
            }
            items(
                count = sessions.size,
                key = { sessions[it].id },
            ) { index ->
                val position =
                    when {
                        sessions.size == 1 -> GroupRowPosition.Only
                        index == 0 -> GroupRowPosition.Top
                        index == sessions.size - 1 -> GroupRowPosition.Bottom
                        else -> GroupRowPosition.Middle
                    }
                SessionRowItem(
                    row = sessions[index],
                    onClick = { onSessionClick(sessions[index].id) },
                    groupPosition = position,
                    groupFill = groupFill,
                    markerColor = accent,
                    modifier = Modifier.padding(horizontal = sessionListHorizontalInset),
                )
            }
            item(key = "gap-${section.id}") {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SessionsListHeader(
    state: AppUiState,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sessionListHorizontalInset)
                .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZeronaDecorMark(sizeDp = brandMarkSize)
                Text(
                    "ZERONA",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                Box(
                    modifier =
                        Modifier
                            .defaultMinSize(
                                minWidth = accountButtonTouchSize,
                                minHeight = accountButtonTouchSize,
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer)
                            .clickable { onMenuOpenChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Account menu",
                        modifier = Modifier.size(accountButtonVisualSize * 0.5f),
                        tint = scheme.onPrimaryContainer,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Core ${state.coreVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    if (state.isDemo) {
                        DropdownMenuItem(
                            text = { Text("Sign in") },
                            onClick = onSignIn,
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = onSignOut,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your sessions.",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ConnectionStatusLine(
            state = state,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionStatusLine(
    state: AppUiState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (label, dotColor) =
        when {
            state.isDemo ->
                "Demo" to scheme.primary
            state.isOffline ->
                "Offline" to scheme.secondary
            else ->
                "Live" to scheme.tertiary
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun sessionGroupFillColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        scheme.surfaceContainer
    } else {
        scheme.primaryContainer.copy(alpha = 0.55f)
    }
}

@Composable
private fun sessionRowPressColor(pressed: Boolean): Color {
    if (!pressed) return Color.Transparent
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        scheme.surfaceContainerHigh
    } else {
        scheme.primaryContainer
    }
}

private fun projectAccentForSection(
    sectionId: String,
    scheme: ColorScheme,
): Color {
    // Deterministic decorative marker per project section (not a11y status).
    val palette =
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
        )
    val index = (sectionId.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}

@Composable
private fun groupRowBackgroundShape(position: GroupRowPosition): Shape {
    val r = sessionGroupCorner
    return when (position) {
        GroupRowPosition.Only -> RoundedCornerShape(r)
        GroupRowPosition.Top ->
            RoundedCornerShape(
                topStart = r,
                topEnd = r,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )
        GroupRowPosition.Middle -> RoundedCornerShape(0.dp)
        GroupRowPosition.Bottom ->
            RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = r,
                bottomEnd = r,
            )
    }
}

@Composable
private fun TranscriptLabEntryRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    groupFill: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val rowColor =
        if (pressed) {
            sessionRowPressColor(true)
        } else {
            groupFill
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "Transcript lab (fixture markdown)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "Offline fixture",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title.uppercase(Locale.getDefault()),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SessionRowItem(
    row: SessionRowUi,
    onClick: () -> Unit,
    groupPosition: GroupRowPosition,
    groupFill: Color,
    markerColor: Color,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressColor = sessionRowPressColor(pressed)
    val scheme = MaterialTheme.colorScheme
    val groupShape = groupRowBackgroundShape(groupPosition)
    val horizontalPad = 12.dp
    val verticalPad = if (groupPosition == GroupRowPosition.Top || groupPosition == GroupRowPosition.Only) 6.dp else 0.dp
    val bottomPad = if (groupPosition == GroupRowPosition.Bottom || groupPosition == GroupRowPosition.Only) 6.dp else 0.dp
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(groupShape)
                .background(groupFill)
                .padding(
                    start = horizontalPad,
                    end = horizontalPad,
                    top = verticalPad,
                    bottom = bottomPad,
                ),
    ) {
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(markerColor),
            )
            Text(
                row.title,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.timeLabel,
                modifier =
                    Modifier.widthIn(
                        min = sessionTimeMinWidth,
                        max = sessionTimeMaxWidth,
                    ),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
