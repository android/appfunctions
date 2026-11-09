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
package com.example.appfunctions.agent.domain.appfunction

import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionTruncationTest {
    @Test
    fun `cuts at first sentence`() {
        assertEquals("Create notes.", "Create notes. Supports tags.".toOneLineDescription())
    }

    @Test
    fun `cuts at first newline`() {
        assertEquals("Create notes", "Create notes\nSupports tags.".toOneLineDescription())
    }

    @Test
    fun `cuts at whichever delimiter comes first`() {
        assertEquals("First line", "First line\nSecond. Third.".toOneLineDescription())
        assertEquals("First.", "First. Second\nThird".toOneLineDescription())
    }

    @Test
    fun `does not cut at periods inside words or versions`() {
        assertEquals(
            "Supports v1.2 notes from example.com.",
            "Supports v1.2 notes from example.com. More text.".toOneLineDescription(),
        )
    }

    @Test
    fun `returns whole trimmed text when there is no delimiter`() {
        assertEquals("Create notes", "  Create notes  ".toOneLineDescription())
    }

    @Test
    fun `returns empty string for blank text`() {
        assertEquals("", "   ".toOneLineDescription())
    }
}
