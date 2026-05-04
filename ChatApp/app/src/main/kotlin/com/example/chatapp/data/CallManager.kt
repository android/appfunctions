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
package com.example.chatapp.data

import com.example.chatapp.appfunctions.AppFunctions.Recipient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManager
    @Inject
    constructor(
        private val recipientsRepository: RecipientsRepository,
    ) {
        private val _activeCall = MutableStateFlow<Recipient?>(null)
        val activeCall: StateFlow<Recipient?> = _activeCall.asStateFlow()

        fun startCall(recipient: Recipient) {
            _activeCall.value = recipient
        }

        fun startCall(recipientId: String) {
            val recipient =
                recipientsRepository.getRecipientById(recipientId)
                    ?: recipientsRepository.getGroupById(recipientId)?.let {
                        Recipient(it.id, it.name, "")
                    }
                    ?: recipientsRepository.getRecipientByName(recipientId)
                    ?: Recipient(recipientId, recipientId, "")
            startCall(recipient)
        }

        fun endCall() {
            _activeCall.value = null
        }
    }
