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
package com.example.chatapp.uicomponents

import android.net.Uri

/**
 * A chat message.
 */
data class DisplayMessage(
    /**
     * The message content
     */
    val content: String,
    /**
     * When the message was sent
     */
    val sentAt: Long,
    /**
     * `true` if the message was received by the current user, `false` if it was sent.
     */
    val isInbound: Boolean = false,
    /**
     * The sender's name.
     */
    val senderName: String? = null,
    /**
     * The sender's display avatar.
     */
    val senderAvatar: Uri? = null,
    /**
     * Images attached to the message.
     */
    val images: List<Uri> = emptyList(),
)
