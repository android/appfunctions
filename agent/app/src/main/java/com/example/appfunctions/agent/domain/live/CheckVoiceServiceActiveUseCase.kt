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
package com.example.appfunctions.agent.domain.live

import android.content.ComponentName
import android.content.Context
import android.service.voice.VoiceInteractionService
import com.example.appfunctions.agent.voice.AgentVoiceInteractionService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case checking whether [AgentVoiceInteractionService] is selected as the active Digital
 * Assistant on the device.
 */
class CheckVoiceServiceActiveUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Returns `true` if [AgentVoiceInteractionService] is currently the active service. */
        operator fun invoke(): Boolean =
            VoiceInteractionService.isActiveService(
                context,
                ComponentName(context, AgentVoiceInteractionService::class.java),
            )
    }
