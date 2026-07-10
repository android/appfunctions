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

import androidx.appfunctions.AppFunctionException

/** Formats an [AppFunctionException] into a detailed string representation for LLM tool outputs and debugging. */
object AppFunctionExceptionFormatter {
    /**
     * Formats the given [exception] including its class name and message.
     */
    fun format(exception: AppFunctionException, functionId: String? = null): String {
        val className = exception.javaClass.simpleName
        val message = exception.errorMessage ?: exception.message ?: "No error message provided"
        val prefix = if (functionId != null) "Tool execution failed for $functionId: " else ""
        return "${prefix}Error: $className - $message"
    }

    /**
     * Extracts an [AppFunctionException] from the given [throwable] or its cause chain, if present.
     */
    fun getAppFunctionException(throwable: Throwable?): AppFunctionException? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is AppFunctionException) {
                return current
            }
            current = current.cause
        }
        return null
    }
}
