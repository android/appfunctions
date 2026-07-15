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

import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSystemUnknownException
import androidx.appfunctions.AppFunctionUnknownException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFunctionExceptionFormatterTest {
    @Test
    fun `format formats AppFunctionPermissionRequiredException correctly`() {
        val exception = AppFunctionPermissionRequiredException("Calendar permission required")
        val formatted = AppFunctionExceptionFormatter.format(exception)
        assertEquals(
            "Error: AppFunctionPermissionRequiredException - Calendar permission required",
            formatted,
        )
    }

    @Test
    fun `format formats AppFunctionInvalidArgumentException correctly`() {
        val exception = AppFunctionInvalidArgumentException("Invalid date format")
        val formatted = AppFunctionExceptionFormatter.format(exception)
        assertEquals(
            "Error: AppFunctionInvalidArgumentException - Invalid date format",
            formatted,
        )
    }

    @Test
    fun `format formats AppFunctionSystemUnknownException correctly`() {
        val exception = AppFunctionSystemUnknownException("Service not found")
        val formatted = AppFunctionExceptionFormatter.format(exception)
        assertEquals(
            "Error: AppFunctionSystemUnknownException - Service not found",
            formatted,
        )
    }

    @Test
    fun `format formats AppFunctionUnknownException correctly`() {
        val exception = AppFunctionUnknownException(errorCode = 9999, errorMessage = "Future error")
        val formatted = AppFunctionExceptionFormatter.format(exception)
        assertEquals(
            "Error: AppFunctionUnknownException - Future error",
            formatted,
        )
    }

    @Test
    fun `getAppFunctionException extracts exception from cause chain`() {
        val appException = AppFunctionInvalidArgumentException("Invalid arg")
        val wrappedException = RuntimeException("Wrapper", RuntimeException("Inner wrapper", appException))

        val extracted = AppFunctionExceptionFormatter.getAppFunctionException(wrappedException)
        assertEquals(appException, extracted)
    }

    @Test
    fun `format includes functionId prefix when provided`() {
        val exception = AppFunctionInvalidArgumentException("Message body cannot be empty")
        val formatted =
            AppFunctionExceptionFormatter.format(
                exception,
                "com.example.chatapp.appfunctions.BaseChatAppFunctionService#send",
            )
        assertEquals(
            "Tool execution failed for com.example.chatapp.appfunctions.BaseChatAppFunctionService#send: " +
                "Error: AppFunctionInvalidArgumentException - Message body cannot be empty",
            formatted,
        )
    }
}
