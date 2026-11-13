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

import androidx.appfunctions.AppFunctionData
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
import javax.inject.Inject

/** Use case to convert a map of raw inputs to type-safe [AppFunctionData]. */
class ConvertInputToAppFunctionDataUseCase
    @Inject
    constructor() {
        /**
         * Executes the use case.
         *
         * @param parameters The list of parameter metadata for the function.
         * @param components The component metadata for resolving references.
         * @param inputs The map of raw inputs from the UI or LLM tool call.
         * @return The converted [AppFunctionData].
         */
        operator fun invoke(
            parameters: List<AppFunctionParameterMetadata>,
            components: AppFunctionComponentsMetadata,
            inputs: Map<String, Any>,
        ): Result<AppFunctionData> =
            runCatching {
                val builder = AppFunctionData.Builder(parameters, components)
                for (parameter in parameters) {
                    val value = inputs[parameter.name] ?: continue
                    setParameterValue(
                        builder,
                        parameter.name,
                        parameter.dataType,
                        value,
                        components,
                    )
                }
                builder.build()
            }

        private fun setParameterValue(
            builder: AppFunctionData.Builder,
            name: String,
            dataType: AppFunctionDataTypeMetadata,
            value: Any,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionStringTypeMetadata -> {
                    builder.setString(name, value.toString())
                }
                is AppFunctionIntTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setInt(name, value.toInt())
                        is String -> if (value.isNotEmpty()) builder.setInt(name, value.toInt())
                    }
                }
                is AppFunctionLongTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setLong(name, value.toLong())
                        is String -> if (value.isNotEmpty()) builder.setLong(name, value.toLong())
                    }
                }
                is AppFunctionBooleanTypeMetadata -> {
                    when (value) {
                        is Boolean -> builder.setBoolean(name, value)
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setBoolean(name, value.toBoolean())
                            }
                    }
                }
                is AppFunctionDoubleTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setDouble(name, value.toDouble())
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setDouble(name, value.toDouble())
                            }
                    }
                }
                is AppFunctionFloatTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setFloat(name, value.toFloat())
                        is String ->
                            if (value.isNotEmpty()) {
                                builder.setFloat(name, value.toFloat())
                            }
                    }
                }
                is AppFunctionObjectTypeMetadata -> {
                    val objData = toAppFunctionData(dataType, value, components)
                    builder.setAppFunctionData(name, objData)
                }
                is AppFunctionArrayTypeMetadata -> {
                    val values =
                        value as? List<*>
                            ?: throw IllegalArgumentException(
                                "Parameter '$name' expects an array but got '$value'",
                            )
                    setArrayValue(builder, name, dataType, values, components)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = dataType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        val objData = toAppFunctionData(objectType, value, components)
                        builder.setAppFunctionData(name, objData)
                    }
                }
            }
        }

        private fun toAppFunctionData(
            objectType: AppFunctionObjectTypeMetadata,
            value: Any,
            components: AppFunctionComponentsMetadata,
        ): AppFunctionData {
            if (objectType.qualifiedName == "android.net.Uri" && value is String) {
                val uriPropertyName = objectType.properties.keys.firstOrNull() ?: "uri"
                return AppFunctionData.Builder(objectType, components)
                    .setString(uriPropertyName, value)
                    .build()
            }
            return convertObject(objectType, value as Map<String, Any>, components)
        }

        private fun convertObject(
            objectType: AppFunctionObjectTypeMetadata,
            values: Map<String, Any>,
            components: AppFunctionComponentsMetadata,
        ): AppFunctionData {
            val builder = AppFunctionData.Builder(objectType, components)
            for ((propName, propType) in objectType.properties) {
                val value = values[propName] ?: continue
                setParameterValue(builder, propName, propType, value, components)
            }
            return builder.build()
        }

        private fun setArrayValue(
            builder: AppFunctionData.Builder,
            name: String,
            arrayType: AppFunctionArrayTypeMetadata,
            values: List<*>,
            components: AppFunctionComponentsMetadata,
        ) {
            when (val itemType = arrayType.itemType) {
                is AppFunctionStringTypeMetadata -> {
                    val stringList = values.convertElements(name, "string") { it.toString() }
                    builder.setStringList(name, stringList)
                }
                is AppFunctionIntTypeMetadata -> {
                    val intArray =
                        values.convertElements(name, "int") {
                            when (it) {
                                is Number -> it.toInt()
                                is String -> it.toIntOrNull()
                                else -> null
                            }
                        }
                    builder.setIntArray(name, intArray.toIntArray())
                }
                is AppFunctionLongTypeMetadata -> {
                    val longArray =
                        values.convertElements(name, "long") {
                            when (it) {
                                is Number -> it.toLong()
                                is String -> it.toLongOrNull()
                                else -> null
                            }
                        }
                    builder.setLongArray(name, longArray.toLongArray())
                }
                is AppFunctionBooleanTypeMetadata -> {
                    val booleanArray =
                        values.convertElements(name, "boolean") {
                            when (it) {
                                is Boolean -> it
                                is String -> it.lowercase().toBooleanStrictOrNull()
                                else -> null
                            }
                        }
                    builder.setBooleanArray(name, booleanArray.toBooleanArray())
                }
                is AppFunctionDoubleTypeMetadata -> {
                    val doubleArray =
                        values.convertElements(name, "double") {
                            when (it) {
                                is Number -> it.toDouble()
                                is String -> it.toDoubleOrNull()
                                else -> null
                            }
                        }
                    builder.setDoubleArray(name, doubleArray.toDoubleArray())
                }
                is AppFunctionFloatTypeMetadata -> {
                    val floatArray =
                        values.convertElements(name, "float") {
                            when (it) {
                                is Number -> it.toFloat()
                                is String -> it.toFloatOrNull()
                                else -> null
                            }
                        }
                    builder.setFloatArray(name, floatArray.toFloatArray())
                }
                is AppFunctionObjectTypeMetadata -> {
                    val dataList =
                        values.convertElements(name, "object") { item ->
                            toAppFunctionData(itemType, item, components)
                        }
                    builder.setAppFunctionDataList(name, dataList)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = itemType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        val dataList =
                            values.convertElements(name, "object") { item ->
                                toAppFunctionData(objectType, item, components)
                            }
                        builder.setAppFunctionDataList(name, dataList)
                    }
                }
            }
        }

        /**
         * Converts each element with [convert], which returns null for an element it cannot
         * convert. LLMs may encode array elements as numbers, strings or booleans regardless of
         * the declared item type, so callers accept every lossless representation.
         *
         * @throws IllegalArgumentException naming the parameter, index and value of the first
         *   element that is null or cannot be converted.
         */
        private inline fun <T> List<*>.convertElements(
            name: String,
            expectedType: String,
            convert: (Any) -> T?,
        ): List<T> =
            mapIndexed { index, item ->
                item?.let(convert)
                    ?: throw IllegalArgumentException(
                        "Parameter '$name' expects an array of $expectedType, " +
                            "but element $index is '$item'",
                    )
            }
    }
