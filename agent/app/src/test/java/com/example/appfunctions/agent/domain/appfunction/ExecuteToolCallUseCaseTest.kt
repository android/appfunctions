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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.metadata.AppFunctionMetadata
import com.example.appfunctions.agent.data.AgentInternalTools
import com.example.appfunctions.agent.data.db.entities.MessageAttachment
import com.example.appfunctions.agent.domain.LlmResponsePart
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExecuteToolCallUseCaseTest {
    private val context: Context = mockk(relaxed = true)
    private val convertInputToAppFunctionDataUseCase: ConvertInputToAppFunctionDataUseCase = mockk()
    private val executeAppFunctionUseCase: ExecuteAppFunctionUseCase = mockk()
    private val agentInternalTools: AgentInternalTools = mockk(relaxed = true)

    private lateinit var useCase: ExecuteToolCallUseCase

    @Before
    fun setUp() {
        useCase =
            ExecuteToolCallUseCase(
                context = context,
                convertInputToAppFunctionDataUseCase = convertInputToAppFunctionDataUseCase,
                executeAppFunctionUseCase = executeAppFunctionUseCase,
                agentInternalTools = agentInternalTools,
            )
    }

    @Test
    fun `internal generateImage tool returns Success with extracted MessageAttachment`() =
        runTest {
            val imageResult =
                AgentInternalTools.GeneratedImageResult(
                    imageUri = "content://com.example.appfunctions.agent.fileprovider/cache/cat.png",
                    mimeType = "image/png",
                    prompt = "a cat",
                )
            coEvery { agentInternalTools.generateImage("a cat", "1:1") } returns imageResult

            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = AgentInternalTools.INTERNAL_TOOL_PACKAGE,
                    functionId = "generateImage",
                    arguments = mapOf("prompt" to "a cat", "aspectRatio" to "1:1"),
                    callId = "call-1",
                )

            val result = useCase(toolCall = toolCall, tools = emptyList(), threadId = "thread-1")

            assertTrue(result is ToolCallExecutionResult.Success)
            val success = result as ToolCallExecutionResult.Success
            assertEquals("call-1", success.toolOutput.callId)
            assertEquals("generateImage", success.toolOutput.functionId)
            assertEquals(
                MessageAttachment(uri = imageResult.imageUri, mimeType = "image/png"),
                success.attachment,
            )
        }

    @Test
    fun `missing tool metadata returns Failure`() =
        runTest {
            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = "nonexistent",
                    arguments = emptyMap(),
                    callId = "call-2",
                )

            val result = useCase(toolCall = toolCall, tools = emptyList(), threadId = "thread-1")

            assertTrue(result is ToolCallExecutionResult.Failure)
            assertEquals(
                "Tool not found: nonexistent",
                (result as ToolCallExecutionResult.Failure).errorMessage,
            )
        }

    @Test
    fun `AppFunction execution grants recursive URI permissions and returns Data output`() =
        runTest {
            val tool = createMockTool("com.example.notes", "attachImages")
            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(AppFunctionData.EMPTY)
            coEvery {
                executeAppFunctionUseCase(tool, any(), "thread-1")
            } returns ExecuteAppFunctionResult.Data(AppFunctionData.EMPTY, """{"ok":true}""")

            val nestedUri = "content://com.example.appfunctions.agent.fileprovider/cache/nested.jpg"
            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.notes",
                    functionId = "attachImages",
                    arguments =
                        mapOf(
                            "payload" to mapOf("uris" to listOf(nestedUri)),
                        ),
                    callId = "call-3",
                )

            val result = useCase(toolCall = toolCall, tools = listOf(tool), threadId = "thread-1")

            assertTrue(result is ToolCallExecutionResult.Success)
            assertEquals(
                """{"ok":true}""",
                (result as ToolCallExecutionResult.Success).toolOutput.result,
            )
            verify(exactly = 1) {
                context.grantUriPermission(
                    eq("com.example.notes"),
                    match { it.toString() == nestedUri },
                    eq(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }

    @Test
    fun `AppFunction PendingIntentAction returns PendingIntentRequired`() =
        runTest {
            val tool = createMockTool("com.example.payments", "sendPayment")
            val pendingIntent = mockk<PendingIntent>()
            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(AppFunctionData.EMPTY)
            coEvery {
                executeAppFunctionUseCase(tool, any(), "thread-1")
            } returns ExecuteAppFunctionResult.PendingIntentAction(pendingIntent)

            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.payments",
                    functionId = "sendPayment",
                    arguments = emptyMap(),
                    callId = "call-4",
                )

            val result = useCase(toolCall = toolCall, tools = listOf(tool), threadId = "thread-1")

            assertTrue(result is ToolCallExecutionResult.PendingIntentRequired)
            assertSame(
                pendingIntent,
                (result as ToolCallExecutionResult.PendingIntentRequired).pendingIntent,
            )
        }

    @Test
    fun `AppFunctionException returns formatted recoverable Success output while generic Exception returns Failure`() =
        runTest {
            val tool = createMockTool("com.example.calendar", "createEvent")
            coEvery {
                convertInputToAppFunctionDataUseCase(any(), any(), any())
            } returns Result.success(AppFunctionData.EMPTY)

            val appException = AppFunctionPermissionRequiredException("Calendar denied")
            coEvery {
                executeAppFunctionUseCase(tool, any(), "thread-1")
            } returns ExecuteAppFunctionResult.Error(appException)

            val toolCall =
                LlmResponsePart.ToolCall(
                    packageName = "com.example.calendar",
                    functionId = "createEvent",
                    arguments = emptyMap(),
                    callId = "call-5",
                )

            val recoverable =
                useCase(toolCall = toolCall, tools = listOf(tool), threadId = "thread-1")
            assertTrue(recoverable is ToolCallExecutionResult.Success)
            assertEquals(
                "Tool execution failed for createEvent: Error: AppFunctionPermissionRequiredException - Calendar denied",
                (recoverable as ToolCallExecutionResult.Success).toolOutput.result,
            )

            coEvery {
                executeAppFunctionUseCase(tool, any(), "thread-1")
            } returns ExecuteAppFunctionResult.Error(IllegalStateException("Binder died"))

            val fatal = useCase(toolCall = toolCall, tools = listOf(tool), threadId = "thread-1")
            assertTrue(fatal is ToolCallExecutionResult.Failure)
            assertEquals(
                "Tool execution failed for createEvent: Binder died",
                (fatal as ToolCallExecutionResult.Failure).errorMessage,
            )
        }

    private fun createMockTool(
        packageName: String,
        id: String,
    ): AppFunctionMetadata =
        mockk<AppFunctionMetadata>(relaxed = true).apply {
            every { this@apply.packageName } returns packageName
            every { this@apply.id } returns id
            every { parameters } returns emptyList()
        }
}
