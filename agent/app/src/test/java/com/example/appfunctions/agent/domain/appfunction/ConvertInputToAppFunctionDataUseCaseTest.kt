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

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConvertInputToAppFunctionDataUseCaseTest {
    private val useCase = ConvertInputToAppFunctionDataUseCase()
    private val components = AppFunctionComponentsMetadata(emptyMap())

    @Test
    fun invoke_allPrimitiveTypes_convertsCorrectly() {
        val parameters =
            listOf(
                AppFunctionParameterMetadata("str", true, AppFunctionStringTypeMetadata(false)),
                AppFunctionParameterMetadata("int", true, AppFunctionIntTypeMetadata(false)),
                AppFunctionParameterMetadata("long", true, AppFunctionLongTypeMetadata(false)),
                AppFunctionParameterMetadata("bool", true, AppFunctionBooleanTypeMetadata(false)),
                AppFunctionParameterMetadata("double", true, AppFunctionDoubleTypeMetadata(false)),
                AppFunctionParameterMetadata("float", true, AppFunctionFloatTypeMetadata(false)),
            )
        val inputs =
            mapOf(
                "str" to "value",
                "int" to "123",
                "long" to "456",
                "bool" to true,
                "double" to "1.23",
                "float" to "4.56",
            )

        val result = useCase(parameters, components, inputs).getOrThrow()

        assertEquals("value", result.getString("str"))
        assertEquals(123, result.getInt("int"))
        assertEquals(456L, result.getLong("long"))
        assertEquals(true, result.getBoolean("bool"))
        assertEquals(1.23, result.getDouble("double"), 0.001)
        assertEquals(4.56f, result.getFloat("float"), 0.001f)
    }

    @Test
    fun invoke_allPrimitiveArrays_convertsCorrectly() {
        val parameters =
            listOf(
                AppFunctionParameterMetadata(
                    "strArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionStringTypeMetadata(false), false),
                ),
                AppFunctionParameterMetadata(
                    "intArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionIntTypeMetadata(false), false),
                ),
                AppFunctionParameterMetadata(
                    "longArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionLongTypeMetadata(false), false),
                ),
                AppFunctionParameterMetadata(
                    "boolArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionBooleanTypeMetadata(false), false),
                ),
                AppFunctionParameterMetadata(
                    "doubleArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionDoubleTypeMetadata(false), false),
                ),
                AppFunctionParameterMetadata(
                    "floatArray",
                    true,
                    AppFunctionArrayTypeMetadata(AppFunctionFloatTypeMetadata(false), false),
                ),
            )
        val inputs =
            mapOf(
                "strArray" to listOf("v1", "v2"),
                "intArray" to listOf("1", "2"),
                "longArray" to listOf("3", "4"),
                "boolArray" to listOf(true, false),
                "doubleArray" to listOf("1.1", "2.2"),
                "floatArray" to listOf("3.3", "4.4"),
            )

        val result = useCase(parameters, components, inputs).getOrThrow()

        assertEquals(listOf("v1", "v2"), result.getStringList("strArray"))
        assertEquals(listOf(1, 2), result.getIntArray("intArray")?.toList())
        assertEquals(listOf(3L, 4L), result.getLongArray("longArray")?.toList())
        assertEquals(listOf(true, false), result.getBooleanArray("boolArray")?.toList())
        assertEquals(listOf(1.1, 2.2), result.getDoubleArray("doubleArray")?.toList())
        assertEquals(listOf(3.3f, 4.4f), result.getFloatArray("floatArray")?.toList())
    }

    @Test
    fun invoke_objectParameter_convertsCorrectly() {
        val objectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "prop1" to AppFunctionStringTypeMetadata(false),
                        "prop2" to AppFunctionIntTypeMetadata(false),
                    ),
                required = listOf("prop1", "prop2"),
                qualifiedName = null,
                isNullable = false,
            )
        val parameters =
            listOf(
                AppFunctionParameterMetadata("obj", true, objectType),
            )
        val inputs =
            mapOf(
                "obj" to mapOf("prop1" to "val1", "prop2" to "123"),
            )

        val result = useCase(parameters, components, inputs).getOrThrow()

        val objResult = result.getAppFunctionData("obj")
        assertEquals("val1", objResult?.getString("prop1"))
        assertEquals(123, objResult?.getInt("prop2"))
    }

    @Test
    fun invoke_referenceParameter_convertsCorrectly() {
        val objectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "prop1" to AppFunctionStringTypeMetadata(false),
                    ),
                required = listOf("prop1"),
                qualifiedName = null,
                isNullable = false,
            )
        val componentsWithTypes =
            AppFunctionComponentsMetadata(
                dataTypes = mapOf("RefType" to objectType),
            )
        val parameters =
            listOf(
                AppFunctionParameterMetadata(
                    "ref",
                    true,
                    AppFunctionReferenceTypeMetadata("RefType", false),
                ),
            )
        val inputs =
            mapOf(
                "ref" to mapOf("prop1" to "val1"),
            )

        val result = useCase(parameters, componentsWithTypes, inputs).getOrThrow()

        val refResult = result.getAppFunctionData("ref")
        assertEquals("val1", refResult?.getString("prop1"))
    }

    @Test
    fun invoke_emptyStringNumericParameters_areSkipped() {
        val parameters =
            listOf(
                AppFunctionParameterMetadata("int", false, AppFunctionIntTypeMetadata(false)),
                AppFunctionParameterMetadata("long", false, AppFunctionLongTypeMetadata(false)),
                AppFunctionParameterMetadata("double", false, AppFunctionDoubleTypeMetadata(false)),
                AppFunctionParameterMetadata("float", false, AppFunctionFloatTypeMetadata(false)),
            )
        val inputs =
            mapOf(
                "int" to "",
                "long" to "",
                "double" to "",
                "float" to "",
            )

        val result = useCase(parameters, components, inputs)

        // Verify that it doesn't throw NumberFormatException during conversion
        assertEquals(true, result.isSuccess)
    }

    @Test
    fun invoke_invalidNumericInputs_returnsFailure() {
        assertFailureForInput(AppFunctionIntTypeMetadata(false), "not_an_int")
        assertFailureForInput(AppFunctionLongTypeMetadata(false), "not_a_long")
        assertFailureForInput(AppFunctionDoubleTypeMetadata(false), "not_a_double")
        assertFailureForInput(AppFunctionFloatTypeMetadata(false), "not_a_float")
    }

    private fun assertFailureForInput(
        dataType: AppFunctionDataTypeMetadata,
        value: Any,
    ) {
        val parameters =
            listOf(
                AppFunctionParameterMetadata(
                    name = "param1",
                    isRequired = true,
                    dataType = dataType,
                ),
            )
        val inputs = mapOf("param1" to value)

        val result = useCase(parameters, components, inputs)

        assertEquals(true, result.isFailure)
    }

    @Test
    fun convert_uriObjectTypeWithStringInput_buildsUriAppFunctionData() {
        val uriObjectType =
            AppFunctionObjectTypeMetadata(
                properties =
                    mapOf(
                        "uri" to AppFunctionStringTypeMetadata(false),
                    ),
                required = listOf("uri"),
                qualifiedName = "android.net.Uri",
                isNullable = false,
            )
        val parameters =
            listOf(
                AppFunctionParameterMetadata(
                    name = "wallpaperUri",
                    isRequired = true,
                    dataType = uriObjectType,
                ),
            )
        val inputs = mapOf("wallpaperUri" to "content://com.example/file.jpg")

        val result = useCase(parameters, components, inputs).getOrThrow()

        val uriData = result.getAppFunctionData("wallpaperUri")
        assertEquals("content://com.example/file.jpg", uriData?.getString("uri"))
    }
}
