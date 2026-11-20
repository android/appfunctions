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
package com.example.appfunctions.agent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ToolOutputImageTest {
    @Test
    fun `images with the same type and bytes are equal`() {
        val image = ToolOutputImage(mimeType = "image/png", data = byteArrayOf(1, 2, 3))
        val copy = ToolOutputImage(mimeType = "image/png", data = byteArrayOf(1, 2, 3))

        assertEquals(image, copy)
        assertEquals(image.hashCode(), copy.hashCode())
        assertEquals(
            ToolOutput(functionId = "getPhoto", callId = "call_1", result = "{}", images = listOf(image)),
            ToolOutput(functionId = "getPhoto", callId = "call_1", result = "{}", images = listOf(copy)),
        )
    }

    @Test
    fun `images with other bytes or another type are not equal`() {
        val image = ToolOutputImage(mimeType = "image/png", data = byteArrayOf(1, 2, 3))

        assertNotEquals(image, ToolOutputImage(mimeType = "image/png", data = byteArrayOf(1, 2, 4)))
        assertNotEquals(image, ToolOutputImage(mimeType = "image/jpeg", data = byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `toString shows the size of the image instead of its bytes`() {
        val image = ToolOutputImage(mimeType = "image/png", data = ByteArray(2048))

        assertEquals("ToolOutputImage(mimeType=image/png, size=2048)", image.toString())
    }
}
