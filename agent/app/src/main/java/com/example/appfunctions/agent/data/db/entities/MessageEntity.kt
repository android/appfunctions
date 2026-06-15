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
package com.example.appfunctions.agent.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys =
        [
            ForeignKey(
                entity = ThreadEntity::class,
                parentColumns = ["threadId"],
                childColumns = ["threadId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("threadId")],
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val threadId: String,
    val role: MessageRole,
    val textContent: String,
    val timestamp: Long,
    val processingStatus: MessageProcessingStatus,
    /**
     * An ID that matches a cached Action interaction in the dynamic transient action repository. Is
     * only non-null if Assistant returned PendingIntent tool response.
     */
    val pendingIntentId: String? = null,
    val targetPackageName: String? = null,
)

enum class MessageRole {
    USER,
    ASSISTANT,
}

enum class MessageProcessingStatus {
    PENDING_AGENT_RESPONSE,
    PROCESSED,
    FAILED,
}
