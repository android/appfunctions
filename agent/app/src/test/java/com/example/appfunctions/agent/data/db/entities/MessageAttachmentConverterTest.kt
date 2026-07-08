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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAttachmentConverterTest {

    private val converter = MessageAttachmentConverter()

    @Test
    fun testSerializationAndDeserialization() {
        val attachments =
            listOf(
                MessageAttachment(
                    uri = "content://com.example.appfunctions.agent.fileprovider/cache/test.jpg",
                    mimeType = "image/jpeg"
                ),
                MessageAttachment(
                    uri = "content://com.example.appfunctions.agent.fileprovider/cache/test.png",
                    mimeType = "image/png"
                )
            )

        val json = converter.fromAttachments(attachments)
        val decoded = converter.toAttachments(json)

        assertEquals(attachments, decoded)
    }

    @Test
    fun testEmptyOrNullStringDeserializesToEmptyList() {
        assertTrue(converter.toAttachments(null).isEmpty())
        assertTrue(converter.toAttachments("").isEmpty())
    }

    @Test
    fun testMalformedJsonDeserializesToEmptyList() {
        assertTrue(converter.toAttachments("{invalid_json").isEmpty())
    }
}
