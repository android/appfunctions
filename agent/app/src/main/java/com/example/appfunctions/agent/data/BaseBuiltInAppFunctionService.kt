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
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.appfunctions.agent.BuildConfig
import com.example.appfunctions.agent.di.settingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Built-in AppFunctions for location and geocoding services. */
@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "BuiltInAppFunctionService",
    appFunctionXmlFileName = "builtin_app_function_service",
)
abstract class BaseBuiltInAppFunctionService : AppFunctionService() {
    /**
     * Geocode a physical address string into its latitude and longitude coordinates.
     *
     * @param address The physical address to geocode (e.g., "1600 Amphitheatre Pkwy, Mountain View,
     *   CA").
     * @return The latitude and longitude coordinates of the address, or null if geocoding fails.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun geocodeAddress(address: String): LatLng? {
        if (!Geocoder.isPresent()) {
            return null
        }

        val geocoder = Geocoder(this)

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
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCurrentLocation(): LatLng? =
        withContext(Dispatchers.Default) {
            // Check permissions
            val hasFineLocation =
                ContextCompat.checkSelfPermission(
                    this@BaseBuiltInAppFunctionService,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocation =
                ContextCompat.checkSelfPermission(
                    this@BaseBuiltInAppFunctionService,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocation && !hasCoarseLocation) {
                throw IllegalStateException("Location permission is not granted")
            }

            val locationManager =
                this@BaseBuiltInAppFunctionService.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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

    /** Represents the latitude and longitude coordinates. */
    @AppFunctionSerializable(isDescribedByKDoc = true)
    data class LatLng(
        /** The latitude coordinate. */
        val latitude: Double,
        /** The longitude coordinate. */
        val longitude: Double,
    )

    /**
     * Generates an image from a text prompt and returns the remote image URI.
     *
     * @param prompt The text prompt describing the image to generate (e.g., "futuristic cityscape at sunset").
     * @param aspectRatio Optional aspect ratio for the image (e.g., "16:9", "1:1").
     * @return A GeneratedImageResult containing the generated remote image URI.
     */
    @AppFunction(isDescribedByKDoc = true)
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
    ): String =
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
        }.toString()

    private fun executeImageRequest(
        apiKey: String,
        requestPayload: String,
    ): String {
        val endpointUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=$apiKey"
        val connection =
            (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

        return try {
            connection.outputStream.use { os ->
                os.write(requestPayload.toByteArray(Charsets.UTF_8))
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

            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private data class ImageInlineData(val base64: String, val mimeType: String)

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

        val inlineResult =
            (0 until parts.length())
                .asSequence()
                .mapNotNull { i ->
                    val part = parts.getJSONObject(i)
                    val inlineData =
                        part.optJSONObject("inlineData")
                            ?: part.optJSONObject("inline_data")
                    if (inlineData != null) {
                        val data = inlineData.optString("data")
                        val mime =
                            inlineData
                                .optString("mimeType")
                                .takeIf { it.isNotBlank() }
                                ?: inlineData.optString("mime_type").takeIf { it.isNotBlank() }
                                ?: "image/png"
                        if (data.isNotBlank()) ImageInlineData(data, mime) else null
                    } else {
                        null
                    }
                }
                .firstOrNull()
                ?: throw IllegalStateException(
                    "No inlineData image found in response parts. Gemini response: $responseText",
                )

        val imageBytes = Base64.decode(inlineResult.base64, Base64.DEFAULT)
        val extension =
            when {
                inlineResult.mimeType.contains("jpeg", ignoreCase = true) ||
                    inlineResult.mimeType.contains("jpg", ignoreCase = true) -> "jpg"
                else -> "png"
            }
        val cachedFile =
            File(
                cacheDir,
                "generated_${UUID.randomUUID()}.$extension",
            )
        cachedFile.writeBytes(imageBytes)

        val authority = "$packageName.fileprovider"
        val contentUri = FileProvider.getUriForFile(this, authority, cachedFile)
        return GeneratedImageResult(
            imageUri = contentUri.toString(),
            mimeType = inlineResult.mimeType,
            prompt = prompt,
        )
    }

    /** Represents the result of an image generation request. */
    @AppFunctionSerializable
    data class GeneratedImageResult(
        /** The remote URI or URL of the generated image. */
        val imageUri: String,
        /** The MIME type of the generated image. */
        val mimeType: String,
        /** The original prompt used to generate the image. */
        val prompt: String,
    )
}
