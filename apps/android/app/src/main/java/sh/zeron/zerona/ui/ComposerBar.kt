package sh.zeron.zerona.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.zeron_core.BusyPolicy

@Composable
fun SessionComposerBar(
    composer: ComposerStripState,
    draftText: String,
    demoLocked: Boolean,
    onDraftChange: (String) -> Unit,
    onDeliveryModeChange: (BusyPolicy) -> Unit,
    onSend: () -> Unit,
    onRemoveQueued: (String) -> Unit,
    onDeliverQueuedNow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = draftText.isNotBlank() && !composer.sendInFlight
    val scheme = MaterialTheme.colorScheme
    val fieldColors =
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            disabledContainerColor = scheme.surfaceVariant,
            errorContainerColor = scheme.surface,
            focusedBorderColor = scheme.outline,
            unfocusedBorderColor = scheme.outlineVariant,
            disabledBorderColor = scheme.outlineVariant,
        )
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldTextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GlassInspired.composerBackdropColor(),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
        ) {
            composer.banner?.let { banner ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.surfaceVariant,
                ) {
                    Text(
                        banner,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (composer.bannerIsError) {
                                scheme.error
                            } else {
                                scheme.onSurfaceVariant
                            },
                    )
                }
            }
            composer.queueError?.let { err ->
                Text(
                    err,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }
            if (composer.queue.isNotEmpty()) {
                HorizontalDivider(color = scheme.outlineVariant)
                composer.queue.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            item.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                        )
                        if (!item.actionPending) {
                            TextButton(onClick = { onDeliverQueuedNow(item.id) }) {
                                Text("Send now")
                            }
                            TextButton(onClick = { onRemoveQueued(item.id) }) {
                                Text("Remove")
                            }
                        } else {
                            Text(
                                "Updating…",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
            }
            GlassInspired.GlassCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (composer.turnRunning && !demoLocked && draftText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            DeliveryModeChip(
                                label = "Queue",
                                selected = composer.deliveryMode == BusyPolicy.QUEUE,
                                onClick = { onDeliveryModeChange(BusyPolicy.QUEUE) },
                            )
                            if (composer.canSteer) {
                                DeliveryModeChip(
                                    label = "Steer",
                                    selected = composer.deliveryMode == BusyPolicy.STEER,
                                    onClick = { onDeliveryModeChange(BusyPolicy.STEER) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (demoLocked) {
                            OutlinedTextField(
                                value = "",
                                onValueChange = {},
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Sign in to send (demo)",
                                        style = fieldTextStyle,
                                    )
                                },
                                textStyle = fieldTextStyle,
                                shape = fieldShape,
                                colors = fieldColors,
                                minLines = 1,
                                maxLines = 4,
                            )
                        } else {
                            OutlinedTextField(
                                value = draftText,
                                onValueChange = onDraftChange,
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text("Message", style = fieldTextStyle)
                                },
                                textStyle = fieldTextStyle,
                                shape = fieldShape,
                                colors = fieldColors,
                                minLines = 1,
                                maxLines = 6,
                            )
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint =
                                        if (canSend) {
                                            scheme.primary
                                        } else {
                                            scheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            Modifier
                .height(24.dp)
                .semantics {
                    role = Role.Button
                    this.selected = selected
                }
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color =
            if (selected) {
                colors.secondaryContainer
            } else {
                colors.surface
            },
        shadowElevation = if (selected) 2.dp else 0.dp,
        tonalElevation = if (selected) 0.dp else 1.dp,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    colors.onSecondaryContainer
                } else {
                    colors.onSurfaceVariant
                },
        )
    }
}
