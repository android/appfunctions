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

private val SENTENCE_END = Regex("""\.(\s|$)""")

/**
 * Returns a one-line summary of this description.
 *
 * The text is cut at the first line break or at the first period that is followed by whitespace
 * (or the end of the text), whichever comes first. The period is kept. Periods that are not
 * followed by whitespace (e.g. "v1.2" or "example.com") do not end the summary.
 */
fun String.toOneLineDescription(): String {
    val trimmed = trim()
    val newlineIndex = trimmed.indexOf('\n').takeIf { it >= 0 } ?: trimmed.length
    val sentenceEndIndex =
        SENTENCE_END.find(trimmed)?.range?.first?.plus(1) ?: trimmed.length
    return trimmed.substring(0, minOf(newlineIndex, sentenceEndIndex)).trim()
}
