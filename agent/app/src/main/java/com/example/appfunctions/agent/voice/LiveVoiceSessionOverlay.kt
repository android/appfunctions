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
package com.example.appfunctions.agent.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.appfunctions.agent.R
import com.example.appfunctions.agent.domain.AgentStatus
import com.example.appfunctions.agent.domain.LiveSessionState
import kotlin.math.roundToInt

/**
 * Floating system-overlay pill displayed inside [AgentVoiceInteractionSession] while a Live voice
 * session is connecting, listening, invoking an AppFunction tool, or reporting an error.
 */
@Composable
fun LiveVoiceSessionOverlay(
    liveSessionState: LiveSessionState,
    agentStatus: AgentStatus,
    onStopClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOverlayBoundsChanged: (Rect) -> Unit = {},
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
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
                        liveSessionState = liveSessionState,
                        agentStatus = agentStatus,
                        packageManager = packageManager,
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
                            when {
                                agentStatus is AgentStatus.InvokingTool -> {
                                    val appLabel =
                                        resolveAppLabel(packageManager, agentStatus.packageName)
                                    stringResource(R.string.live_session_invoking_tool, appLabel)
                                }
                                liveSessionState is LiveSessionState.Connecting ->
                                    stringResource(R.string.agent_demo_live_connecting)
                                liveSessionState is LiveSessionState.Listening ->
                                    stringResource(R.string.agent_demo_live_listening)
                                liveSessionState is LiveSessionState.Error ->
                                    liveSessionState.message
                                else -> stringResource(R.string.agent_demo_live_connecting)
                            }
                        val subtitleColor =
                            if (liveSessionState is LiveSessionState.Error) {
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
                    if (liveSessionState is LiveSessionState.Error) {
                        TextButton(onClick = onRetryClick) {
                            Text(stringResource(R.string.live_session_retry))
                        }
                    }
                    FilledIconButton(
                        onClick = onStopClick,
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
    liveSessionState: LiveSessionState,
    agentStatus: AgentStatus,
    packageManager: PackageManager,
) {
    when {
        agentStatus is AgentStatus.InvokingTool -> {
            val appIcon =
                remember(agentStatus.packageName) {
                    runCatching {
                        packageManager.getApplicationIcon(agentStatus.packageName)
                    }.getOrNull()
                }
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.toBitmap().asImageBitmap(),
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

        liveSessionState is LiveSessionState.Listening -> {
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

        liveSessionState is LiveSessionState.Error -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        }

        else -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
            )
        }
    }
}

private fun resolveAppLabel(
    packageManager: PackageManager,
    packageName: String,
): String {
    if (packageName.isBlank()) return "AppFunction"
    return runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrDefault(packageName)
}

/**
 * Remembers a launcher callback that verifies `RECORD_AUDIO` permission and starts
 * [AgentVoiceInteractionService] (opening Digital Assistant settings via
 * [Settings.ACTION_VOICE_INPUT_SETTINGS] if this app is not yet the active
 * [android.service.voice.VoiceInteractionService]).
 */
@Composable
fun rememberLiveVoiceSessionLauncher(): (threadId: String, targetPackageName: String?) -> Unit {
    val context = LocalContext.current
    val selectAssistantPrompt = stringResource(R.string.live_session_select_assistant_prompt)
    var pendingThreadId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTargetPackageName by rememberSaveable { mutableStateOf<String?>(null) }

    val assistantSettingsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            val threadId = pendingThreadId
            val targetPackageName = pendingTargetPackageName
            pendingThreadId = null
            pendingTargetPackageName = null
            if (threadId != null && AgentVoiceInteractionService.isVoiceServiceActive(context)) {
                AgentVoiceInteractionService.launchLiveSession(
                    context = context,
                    threadId = threadId,
                    targetPackageName = targetPackageName,
                )
            }
        }

    val startVoiceServiceForRequest: (String, String?) -> Unit = { threadId, targetPackageName ->
        val launched =
            AgentVoiceInteractionService.launchLiveSession(
                context = context,
                threadId = threadId,
                targetPackageName = targetPackageName,
            )
        if (!launched) {
            pendingThreadId = threadId
            pendingTargetPackageName = targetPackageName
            Toast.makeText(
                context,
                selectAssistantPrompt,
                Toast.LENGTH_LONG,
            ).show()
            runCatching {
                assistantSettingsLauncher.launch(
                    AgentVoiceInteractionService.createAssistantSettingsIntent(),
                )
            }.onFailure {
                runCatching {
                    assistantSettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                    )
                }.onFailure {
                    pendingThreadId = null
                    pendingTargetPackageName = null
                }
            }
        }
    }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            val threadId = pendingThreadId
            val targetPackageName = pendingTargetPackageName
            pendingThreadId = null
            pendingTargetPackageName = null
            if (isGranted && threadId != null) {
                startVoiceServiceForRequest(threadId, targetPackageName)
            }
        }

    return { threadId, targetPackageName ->
        val hasRecordAudioPermission =
            runCatching {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(true)

        if (hasRecordAudioPermission) {
            startVoiceServiceForRequest(threadId, targetPackageName)
        } else {
            pendingThreadId = threadId
            pendingTargetPackageName = targetPackageName
            runCatching {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }.onFailure {
                pendingThreadId = null
                pendingTargetPackageName = null
                startVoiceServiceForRequest(threadId, targetPackageName)
            }
        }
    }
}
