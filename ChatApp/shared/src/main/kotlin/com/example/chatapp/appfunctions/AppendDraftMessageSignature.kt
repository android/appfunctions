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
package com.example.chatapp.appfunctions

import androidx.appfunctions.AppFunctionSignature
import androidx.appfunctions.ExperimentalAppFunctionsApi
import androidx.appfunctions.metadata.AppFunctionMetadata

@OptIn(ExperimentalAppFunctionsApi::class)
@AppFunctionSignature(
    scope = AppFunctionMetadata.SCOPE_ACTIVITY,
    appFunctionXmlFileName = "chat_activity_app_functions",
    isDescribedByKDoc = true,
)
fun interface AppendDraftMessageSignature {
    /** Appends or prefills text content into the currently active chat input bar. */
    suspend fun appendDraftMessage(messageText: String): Boolean
}
