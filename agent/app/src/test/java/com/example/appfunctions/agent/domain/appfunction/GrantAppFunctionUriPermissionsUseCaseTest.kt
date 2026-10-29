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

import android.content.Context
import android.content.Intent
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GrantAppFunctionUriPermissionsUseCaseTest {
    private val context = mockk<Context>(relaxed = true)
    private val useCase = GrantAppFunctionUriPermissionsUseCase(context)

    @Test
    fun `invoke grants recursive read URI permissions for content URIs in AppFunctionData`() {
        val uriObjectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("uri" to AppFunctionStringTypeMetadata(false)),
                required = listOf("uri"),
                qualifiedName = "android.net.Uri",
                isNullable = false,
            )
        val payloadObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "nestedUris" to
                            AppFunctionArrayTypeMetadata(
                                itemType = AppFunctionStringTypeMetadata(false),
                                isNullable = false,
                            ),
                    ),
                required = listOf("nestedUris"),
                qualifiedName = null,
                isNullable = false,
            )
        val parametersMeta =
            listOf(
                AppFunctionParameterMetadata(
                    name = "directUri",
                    isRequired = true,
                    dataType = AppFunctionStringTypeMetadata(false),
                ),
                AppFunctionParameterMetadata(
                    name = "uriObj",
                    isRequired = true,
                    dataType = uriObjectType,
                ),
                AppFunctionParameterMetadata(
                    name = "payload",
                    isRequired = true,
                    dataType = payloadObjectType,
                ),
                AppFunctionParameterMetadata(
                    name = "plainText",
                    isRequired = true,
                    dataType = AppFunctionStringTypeMetadata(false),
                ),
            )
        val components = AppFunctionComponentsMetadata()
        val function =
            AppFunctionMetadata(
                name = AppFunctionName("com.example.notes", "attachMedia"),
                schema = null,
                parameters = parametersMeta,
                response =
                    AppFunctionResponseMetadata(
                        valueType = AppFunctionStringTypeMetadata(false),
                        description = "",
                    ),
                description = "Attach media",
                deprecation = null,
                packageMetadata =
                    AppFunctionPackageMetadata(
                        packageName = "com.example.notes",
                        components = components,
                    ),
            )

        val directUri = "content://com.example.appfunctions.agent.fileprovider/cache/direct.png"
        val objectUri = "content://com.example.appfunctions.agent.fileprovider/cache/object.png"
        val nestedUri = "content://com.example.appfunctions.agent.fileprovider/cache/nested.jpg"

        val uriObjData =
            AppFunctionData.Builder(uriObjectType, components)
                .setString("uri", objectUri)
                .build()
        val payloadData =
            AppFunctionData.Builder(payloadObjectType, components)
                .setStringList("nestedUris", listOf(nestedUri, "https://example.com/ignore"))
                .build()
        val inputData =
            AppFunctionData.Builder(parametersMeta, components)
                .setString("directUri", directUri)
                .setAppFunctionData("uriObj", uriObjData)
                .setAppFunctionData("payload", payloadData)
                .setString("plainText", "hello world")
                .build()

        useCase(function, inputData)

        for (expectedUri in listOf(directUri, objectUri, nestedUri)) {
            verify(exactly = 1) {
                context.grantUriPermission(
                    eq("com.example.notes"),
                    match { it.toString() == expectedUri },
                    eq(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }
    }
}
