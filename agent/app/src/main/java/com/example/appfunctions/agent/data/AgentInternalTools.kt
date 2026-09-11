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
package com.example.appfunctions.agent.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionState
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionName
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.appfunctions.agent.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.emptyList
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
@RequiresApi(36)
class AgentInternalTools
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsDataStore: DataStore<Preferences>,
    ) {
        /**
         * Geocode a physical address string into its latitude and longitude coordinates.
         *
         * @param address The physical address to geocode (e.g., "1600 Amphitheatre Pkwy, Mountain View,
         *   CA").
         * @return The latitude and longitude coordinates of the address, or null if geocoding fails.
         */
        suspend fun geocodeAddress(address: String): LatLng? {
            if (!Geocoder.isPresent()) {
                return null
            }

            val geocoder = Geocoder(context)

            return withContext(Dispatchers.IO) {
                try {
                    suspendCoroutine { continuation ->
                        geocoder.getFromLocationName(
                            address,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    val location = addresses.firstOrNull()
                                    if (location != null) {
                                        continuation.resume(
                                            LatLng(location.latitude, location.longitude),
                                        )
                                    } else {
                                        continuation.resume(null)
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    continuation.resume(null)
                                }
                            },
                        )
                    }
                } catch (e: Exception) {
                    throw IllegalStateException(e.message, e)
                }
            }
        }

        /**
         * Retrieve the current latitude and longitude coordinates of the device.
         *
         * @return The current location coordinates of the device, or null if location is unavailable or
         *   permission is denied.
         */
        @SuppressLint("MissingPermission")
        suspend fun getCurrentLocation(): LatLng? =
            withContext(Dispatchers.Default) {
                // Check permissions
                val hasFineLocation =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED

                val hasCoarseLocation =
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED

                if (!hasFineLocation && !hasCoarseLocation) {
                    throw IllegalStateException("Location permission is not granted")
                }

                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                try {
                    // Try GPS Provider first
                    var location =
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        } else {
                            null
                        }

                    // Fallback to Network Provider if GPS is not available
                    if (location == null &&
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ) {
                        location =
                            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }

                    if (location != null) {
                        LatLng(location.latitude, location.longitude)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    throw IllegalStateException(e.message, e)
                }
            }

        /**
         * Generates an image from a text prompt and returns the remote image URI.
         *
         * @param prompt The text prompt describing the image to generate (e.g., "futuristic cityscape at sunset").
         * @param aspectRatio Optional aspect ratio for the image (e.g., "16:9", "1:1").
         * @return A GeneratedImageResult containing the generated remote image URI.
         */
        suspend fun generateImage(
            prompt: String,
            aspectRatio: String? = null,
        ): GeneratedImageResult =
            withContext(Dispatchers.IO) {
                val apiKey = getOrFetchApiKey()
                val requestPayload = buildImageGenerationPayload(prompt, aspectRatio)
                val responseText = executeImageRequest(apiKey, requestPayload)
                saveBase64ImageToCache(responseText, prompt)
            }

        private suspend fun getOrFetchApiKey(): String {
            val apiKey =
                settingsDataStore.data
                    .first()[stringPreferencesKey("gemini_api_key")]
                    ?.takeIf { it.isNotBlank() }
                    ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
            if (apiKey.isNullOrBlank()) {
                throw IllegalStateException(
                    "Gemini API key is not configured. Please set gemini_api_key in settings.",
                )
            }
            return apiKey
        }

        private fun buildImageGenerationPayload(
            prompt: String,
            aspectRatio: String?,
        ): JSONObject =
            JSONObject().apply {
                put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(JSONObject().apply { put("text", prompt) })
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("IMAGE") })
                        if (!aspectRatio.isNullOrBlank()) {
                            put(
                                "imageConfig",
                                JSONObject().apply {
                                    put("aspectRatio", aspectRatio)
                                },
                            )
                        }
                    },
                )
            }

        private fun executeImageRequest(
            apiKey: String,
            requestJson: JSONObject,
        ): String {
            val endpointUrl =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=$apiKey"
            val url = URL(endpointUrl)
            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 60000
                }

            try {
                connection.outputStream.use { os ->
                    os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody =
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                            ?: "HTTP $responseCode"
                    throw IllegalStateException(
                        "Image generation failed ($responseCode): $errorBody",
                    )
                }

                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

        private fun saveBase64ImageToCache(
            responseText: String,
            prompt: String,
        ): GeneratedImageResult {
            val responseJson = JSONObject(responseText)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException(
                    "No candidates returned from Gemini image generation API",
                )
            }

            val parts =
                candidates
                    .getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                throw IllegalStateException(
                    "No parts returned in candidate content. Gemini response: $responseText",
                )
            }

            val candidateData =
                (0 until parts.length())
                    .asSequence()
                    .mapNotNull { i ->
                        val part = parts.getJSONObject(i)
                        val inlineData =
                            part.optJSONObject("inlineData")
                                ?: part.optJSONObject("inline_data")
                        if (inlineData != null) {
                            val base64 = inlineData.optString("data")
                            val returnedMime =
                                inlineData
                                    .optString("mimeType")
                                    .takeIf { it.isNotBlank() }
                                    ?: inlineData.optString("mime_type")
                                        .takeIf { it.isNotBlank() }
                                    ?: "image/png"
                            base64 to returnedMime
                        } else {
                            null
                        }
                    }
                    .firstOrNull()

            if (candidateData == null || candidateData.first.isBlank()) {
                throw IllegalStateException(
                    "No inlineData image found in response parts. Gemini response: $responseText",
                )
            }

            val (base64Data, mimeType) = candidateData
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val extension =
                when {
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                    else -> "png"
                }
            val cachedFile =
                File(
                    context.cacheDir,
                    "generated_${UUID.randomUUID()}.$extension",
                )
            cachedFile.writeBytes(imageBytes)

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, cachedFile)
            return GeneratedImageResult(
                imageUri = contentUri.toString(),
                mimeType = mimeType,
                prompt = prompt,
            )
        }

        /**
         * Generates a list of AppFunctionMetadata representing these internal tools so the LLM provider
         * can convert them to standard schemas.
         */
        fun getInternalToolsMetadata(): List<Pair<AppFunctionMetadata, AppFunctionState>> {
            val latLngType =
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "latitude" to AppFunctionDoubleTypeMetadata(isNullable = false),
                            "longitude" to AppFunctionDoubleTypeMetadata(isNullable = false),
                        ),
                    required = listOf("latitude", "longitude"),
                    isNullable = true,
                    qualifiedName = "com.example.appfunctions.agent.data.AgentInternalTools.LatLng",
                )

            val imageResultType =
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "imageUri" to AppFunctionStringTypeMetadata(isNullable = false),
                            "mimeType" to AppFunctionStringTypeMetadata(isNullable = false),
                            "prompt" to AppFunctionStringTypeMetadata(isNullable = false),
                        ),
                    required = listOf("imageUri", "mimeType", "prompt"),
                    isNullable = false,
                    qualifiedName = "com.example.appfunctions.agent.data.AgentInternalTools.GeneratedImageResult",
                )

            val getCurrentLocationTool =
                AppFunctionMetadata(
                    name = AppFunctionName(INTERNAL_TOOL_PACKAGE, "getCurrentLocation"),
                    schema = null,
                    parameters = emptyList<AppFunctionParameterMetadata>(),
                    response =
                        AppFunctionResponseMetadata(
                            valueType = latLngType,
                            description = "The current location coordinates of the device, or null.",
                        ),
                    description = "Retrieve the current latitude and longitude coordinates of the device.",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = INTERNAL_TOOL_PACKAGE,
                            appFunctions = listOf(),
                            components = AppFunctionComponentsMetadata(),
                        ),
                )

            val geocodeAddressTool =
                AppFunctionMetadata(
                    name = AppFunctionName(INTERNAL_TOOL_PACKAGE, "geocodeAddress"),
                    schema = null,
                    parameters =
                        listOf(
                            AppFunctionParameterMetadata(
                                name = "address",
                                isRequired = true,
                                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                                description = "The physical address to geocode (e.g., '1600 Amphitheatre Pkwy, Mountain View, CA').",
                            ),
                        ),
                    response =
                        AppFunctionResponseMetadata(
                            valueType = latLngType,
                            description = "The latitude and longitude coordinates of the address, or null.",
                        ),
                    description = "Geocode a physical address string into its latitude and longitude coordinates.",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = INTERNAL_TOOL_PACKAGE,
                            appFunctions = listOf(),
                            components = AppFunctionComponentsMetadata(),
                        ),
                )

            val generateImageTool =
                AppFunctionMetadata(
                    name = AppFunctionName(INTERNAL_TOOL_PACKAGE, "generateImage"),
                    schema = null,
                    parameters =
                        listOf(
                            AppFunctionParameterMetadata(
                                name = "prompt",
                                isRequired = true,
                                dataType = AppFunctionStringTypeMetadata(isNullable = false),
                                description = "The text prompt describing the image to generate (e.g., 'futuristic cityscape at sunset').",
                            ),
                            AppFunctionParameterMetadata(
                                name = "aspectRatio",
                                isRequired = false,
                                dataType = AppFunctionStringTypeMetadata(isNullable = true),
                                description = "Optional aspect ratio for the image (e.g., '16:9', '1:1').",
                            ),
                        ),
                    response =
                        AppFunctionResponseMetadata(
                            valueType = imageResultType,
                            description = "A GeneratedImageResult containing the generated remote image URI.",
                        ),
                    description = "Generates an image from a text prompt and returns the remote image URI.",
                    deprecation = null,
                    packageMetadata =
                        AppFunctionPackageMetadata(
                            packageName = INTERNAL_TOOL_PACKAGE,
                            appFunctions = listOf(),
                            components = AppFunctionComponentsMetadata(),
                        ),
                )

            @SuppressLint("RestrictedApi")
            return listOf(
                getCurrentLocationTool to AppFunctionState(functionName = getCurrentLocationTool.name, isEnabled = true),
                geocodeAddressTool to AppFunctionState(functionName = geocodeAddressTool.name, isEnabled = true),
                generateImageTool to AppFunctionState(functionName = generateImageTool.name, isEnabled = true),
            )
        }

        /** Represents the latitude and longitude coordinates. */
        data class LatLng(
            val latitude: Double,
            val longitude: Double,
        )

        /** Represents the result of an image generation request. */
        data class GeneratedImageResult(
            val imageUri: String,
            val mimeType: String,
            val prompt: String,
        )

        companion object {
            const val INTERNAL_TOOL_PACKAGE = "com.example.appfunctions.agent.internal"
        }
    }
