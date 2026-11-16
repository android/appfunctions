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

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionUriGrant
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionOneOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.core.net.toUri
import com.example.appfunctions.agent.data.ResultImageException
import com.example.appfunctions.agent.data.ResultImageStore
import com.example.appfunctions.agent.domain.ToolOutputImage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * Use case to load the images a function returned, so that they can be shown in the chat and sent
 * to the model.
 *
 * Images come from every [AppFunctionUriGrant] with [Intent.FLAG_GRANT_READ_URI_PERMISSION] in the
 * return value, at any depth, and from a top-level `content://` `imageUri` field (with an optional
 * `mimeType`), the convention of the agent's `generateImage` tool. Only images are used, at most
 * [MAX_IMAGES] per result and [MAX_IMAGE_BYTES] each. Images that the agent doesn't store itself
 * are copied into its cache, so the chat keeps showing what the model saw after the grant ends or
 * the function's app changes the file.
 */
class LoadResultImagesUseCase
    @Inject
    constructor(
        private val resultImageStore: ResultImageStore,
    ) {
        /**
         * Loads the images in [result].
         *
         * @param function The metadata of the function that returned [result].
         * @param result The result of the function.
         * @return The loaded images, and the ones that couldn't be used with the reason why.
         */
        suspend operator fun invoke(
            function: AppFunctionMetadata,
            result: ExecuteAppFunctionResult.Data,
        ): ResultImages {
            val skipped = mutableListOf<SkippedResultImage>()
            val uriGrants =
                try {
                    findReadableUriGrants(function, result.data)
                } catch (e: IllegalArgumentException) {
                    // The result doesn't match the function's metadata.
                    Log.e(TAG, "Failed to find the URI grants returned by ${function.id}", e)
                    skipped += SkippedResultImage(uri = null, reason = "the result could not be read (${e.message})")
                    emptyList()
                }
            val candidates = (uriGrants + findImageUriField(result.formattedJson)).distinctBy { it.uri }

            val loaded = mutableListOf<ResultImage>()
            for (candidate in candidates) {
                if (loaded.size == MAX_IMAGES) {
                    skipped +=
                        SkippedResultImage(
                            uri = candidate.uri.toString(),
                            reason = "the limit of $MAX_IMAGES images per result was reached",
                        )
                    continue
                }
                try {
                    val image = resultImageStore.read(candidate.uri, candidate.mimeType, MAX_IMAGE_BYTES)
                    val displayUri =
                        if (resultImageStore.isAgentUri(candidate.uri)) {
                            candidate.uri
                        } else {
                            resultImageStore.saveSnapshot(image)
                        }
                    loaded += ResultImage(displayUri = displayUri.toString(), image = image)
                } catch (e: ResultImageException) {
                    Log.w(TAG, "Image ${candidate.uri} returned by ${function.id} not loaded: ${e.reason}", e)
                    skipped += SkippedResultImage(uri = candidate.uri.toString(), reason = e.reason)
                }
            }
            return ResultImages(loaded = loaded, skipped = skipped)
        }

        private fun findReadableUriGrants(
            function: AppFunctionMetadata,
            data: AppFunctionData,
        ): List<ImageCandidate> =
            buildList {
                addUriGrantsOfProperty(
                    data = data,
                    name = PROPERTY_RETURN_VALUE,
                    type = function.response.valueType,
                    components = function.components,
                )
            }

        private fun MutableList<ImageCandidate>.addUriGrantsOfProperty(
            data: AppFunctionData,
            name: String,
            type: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            when (val resolvedType = type.resolve(components)) {
                is AppFunctionObjectTypeMetadata,
                is AppFunctionAllOfTypeMetadata,
                is AppFunctionOneOfTypeMetadata,
                -> {
                    val nested = data.getAppFunctionData(name) ?: return
                    addUriGrantsOfObject(nested, resolvedType, components)
                }
                is AppFunctionArrayTypeMetadata -> {
                    val itemType = resolvedType.itemType.resolve(components)
                    if (itemType.isObjectType()) {
                        data.getAppFunctionDataList(name)?.forEach { item ->
                            addUriGrantsOfObject(item, itemType, components)
                        }
                    }
                }
                else -> Unit
            }
        }

        private fun MutableList<ImageCandidate>.addUriGrantsOfObject(
            data: AppFunctionData,
            type: AppFunctionDataTypeMetadata,
            components: AppFunctionComponentsMetadata,
        ) {
            if (data.qualifiedName == URI_GRANT_QUALIFIED_NAME) {
                val uriGrant = data.deserialize(AppFunctionUriGrant::class.java)
                if (uriGrant.modeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    add(ImageCandidate(uri = uriGrant.uri, mimeType = null))
                }
                return
            }
            when (type) {
                is AppFunctionObjectTypeMetadata -> {
                    for ((propertyName, propertyType) in type.properties) {
                        addUriGrantsOfProperty(data, propertyName, propertyType, components)
                    }
                }
                is AppFunctionAllOfTypeMetadata -> {
                    for (subType in type.matchAll) {
                        addUriGrantsOfObject(data, subType.resolve(components), components)
                    }
                }
                is AppFunctionOneOfTypeMetadata -> {
                    val matchingType =
                        type.matchOneOf
                            .map { it.resolve(components) }
                            .find { it.qualifiedNameOrNull() == data.qualifiedName }
                    if (matchingType != null) {
                        addUriGrantsOfObject(data, matchingType, components)
                    }
                }
                else -> Unit
            }
        }

        private fun findImageUriField(formattedJson: String): List<ImageCandidate> {
            val json =
                try {
                    Json.parseToJsonElement(formattedJson) as? JsonObject
                } catch (_: SerializationException) {
                    // A result that isn't JSON, e.g. a plain string, has no imageUri field.
                    null
                } ?: return emptyList()
            val uri = (json[IMAGE_URI_FIELD] as? JsonPrimitive)?.contentOrNull?.toUri()
            if (uri?.scheme != ContentResolver.SCHEME_CONTENT) {
                return emptyList()
            }
            val mimeType = (json[MIME_TYPE_FIELD] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            return listOf(ImageCandidate(uri = uri, mimeType = mimeType))
        }

        private fun AppFunctionDataTypeMetadata.resolve(components: AppFunctionComponentsMetadata): AppFunctionDataTypeMetadata =
            if (this is AppFunctionReferenceTypeMetadata) {
                components.dataTypes[referenceDataType] ?: this
            } else {
                this
            }

        private fun AppFunctionDataTypeMetadata.isObjectType(): Boolean =
            this is AppFunctionObjectTypeMetadata ||
                this is AppFunctionAllOfTypeMetadata ||
                this is AppFunctionOneOfTypeMetadata

        private fun AppFunctionDataTypeMetadata.qualifiedNameOrNull(): String? =
            when (this) {
                is AppFunctionObjectTypeMetadata -> qualifiedName
                is AppFunctionAllOfTypeMetadata -> qualifiedName
                is AppFunctionOneOfTypeMetadata -> qualifiedName
                else -> null
            }

        /** An image URI found in a result, with the MIME type the result declared for it, if any. */
        private data class ImageCandidate(
            val uri: Uri,
            val mimeType: String?,
        )

        companion object {
            /** The maximum number of images loaded from one function result. */
            const val MAX_IMAGES = 3

            /** The size limit of a loaded image, in bytes. */
            const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

            private const val TAG = "LoadResultImagesUseCase"
            private const val IMAGE_URI_FIELD = "imageUri"
            private const val MIME_TYPE_FIELD = "mimeType"
            private val URI_GRANT_QUALIFIED_NAME = AppFunctionUriGrant::class.java.name
        }
    }

/**
 * The images of a function result.
 *
 * @property loaded The images that were loaded.
 * @property skipped The images that couldn't be used.
 */
data class ResultImages(
    val loaded: List<ResultImage>,
    val skipped: List<SkippedResultImage>,
)

/**
 * An image loaded from a function result.
 *
 * @property displayUri A `content://` URI of the agent's storage to show the image with.
 * @property image The image itself, to send to the model.
 */
data class ResultImage(
    val displayUri: String,
    val image: ToolOutputImage,
)

/**
 * An image of a function result that couldn't be used.
 *
 * @property uri The URI the function returned for the image, or null when it isn't known.
 * @property reason Why, phrased to complete "The image was not attached because …".
 */
data class SkippedResultImage(
    val uri: String?,
    val reason: String,
)
