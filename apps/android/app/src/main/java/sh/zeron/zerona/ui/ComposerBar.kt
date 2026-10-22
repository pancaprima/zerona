package sh.zeron.zerona.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        composer.banner?.let { banner ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    banner,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (composer.bannerIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        composer.queueError?.let { err ->
            Text(
                err,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (composer.queue.isNotEmpty()) {
            HorizontalDivider()
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        if (composer.turnRunning && !demoLocked) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = composer.deliveryMode == BusyPolicy.QUEUE,
                    onClick = { onDeliveryModeChange(BusyPolicy.QUEUE) },
                    label = { Text("Queue") },
                )
                if (composer.canSteer) {
                    FilterChip(
                        selected = composer.deliveryMode == BusyPolicy.STEER,
                        onClick = { onDeliveryModeChange(BusyPolicy.STEER) },
                        label = { Text("Steer") },
                    )
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (demoLocked) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Sign in to send (demo)") },
                    minLines = 1,
                    maxLines = 4,
                )
            } else {
                OutlinedTextField(
                    value = draftText,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
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
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
