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
package com.example.appfunctions.agent

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.appfunctions.agent.domain.appfunction.ConvertAppFunctionDataToJsonUseCase
import com.example.appfunctions.agent.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import com.example.appfunctions.agent.domain.appfunction.ExecuteAppFunctionUseCase
import com.example.appfunctions.agent.domain.appfunction.GetAppFunctionsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureNanoTime

/**
 * CTS-style direct AppFunction performance benchmark running inside the :benchmark test module
 * against the target app's release build.
 *
 * Outputs formatted performance logs to both Android Studio Test Console (stdout)
 * and Android Logcat.
 */
@RunWith(AndroidJUnit4::class)
class AppFunctionPerformanceTest {

    private lateinit var context: Context
    private lateinit var appFunctionManager: AppFunctionManager
    private lateinit var getAppFunctionsUseCase: GetAppFunctionsUseCase
    private lateinit var convertAppFunctionDataToJsonUseCase: ConvertAppFunctionDataToJsonUseCase
    private lateinit var executeAppFunctionUseCase: ExecuteAppFunctionUseCase
    private var chatFunctions: List<AppFunctionMetadata> = emptyList()

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        // 1. Purge and set allowlist for privileged agent execution
        val packageName = context.packageName
        device.executeShellCommand("cmd app_function purge-allowlist-cache")
        device.executeShellCommand("cmd allowlist add-package-multimap 2 $packageName:2b2a355227c3fa4b666269bcdb2dbda5287142603927956e6cecae41a8e949ec '*'")

        // 2. Adopt shell permission identity within this single instrumentation process
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.EXECUTE_APP_FUNCTIONS")

        // 3. Initialize AppFunctionManager & UseCases
        appFunctionManager = AppFunctionManager.getInstance(context)!!
        getAppFunctionsUseCase = GetAppFunctionsUseCase(appFunctionManager)
        convertAppFunctionDataToJsonUseCase = ConvertAppFunctionDataToJsonUseCase()
        executeAppFunctionUseCase = ExecuteAppFunctionUseCase(appFunctionManager, convertAppFunctionDataToJsonUseCase)

        val appFunctionsMap = getAppFunctionsUseCase().first()
        val chatAppEntry = appFunctionsMap.entries.find { it.key.packageName == CHAT_APP_PACKAGE_NAME }
        chatFunctions = chatAppEntry?.value ?: emptyList()

        logToConsoleAndLogcat("AppFunctionPerformanceTest setup completed with ${chatFunctions.size} ChatApp functions loaded.")
    }

    @Test
    fun benchmarkSearchContactsDirect() = runBlocking {
        invokeFunctionRepeatedly(FUNCTION_ID_SEARCH_CONTACTS, mapOf("query" to "Alice", "contactType" to "INDIVIDUAL"))
    }

    @Test
    fun benchmarkSendMessageDirect() = runBlocking {
        invokeFunctionRepeatedly(FUNCTION_ID_SEND_MESSAGE, mapOf("endpointValue" to "1", "messageBody" to "CTS Benchmark Message"))
    }

    @Test
    fun benchmarkMakeCallDirect() = runBlocking {
        invokeFunctionRepeatedly(FUNCTION_ID_MAKE_CALL, mapOf("endpointValue" to "1"))
    }

    @Test
    fun benchmarkUpdateChatWallpaperDirect() = runBlocking {
        invokeFunctionRepeatedly(FUNCTION_ID_UPDATE_WALLPAPER, mapOf("chatId" to "1", "wallpaperUri" to createDummyWallpaperUri()))
    }

    @Test
    fun benchmarkSearchMessagesDirect() = runBlocking {
        invokeFunctionRepeatedly(FUNCTION_ID_SEARCH_MESSAGES, mapOf("query" to "Hello", "endpointValue" to "1"))
    }

    @Test
    fun benchmarkAllChatAppFunctionsDirect() = runBlocking {
        val functionsToRun = listOf(
            FUNCTION_ID_SEARCH_CONTACTS to mapOf("query" to "Alice", "contactType" to "INDIVIDUAL"),
            FUNCTION_ID_SEND_MESSAGE to mapOf("endpointValue" to "1", "messageBody" to "Benchmark message"),
            FUNCTION_ID_MAKE_CALL to mapOf("endpointValue" to "1"),
            FUNCTION_ID_UPDATE_WALLPAPER to mapOf("chatId" to "1", "wallpaperUri" to createDummyWallpaperUri()),
            FUNCTION_ID_SEARCH_MESSAGES to mapOf("query" to "Hello", "endpointValue" to "1"),
        )
        val totalDurationsMs = mutableListOf<Double>()
        for (i in 1..ITERATIONS) {
            val iterationNanos = measureNanoTime {
                for ((functionId, inputs) in functionsToRun) {
                    val metadata = chatFunctions.find { it.id.contains(functionId) }
                    if (metadata != null) {
                        invokeFunctionOnce(metadata, inputs)
                    }
                }
            }
            totalDurationsMs.add(iterationNanos / 1_000_000.0)
        }

        val avgMs = totalDurationsMs.average()
        val minMs = totalDurationsMs.minOrNull() ?: 0.0
        val maxMs = totalDurationsMs.maxOrNull() ?: 0.0
        val report = buildString {
            appendLine("========================================================================")
            appendLine("[BENCHMARK SUMMARY] Combined All ChatApp Functions (Release)")
            appendLine("  Iterations:       $ITERATIONS")
            appendLine("  Average Latency:  %.3f ms".format(avgMs))
            appendLine("  Min Latency:      %.3f ms".format(minMs))
            appendLine("  Max Latency:      %.3f ms".format(maxMs))
            appendLine("  Total Latency:    %.3f ms".format(totalDurationsMs.sum()))
            appendLine("========================================================================")
        }
        logToConsoleAndLogcat(report)
    }

    private suspend fun invokeFunctionRepeatedly(functionId: String, inputs: Map<String, Any>) {
        val metadata = chatFunctions.find { it.id.contains(functionId) } ?: run {
            logToConsoleAndLogcat("❌ Function $functionId not found in ChatApp metadata!")
            return
        }
        // Warmup iteration
        invokeFunctionOnce(metadata, inputs)

        val durationsMs = mutableListOf<Double>()
        for (i in 1..ITERATIONS) {
            val durationNanos = measureNanoTime {
                invokeFunctionOnce(metadata, inputs)
            }
            durationsMs.add(durationNanos / 1_000_000.0)
        }

        val avgMs = durationsMs.average()
        val minMs = durationsMs.minOrNull() ?: 0.0
        val maxMs = durationsMs.maxOrNull() ?: 0.0
        val report = buildString {
            appendLine("========================================================================")
            appendLine("[BENCHMARK RESULT] Function: $functionId")
            appendLine("  Iterations:       $ITERATIONS")
            appendLine("  Average Latency:  %.3f ms".format(avgMs))
            appendLine("  Min Latency:      %.3f ms".format(minMs))
            appendLine("  Max Latency:      %.3f ms".format(maxMs))
            appendLine("  Total Latency:    %.3f ms".format(durationsMs.sum()))
            appendLine("========================================================================")
        }
        logToConsoleAndLogcat(report)
    }

    private suspend fun invokeFunctionOnce(metadata: AppFunctionMetadata, inputs: Map<String, Any>) {
        val inputConverter = ConvertInputToAppFunctionDataUseCase()
        val dataResult = inputConverter(
            parameters = metadata.parameters,
            components = metadata.components,
            inputs = inputs,
        )
        val appFunctionData = dataResult.getOrThrow()
        executeAppFunctionUseCase(
            function = metadata,
            parameters = appFunctionData,
        )
    }

    private fun createDummyWallpaperUri(): String {
        val file = File(context.cacheDir, "benchmark_wallpaper.png")
        if (!file.exists()) {
            Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
                FileOutputStream(file).use { out ->
                    compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return contentUri.toString()
    }

    private fun logToConsoleAndLogcat(message: String) {
        println(message)
        Log.i(TAG, message)
    }

    companion object {
        private const val TAG = "AppFunctionPerformanceTest"
        private const val CHAT_APP_PACKAGE_NAME = "com.example.chatapp"
        private const val FUNCTION_ID_SEARCH_CONTACTS = "#searchContacts"
        private const val FUNCTION_ID_SEND_MESSAGE = "#sendMessage"
        private const val FUNCTION_ID_MAKE_CALL = "#makeCall"
        private const val FUNCTION_ID_UPDATE_WALLPAPER = "#updateChatWallpaper"
        private const val FUNCTION_ID_SEARCH_MESSAGES = "#searchMessages"
        private const val ITERATIONS = 10
    }
}
