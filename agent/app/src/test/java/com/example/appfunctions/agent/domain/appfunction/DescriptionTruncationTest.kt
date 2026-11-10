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
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionTruncationTest {
    @Test
    fun `short text is returned in full with whitespace collapsed`() {
        assertEquals(
            "Create notes. Supports tags.",
            "  Create notes.\n  Supports tags.  ".toSummary(),
        )
    }

    @Test
    fun `long text is cut at the first sentence end`() {
        val text = "Create and manage notes. " + "More details. ".repeat(20)
        assertEquals("Create and manage notes.", text.toSummary())
    }

    @Test
    fun `long text is cut at the first line break when it comes first`() {
        val text = "Create notes\n" + "More details. ".repeat(20)
        assertEquals("Create notes", text.toSummary())
    }

    @Test
    fun `abbreviations and periods inside words do not end the sentence`() {
        val text = "Manage notes, e.g. lists from v1.2 or example.com, i.e. anything. " + "x ".repeat(100)
        assertEquals("Manage notes, e.g. lists from v1.2 or example.com, i.e. anything.", text.toSummary())
    }

    @Test
    fun `long first sentence is capped on a word boundary with an ellipsis`() {
        val text = "word ".repeat(100)
        val summary = text.toSummary(maxLength = 40)
        assertTrue(summary.length <= 40)
        assertTrue(summary.endsWith("…"))
        assertEquals("word word word word word word word word…", summary)
    }

    @Test
    fun `blank text returns an empty string`() {
        assertEquals("", "   ".toSummary())
    }
}
