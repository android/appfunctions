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
import org.junit.Assert.assertTrue
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
                AppFunctionParameterMetadata(
                    "double",
                    true,
                    AppFunctionDoubleTypeMetadata(false),
                ),
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
                "intArray" to listOf("1", 2),
                "longArray" to listOf("3", 4L),
                "boolArray" to listOf(true, false),
                "doubleArray" to listOf("1.1", 2.2),
                "floatArray" to listOf("3.3", 4.4f),
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
    fun invoke_stringArrayWithNumericElements_stringifiesElements() {
        val parameters = listOf(arrayParameter("ids", AppFunctionStringTypeMetadata(false)))
        val inputs = mapOf("ids" to listOf(1L, 2.5, "three"))

        val result = useCase(parameters, components, inputs).getOrThrow()

        assertEquals(listOf("1", "2.5", "three"), result.getStringList("ids"))
    }

    @Test
    fun invoke_numericArraysWithMixedNumbersAndNumericStrings_convertsElements() {
        val parameters =
            listOf(
                arrayParameter("intArray", AppFunctionIntTypeMetadata(false)),
                arrayParameter("longArray", AppFunctionLongTypeMetadata(false)),
                arrayParameter("doubleArray", AppFunctionDoubleTypeMetadata(false)),
                arrayParameter("floatArray", AppFunctionFloatTypeMetadata(false)),
            )
        val inputs =
            mapOf(
                "intArray" to listOf(1L, 2.0, "3"),
                "longArray" to listOf(4L, 5.0, "6"),
                "doubleArray" to listOf(7L, 8.5, "9.5"),
                "floatArray" to listOf(1L, 2.5, "3.5"),
            )

        val result = useCase(parameters, components, inputs).getOrThrow()

        assertEquals(listOf(1, 2, 3), result.getIntArray("intArray")?.toList())
        assertEquals(listOf(4L, 5L, 6L), result.getLongArray("longArray")?.toList())
        assertEquals(listOf(7.0, 8.5, 9.5), result.getDoubleArray("doubleArray")?.toList())
        assertEquals(listOf(1.0f, 2.5f, 3.5f), result.getFloatArray("floatArray")?.toList())
    }

    @Test
    fun invoke_booleanArrayWithBooleanStrings_convertsElements() {
        val parameters = listOf(arrayParameter("flags", AppFunctionBooleanTypeMetadata(false)))
        val inputs = mapOf("flags" to listOf("true", "false", "TRUE", false))

        val result = useCase(parameters, components, inputs).getOrThrow()

        assertEquals(listOf(true, false, true, false), result.getBooleanArray("flags")?.toList())
    }

    @Test
    fun invoke_intArrayWithNonNumericString_returnsFailureNamingParameter() {
        assertArrayFailure(
            itemType = AppFunctionIntTypeMetadata(false),
            value = listOf(1L, "abc"),
            expectedMessage = "Parameter 'items' expects an array of int, but element 1 is 'abc'",
        )
    }

    @Test
    fun invoke_booleanArrayWithNonBooleanString_returnsFailureNamingParameter() {
        assertArrayFailure(
            itemType = AppFunctionBooleanTypeMetadata(false),
            value = listOf("maybe"),
            expectedMessage =
                "Parameter 'items' expects an array of boolean, but element 0 is 'maybe'",
        )
    }

    @Test
    fun invoke_arrayWithNullElement_returnsFailureNamingParameter() {
        assertArrayFailure(
            itemType = AppFunctionStringTypeMetadata(false),
            value = listOf("a", null),
            expectedMessage =
                "Parameter 'items' expects an array of string, but element 1 is 'null'",
        )
    }

    @Test
    fun invoke_nonListValueForArrayParameter_returnsFailureNamingParameter() {
        assertArrayFailure(
            itemType = AppFunctionStringTypeMetadata(false),
            value = "not_a_list",
            expectedMessage = "Parameter 'items' expects an array but got 'not_a_list'",
        )
    }

    private fun arrayParameter(
        name: String,
        itemType: AppFunctionDataTypeMetadata,
    ) = AppFunctionParameterMetadata(name, true, AppFunctionArrayTypeMetadata(itemType, false))

    private fun assertArrayFailure(
        itemType: AppFunctionDataTypeMetadata,
        value: Any,
        expectedMessage: String,
    ) {
        val parameters = listOf(arrayParameter("items", itemType))
        val inputs = mapOf("items" to value)

        val exception = useCase(parameters, components, inputs).exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertEquals(expectedMessage, exception?.message)
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
                AppFunctionParameterMetadata(
                    "double",
                    false,
                    AppFunctionDoubleTypeMetadata(false),
                ),
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

    @Test
    fun convert_uriObjectTypeArrayWithStringInput_buildsUriObjectTypeArray() {
        val uriObjectType =
            AppFunctionObjectTypeMetadata(
                properties = mapOf("uri" to AppFunctionStringTypeMetadata(false)),
                required = listOf("uri"),
                qualifiedName = "android.net.Uri",
                isNullable = false,
            )
        val arrayType =
            AppFunctionArrayTypeMetadata(
                itemType = uriObjectType,
                isNullable = false,
            )
        val parameters =
            listOf(
                AppFunctionParameterMetadata(
                    name = "attachments",
                    isRequired = true,
                    dataType = arrayType,
                ),
            )
        val inputs = mapOf("attachments" to listOf("content://com.example/file.jpg"))

        val result = useCase(parameters, components, inputs).getOrThrow()

        val dataList = result.getAppFunctionDataList("attachments")
        assertEquals(1, dataList?.size)
        val uriData = dataList?.first()
        assertEquals("content://com.example/file.jpg", uriData?.getString("uri"))
    }
}
