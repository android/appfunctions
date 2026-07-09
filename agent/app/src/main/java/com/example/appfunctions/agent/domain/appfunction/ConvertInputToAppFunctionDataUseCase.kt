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
         * @param inputs The map of raw inputs from the UI.
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
                    setParameterValue(builder, parameter.name, parameter.dataType, value, components)
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
                        is String -> if (value.isNotEmpty()) builder.setBoolean(name, value.toBoolean())
                    }
                }
                is AppFunctionDoubleTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setDouble(name, value.toDouble())
                        is String -> if (value.isNotEmpty()) builder.setDouble(name, value.toDouble())
                    }
                }
                is AppFunctionFloatTypeMetadata -> {
                    when (value) {
                        is Number -> builder.setFloat(name, value.toFloat())
                        is String -> if (value.isNotEmpty()) builder.setFloat(name, value.toFloat())
                    }
                }
                is AppFunctionObjectTypeMetadata -> {
                    if (dataType.qualifiedName == "android.net.Uri" && value is String) {
                        val uriPropertyName = dataType.properties.keys.firstOrNull() ?: "uri"
                        val uriData =
                            AppFunctionData.Builder(dataType, components)
                                .setString(uriPropertyName, value)
                                .build()
                        builder.setAppFunctionData(name, uriData)
                    } else {
                        val objData = convertObject(dataType, value as Map<String, Any>, components)
                        builder.setAppFunctionData(name, objData)
                    }
                }
                is AppFunctionArrayTypeMetadata -> {
                    setArrayValue(builder, name, dataType, value as List<Any>, components)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = dataType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        if (referenceKey == "android.net.Uri" && value is String) {
                            val uriPropertyName = objectType.properties.keys.firstOrNull() ?: "uri"
                            val uriData =
                                AppFunctionData.Builder(objectType, components)
                                    .setString(uriPropertyName, value)
                                    .build()
                            builder.setAppFunctionData(name, uriData)
                        } else {
                            val objData = convertObject(objectType, value as Map<String, Any>, components)
                            builder.setAppFunctionData(name, objData)
                        }
                    }
                }
            }
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
            values: List<Any>,
            components: AppFunctionComponentsMetadata,
        ) {
            when (val itemType = arrayType.itemType) {
                is AppFunctionStringTypeMetadata -> {
                    builder.setStringList(name, values.map { it as String })
                }
                is AppFunctionIntTypeMetadata -> {
                    val intArray = values.map { (it as String).toInt() }.toIntArray()
                    builder.setIntArray(name, intArray)
                }
                is AppFunctionLongTypeMetadata -> {
                    val longArray = values.map { (it as String).toLong() }.toLongArray()
                    builder.setLongArray(name, longArray)
                }
                is AppFunctionBooleanTypeMetadata -> {
                    val booleanArray = values.map { it as Boolean }.toBooleanArray()
                    builder.setBooleanArray(name, booleanArray)
                }
                is AppFunctionDoubleTypeMetadata -> {
                    val doubleArray = values.map { (it as String).toDouble() }.toDoubleArray()
                    builder.setDoubleArray(name, doubleArray)
                }
                is AppFunctionFloatTypeMetadata -> {
                    val floatArray = values.map { (it as String).toFloat() }.toFloatArray()
                    builder.setFloatArray(name, floatArray)
                }
                is AppFunctionObjectTypeMetadata -> {
                    val dataList =
                        values.map { convertObject(itemType, it as Map<String, Any>, components) }
                    builder.setAppFunctionDataList(name, dataList)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val referenceKey = itemType.referenceDataType
                    val objectType =
                        components.dataTypes[referenceKey] as? AppFunctionObjectTypeMetadata
                    if (objectType != null) {
                        val dataList =
                            values.map { convertObject(objectType, it as Map<String, Any>, components) }
                        builder.setAppFunctionDataList(name, dataList)
                    }
                }
            }
        }
    }
