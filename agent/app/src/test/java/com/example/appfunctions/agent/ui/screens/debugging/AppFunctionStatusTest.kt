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
package com.example.appfunctions.agent.ui.screens.debugging

import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFunctionStatusTest {
    @Test
    fun `from enabled global function can run`() {
        val status =
            AppFunctionStatus.from(
                createFunction(AppFunctionMetadata.SCOPE_GLOBAL),
                createState(isEnabled = true),
            )

        assertEquals(AppFunctionStatus(isEnabled = true, registeredActivityCount = null), status)
        assertFalse(status.isDynamic)
        assertTrue(status.canRun)
    }

    @Test
    fun `from disabled global function cannot run`() {
        val status =
            AppFunctionStatus.from(
                createFunction(AppFunctionMetadata.SCOPE_GLOBAL),
                createState(isEnabled = false),
            )

        assertEquals(AppFunctionStatus(isEnabled = false, registeredActivityCount = null), status)
        assertFalse(status.isDynamic)
        assertFalse(status.canRun)
    }

    @Test
    fun `from activity-scoped function is dynamic and cannot run`() {
        val status =
            AppFunctionStatus.from(
                createFunction(AppFunctionMetadata.SCOPE_ACTIVITY),
                createState(isEnabled = true),
            )

        // The registering activities can only be read on API 37+, so SDK 34 reports none.
        assertEquals(AppFunctionStatus(isEnabled = true, registeredActivityCount = 0), status)
        assertTrue(status.isDynamic)
        assertFalse(status.canRun)
    }

    @Test
    fun `dynamic function registered by activities cannot run`() {
        val status = AppFunctionStatus(isEnabled = true, registeredActivityCount = 2)

        assertTrue(status.isDynamic)
        assertFalse(status.canRun)
    }

    @Test
    fun `unregistered dynamic function cannot run`() {
        val status = AppFunctionStatus(isEnabled = false, registeredActivityCount = 0)

        assertTrue(status.isDynamic)
        assertFalse(status.canRun)
    }

    @Test
    fun `enabled global function can run`() {
        val status = AppFunctionStatus(isEnabled = true, registeredActivityCount = null)

        assertFalse(status.isDynamic)
        assertTrue(status.canRun)
    }

    private fun createFunction(scope: Int): AppFunctionMetadata =
        AppFunctionMetadata(
            name = AppFunctionName(PACKAGE_NAME, "test_function"),
            schema = null,
            parameters = emptyList(),
            response = AppFunctionResponseMetadata(valueType = AppFunctionStringTypeMetadata(isNullable = false)),
            packageMetadata =
                AppFunctionPackageMetadata(
                    packageName = PACKAGE_NAME,
                    components = AppFunctionComponentsMetadata(),
                ),
            scope = scope,
        )

    private fun createState(isEnabled: Boolean): AppFunctionState =
        mockk<AppFunctionState>().apply {
            every { this@apply.isEnabled } returns isEnabled
        }

    private companion object {
        const val PACKAGE_NAME = "com.example.app"
    }
}
