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

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.example.appfunctions.agent.domain.LiveAgentOrchestrator
import com.example.appfunctions.agent.domain.chat.ManageThreadsUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hilt-injected [VoiceInteractionSessionService] factory that creates [AgentVoiceInteractionSession]
 * instances backed by [LiveAgentOrchestrator].
 */
@AndroidEntryPoint
class AgentVoiceInteractionSessionService : VoiceInteractionSessionService() {
    @Inject lateinit var liveAgentOrchestrator: LiveAgentOrchestrator

    @Inject lateinit var manageThreadsUseCase: ManageThreadsUseCase

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return AgentVoiceInteractionSession(
            context = this,
            liveAgentOrchestrator = liveAgentOrchestrator,
            manageThreadsUseCase = manageThreadsUseCase,
        )
    }
}
