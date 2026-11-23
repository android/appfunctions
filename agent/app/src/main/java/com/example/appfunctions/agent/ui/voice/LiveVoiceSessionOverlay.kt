/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.appfunctions.agent.ui.voice

import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.appfunction.AppInfo
import com.example.appfunctions.agent.ui.theme.AppFunctionsAgentTheme
import kotlin.math.roundToInt

/**
 * Stateless floating system-overlay pill displayed inside
 * [com.example.appfunctions.agent.voice.AgentVoiceInteractionSession] while a Live voice session is
 * connecting, listening, invoking an AppFunction tool, or reporting an error.
 */
@Composable
fun LiveVoiceSessionOverlay(
    uiState: LiveOverlayUiState,
    onEvent: (LiveOverlayUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChat: (threadId: String?) -> Unit = {},
    onOverlayBoundsChanged: (Rect) -> Unit = {},
) {
    val status = uiState.status ?: return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            onClick = { onOpenChat(uiState.threadId) },
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        onOverlayBoundsChanged(
                            Rect(
                                bounds.left.roundToInt(),
                                bounds.top.roundToInt(),
                                bounds.right.roundToInt(),
                                bounds.bottom.roundToInt(),
                            ),
                        )
                    },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveStatusLeadingIcon(
                        status = status,
                        invokingAppIcon = uiState.invokingApp?.icon,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.live_session_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        val subtitle =
                            when (status) {
                                LiveOverlayStatus.Connecting ->
                                    stringResource(R.string.agent_demo_live_connecting)
                                LiveOverlayStatus.Listening ->
                                    stringResource(R.string.agent_demo_live_listening)
                                is LiveOverlayStatus.InvokingTool -> {
                                    val appLabel =
                                        uiState.invokingApp?.label?.takeIf { it.isNotBlank() }
                                            ?: status.packageName.takeIf { it.isNotBlank() }
                                            ?: stringResource(
                                                R.string.live_session_default_tool_target,
                                            )
                                    stringResource(R.string.live_session_invoking_tool, appLabel)
                                }
                                LiveOverlayStatus.Error.AudioPermissionRequired ->
                                    stringResource(R.string.live_session_audio_permission_required)
                                is LiveOverlayStatus.Error.SessionFailed -> status.message
                            }
                        val subtitleColor =
                            if (status is LiveOverlayStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status is LiveOverlayStatus.Error) {
                        TextButton(onClick = { onEvent(LiveOverlayUiEvent.OnRetryClicked) }) {
                            Text(stringResource(R.string.live_session_retry))
                        }
                    }
                    FilledIconButton(
                        onClick = { onEvent(LiveOverlayUiEvent.OnStopClicked) },
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.agent_demo_live_stop),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStatusLeadingIcon(
    status: LiveOverlayStatus,
    invokingAppIcon: Drawable?,
) {
    when (status) {
        is LiveOverlayStatus.InvokingTool -> {
            if (invokingAppIcon != null) {
                Image(
                    painter = rememberAsyncImagePainter(invokingAppIcon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        }

        LiveOverlayStatus.Listening -> {
            val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
            val scale by
                infiniteTransition.animateFloat(
                    initialValue = 0.88f,
                    targetValue = 1.15f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 700),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "live_pulse_scale",
                )
            Surface(
                modifier = Modifier.size(36.dp).scale(scale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        is LiveOverlayStatus.Error -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        }

        LiveOverlayStatus.Connecting -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveVoiceSessionOverlayConnectingPreview() {
    AppFunctionsAgentTheme {
        LiveVoiceSessionOverlay(
            uiState =
                LiveOverlayUiState(
                    isVisible = true,
                    status = LiveOverlayStatus.Connecting,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveVoiceSessionOverlayListeningPreview() {
    AppFunctionsAgentTheme {
        LiveVoiceSessionOverlay(
            uiState =
                LiveOverlayUiState(
                    isVisible = true,
                    status = LiveOverlayStatus.Listening,
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveVoiceSessionOverlayInvokingToolPreview() {
    AppFunctionsAgentTheme {
        LiveVoiceSessionOverlay(
            uiState =
                LiveOverlayUiState(
                    isVisible = true,
                    status = LiveOverlayStatus.InvokingTool("sendMessage", "com.example.chat"),
                    invokingApp = AppInfo("com.example.chat", "ChatApp", null),
                ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LiveVoiceSessionOverlayErrorPreview() {
    AppFunctionsAgentTheme {
        LiveVoiceSessionOverlay(
            uiState =
                LiveOverlayUiState(
                    isVisible = true,
                    status = LiveOverlayStatus.Error.SessionFailed("API key is missing"),
                ),
            onEvent = {},
        )
    }
}
