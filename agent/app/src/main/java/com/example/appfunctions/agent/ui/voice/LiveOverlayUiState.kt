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

import com.example.appfunctions.agent.domain.appfunction.AppInfo

/** UI state for the floating [LiveVoiceSessionOverlay] and its session window host. */
data class LiveOverlayUiState(
    val isVisible: Boolean = false,
    val threadId: String? = null,
    val status: LiveOverlayStatus? = null,
    val invokingApp: AppInfo? = null,
    val keepVisibleOnSystemDialogsClose: Boolean = false,
)

/** Status displayed inside [LiveVoiceSessionOverlay]. */
sealed interface LiveOverlayStatus {
    data object Connecting : LiveOverlayStatus

    data object Listening : LiveOverlayStatus

    data class InvokingTool(
        val functionId: String,
        val packageName: String,
    ) : LiveOverlayStatus

    sealed interface Error : LiveOverlayStatus {
        data object AudioPermissionRequired : Error

        data class SessionFailed(val message: String) : Error
    }
}

/** UI events emitted by the floating [LiveVoiceSessionOverlay] and its session window host. */
sealed class LiveOverlayUiEvent {
    data class OnSessionShown(
        val threadId: String? = null,
        val targetPackageName: String? = null,
    ) : LiveOverlayUiEvent()

    data object OnSessionHidden : LiveOverlayUiEvent()

    data object OnStopClicked : LiveOverlayUiEvent()

    data object OnRetryClicked : LiveOverlayUiEvent()
}
