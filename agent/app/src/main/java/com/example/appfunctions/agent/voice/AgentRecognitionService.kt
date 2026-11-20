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

import android.content.Intent
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Companion [RecognitionService] required by Android's `VoiceInteractionServiceInfo` manifest
 * metadata validation so [AgentVoiceInteractionService] is selectable as the device's Default
 * Digital Assistant (`ROLE_ASSISTANT`).
 */
class AgentRecognitionService : RecognitionService() {
    override fun onStartListening(
        recognizerIntent: Intent?,
        listener: Callback?,
    ) {
        try {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (e: RemoteException) {
            Log.w(TAG, "Recognition callback binder died before error could be delivered", e)
        }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit

    private companion object {
        const val TAG = "AgentRecognitionSvc"
    }
}
