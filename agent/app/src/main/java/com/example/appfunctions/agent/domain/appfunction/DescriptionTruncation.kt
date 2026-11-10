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

/** Default maximum length of a summary produced by [toSummary]. */
const val DEFAULT_SUMMARY_MAX_LENGTH = 160

private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("""\s+""")
private val SENTENCE_END = Regex("""\.(?=\s|$)""")
private val ABBREVIATIONS = setOf("e.g", "i.e", "etc", "vs", "approx", "incl", "mr", "mrs", "ms", "dr")

/**
 * Returns a short summary of this description for tool catalogs.
 *
 * - If the whole text fits in [maxLength] characters, it is returned with whitespace collapsed.
 * - Otherwise it is cut at the first line break or sentence end, whichever comes first. A period
 *   only ends a sentence when followed by whitespace (or the end of the text) and not preceded by a
 *   common abbreviation such as "e.g.".
 * - The result is capped at [maxLength] characters on a word boundary, ending with "…".
 */
fun String.toSummary(maxLength: Int = DEFAULT_SUMMARY_MAX_LENGTH): String {
    val trimmed = trim()
    if (trimmed.length <= maxLength) {
        return trimmed.replace(WHITESPACE, " ")
    }
    val newlineIndex = trimmed.indexOf('\n').takeIf { it >= 0 } ?: trimmed.length
    val sentenceEndIndex =
        SENTENCE_END
            .findAll(trimmed)
            .firstOrNull { !trimmed.endsWithAbbreviationAt(it.range.first) }
            ?.range
            ?.first
            ?.plus(1) ?: trimmed.length
    val firstSentence =
        trimmed.substring(0, minOf(newlineIndex, sentenceEndIndex)).trim().replace(WHITESPACE, " ")
    return firstSentence.capAtWordBoundary(maxLength)
}

/** Returns whether the word ending right before [periodIndex] is a known abbreviation. */
private fun String.endsWithAbbreviationAt(periodIndex: Int): Boolean {
    val wordStart = lastIndexOfAny(charArrayOf(' ', '\n', '\t', '('), periodIndex - 1) + 1
    return substring(wordStart, periodIndex).lowercase() in ABBREVIATIONS
}

private fun String.capAtWordBoundary(maxLength: Int): String {
    if (length <= maxLength) {
        return this
    }
    val limit = maxLength - ELLIPSIS.length
    val lastSpace = lastIndexOf(' ', limit)
    val cut = if (lastSpace > 0) lastSpace else limit
    return substring(0, cut).trimEnd(' ', ',', ';', ':') + ELLIPSIS
}
