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
import android.net.Uri
import android.util.Log
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionOneOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParcelableTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Use case to recursively traverse [AppFunctionData] parameters and grant read URI permissions on
 * any `content://` URIs to the target AppFunction package.
 */
class GrantAppFunctionUriPermissionsUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Grants `FLAG_GRANT_READ_URI_PERMISSION` to [function]'s target package for all
         * `content://` URIs contained in [parameters].
         */
        operator fun invoke(
            function: AppFunctionMetadata,
            parameters: AppFunctionData,
        ) {
            for (parameter in function.parameters) {
                grantUriPermissionsForProperty(
                    targetPackageName = function.packageName,
                    data = parameters,
                    name = parameter.name,
                    dataType = parameter.dataType,
                    components = function.components,
                )
            }
        }

        private fun grantUriPermissionsForProperty(
            targetPackageName: String,
            data: AppFunctionData,
            name: String,
            dataType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionStringTypeMetadata -> {
                    val uriString = data.getString(name)
                    if (uriString != null) {
                        grantIfContentUri(targetPackageName, uriString)
                    }
                }
                is AppFunctionParcelableTypeMetadata -> {
                    if (dataType.qualifiedName == Uri::class.java.name) {
                        val uri = data.getParcelable(name, Uri::class.java)
                        if (uri != null) {
                            grantIfContentUri(targetPackageName, uri)
                        }
                    }
                }
                is AppFunctionObjectTypeMetadata -> {
                    val nested = data.getAppFunctionData(name) ?: return
                    grantUriPermissionsForObject(targetPackageName, nested, dataType, components)
                }
                is AppFunctionAllOfTypeMetadata,
                is AppFunctionOneOfTypeMetadata,
                -> {
                    val nested = data.getAppFunctionData(name) ?: return
                    grantUriPermissionsForDataType(targetPackageName, nested, dataType, components)
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[dataType.referenceDataType] ?: return
                    grantUriPermissionsForProperty(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        dataType = resolved,
                        components = components,
                    )
                }
                is AppFunctionArrayTypeMetadata -> {
                    grantUriPermissionsForArray(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        itemType = dataType.itemType,
                        components = components,
                    )
                }
            }
        }

        private fun grantUriPermissionsForObject(
            targetPackageName: String,
            data: AppFunctionData,
            objectType: AppFunctionObjectTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            if (objectType.qualifiedName == Uri::class.java.name && objectType.properties.isEmpty()) {
                val uriString = data.getString("uri")
                if (uriString != null) {
                    grantIfContentUri(targetPackageName, uriString)
                }
                return
            }
            for ((propName, propType) in objectType.properties) {
                grantUriPermissionsForProperty(
                    targetPackageName = targetPackageName,
                    data = data,
                    name = propName,
                    dataType = propType,
                    components = components,
                )
            }
        }

        private fun grantUriPermissionsForDataType(
            targetPackageName: String,
            data: AppFunctionData,
            dataType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (dataType) {
                is AppFunctionObjectTypeMetadata -> {
                    grantUriPermissionsForObject(targetPackageName, data, dataType, components)
                }
                is AppFunctionAllOfTypeMetadata -> {
                    for (subType in dataType.matchAll) {
                        grantUriPermissionsForDataType(
                            targetPackageName,
                            data,
                            subType,
                            components,
                        )
                    }
                }
                is AppFunctionOneOfTypeMetadata -> {
                    for (candidate in dataType.matchOneOf) {
                        grantUriPermissionsForDataType(
                            targetPackageName,
                            data,
                            candidate,
                            components,
                        )
                    }
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[dataType.referenceDataType] ?: return
                    grantUriPermissionsForDataType(targetPackageName, data, resolved, components)
                }
            }
        }

        private fun grantUriPermissionsForArray(
            targetPackageName: String,
            data: AppFunctionData,
            name: String,
            itemType: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (itemType) {
                is AppFunctionStringTypeMetadata -> {
                    val uriStrings = data.getStringList(name)
                    if (uriStrings != null) {
                        for (uriString in uriStrings) {
                            grantIfContentUri(targetPackageName, uriString)
                        }
                    }
                }
                is AppFunctionParcelableTypeMetadata -> {
                    if (itemType.qualifiedName == Uri::class.java.name) {
                        val uris = data.getParcelableList(name, Uri::class.java)
                        if (uris != null) {
                            for (uri in uris) {
                                grantIfContentUri(targetPackageName, uri)
                            }
                        }
                    }
                }
                is AppFunctionReferenceTypeMetadata -> {
                    val resolved = components.dataTypes[itemType.referenceDataType] ?: return
                    grantUriPermissionsForArray(
                        targetPackageName = targetPackageName,
                        data = data,
                        name = name,
                        itemType = resolved,
                        components = components,
                    )
                }
                is AppFunctionObjectTypeMetadata,
                is AppFunctionAllOfTypeMetadata,
                is AppFunctionOneOfTypeMetadata,
                -> {
                    val items = data.getAppFunctionDataList(name)
                    if (items != null) {
                        for (itemData in items) {
                            grantUriPermissionsForDataType(
                                targetPackageName,
                                itemData,
                                itemType,
                                components,
                            )
                        }
                    }
                }
            }
        }

        private fun grantIfContentUri(
            targetPackageName: String,
            uriString: String,
        ) {
            if (uriString.startsWith("content://")) {
                grantIfContentUri(targetPackageName, Uri.parse(uriString))
            }
        }

        private fun grantIfContentUri(
            targetPackageName: String,
            uri: Uri,
        ) {
            if (uri.scheme == "content") {
                try {
                    context.grantUriPermission(
                        targetPackageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to grant URI permission for $uri to $targetPackageName", e)
                }
            }
        }

        private companion object {
            const val TAG = "GrantAppFunctionUriPermissions"
        }
    }
