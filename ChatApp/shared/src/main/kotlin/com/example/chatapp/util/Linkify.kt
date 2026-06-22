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
package com.example.chatapp.util

import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

fun linkifyString(text: String, linkColor: Color? = null): AnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    var lastIndex = 0
    return buildAnnotatedString {
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()

            append(text.substring(lastIndex, start))

            val uriStr = if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                url
            } else {
                "https://$url"
            }
            
            val linkStyle = SpanStyle(
                color = linkColor ?: Color.Unspecified,
                textDecoration = TextDecoration.Underline
            )

            pushLink(
                LinkAnnotation.Url(
                    url = uriStr,
                    styles = TextLinkStyles(style = linkStyle)
                )
            )
            append(url)
            pop()

            lastIndex = end
        }
        append(text.substring(lastIndex, text.length))
    }
}
