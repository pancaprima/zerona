package sh.zeron.zerona.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.zeron_core.BusyPolicy

@OptIn(ExperimentalLayoutApi::class)
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
    val draftNonBlank = draftText.isNotBlank()
    val canSend = ComposerPresentation.canSend(draftNonBlank, composer.sendInFlight)
    val scheme = MaterialTheme.colorScheme
    val dockColor = GlassInspired.composerDockColor()
    val fieldTextStyle =
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            color = scheme.onSurface,
        )
    val revealSpec =
        tween<IntSize>(durationMillis = ComposerPresentation.CONTENT_MOTION_DURATION_MS)
    val sendEnabledColors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.surfaceVariant,
            disabledContentColor = scheme.onSurfaceVariant,
        )
    val sendShape = RoundedCornerShape(10.dp)
    val demoFieldColors =
        OutlinedTextFieldDefaults.colors(
            disabledContainerColor = scheme.surfaceVariant,
            disabledTextColor = scheme.onSurfaceVariant,
            disabledBorderColor = scheme.outlineVariant,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(dockColor),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .animateContentSize(animationSpec = revealSpec),
        ) {
            HorizontalDivider(color = GlassInspired.tonalDividerColor(), thickness = 1.dp)

            composer.banner?.let { banner ->
                Text(
                    banner,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (composer.bannerIsError) {
                                    scheme.errorContainer
                                } else {
                                    scheme.surface
                                },
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (composer.bannerIsError) {
                            scheme.onErrorContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                )
            }
            composer.queueError?.let { err ->
                Text(
                    err,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                )
            }
            if (composer.queue.isNotEmpty()) {
                HorizontalDivider(color = scheme.outlineVariant)
                composer.queue.forEach { item ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.text,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurface,
                            maxLines = ComposerPresentation.MAX_QUEUED_PREVIEW_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!item.actionPending) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                TextButton(onClick = { onDeliverQueuedNow(item.id) }) {
                                    Text("Send now")
                                }
                                TextButton(onClick = { onRemoveQueued(item.id) }) {
                                    Text("Remove")
                                }
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

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (
                    ComposerPresentation.showDeliveryModeChips(
                        composer.turnRunning,
                        demoLocked,
                        draftNonBlank,
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DeliveryModeChip(
                            label = "Queue",
                            selected = composer.deliveryMode == BusyPolicy.QUEUE,
                            onClick = { onDeliveryModeChange(BusyPolicy.QUEUE) },
                        )
                        if (ComposerPresentation.showSteerChip(composer.canSteer)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (demoLocked) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            enabled = false,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = "Message" },
                            placeholder = {
                                Text(
                                    "Sign in to send (demo)",
                                    style = fieldTextStyle,
                                )
                            },
                            textStyle = fieldTextStyle,
                            shape = RoundedCornerShape(10.dp),
                            colors = demoFieldColors,
                            minLines = 1,
                            maxLines = ComposerPresentation.MAX_DRAFT_LINES,
                        )
                    } else {
                        val interactionSource = remember { MutableInteractionSource() }
                        val focused by interactionSource.collectIsFocusedAsState()
                        val fieldModifier =
                            Modifier
                                .weight(1f)
                                .then(
                                    if (focused) {
                                        Modifier.border(2.dp, scheme.primary, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .semantics { contentDescription = "Message" }
                        BasicTextField(
                            value = draftText,
                            onValueChange = onDraftChange,
                            modifier = fieldModifier,
                            textStyle = fieldTextStyle,
                            cursorBrush = SolidColor(scheme.primary),
                            minLines = 1,
                            maxLines = ComposerPresentation.MAX_DRAFT_LINES,
                            interactionSource = interactionSource,
                            decorationBox = { inner ->
                                Box {
                                    if (draftText.isEmpty()) {
                                        Text(
                                            "Message",
                                            style =
                                                fieldTextStyle.copy(
                                                    color = scheme.onSurfaceVariant,
                                                ),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        FilledIconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier =
                                Modifier
                                    .size(ComposerPresentation.SEND_TOUCH_TARGET_DP.dp)
                                    .semantics { contentDescription = "Send" },
                            colors = sendEnabledColors,
                            shape = sendShape,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
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
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.material3.Surface(
        modifier =
            Modifier
                .defaultMinSize(minHeight = 24.dp)
                .heightIn(min = 24.dp)
                .semantics {
                    role = Role.Button
                    this.selected = selected
                }
                .clickable(onClick = onClick),
        shape = shape,
        color =
            if (selected) {
                colors.primaryContainer
            } else {
                colors.surfaceVariant
            },
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    colors.onPrimaryContainer
                } else {
                    colors.onSurfaceVariant
                },
        )
    }
}
