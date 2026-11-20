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

import android.content.Intent
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionName
import androidx.appfunctions.AppFunctionUriGrant
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.metadata.AppFunctionAllOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionOneOfTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.core.net.toUri
import com.example.appfunctions.agent.data.FakeResultImageReader
import com.example.appfunctions.agent.domain.ToolOutputImage
import com.example.appfunctions.agent.domain.appfunction.LoadResultImagesUseCase.Companion.MAX_IMAGES
import com.example.appfunctions.agent.domain.appfunction.LoadResultImagesUseCase.Companion.MAX_IMAGE_BYTES
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoadResultImagesUseCaseTest {
    private val resultImageReader = FakeResultImageReader()
    private val loadResultImagesUseCase = LoadResultImagesUseCase(resultImageReader)

    private val components = AppFunctionComponentsMetadata()
    private val uriType = objectType("android.net.Uri", "uri" to AppFunctionStringTypeMetadata(isNullable = false))
    private val uriGrantType =
        objectType(URI_GRANT, "uri" to uriType, "modeFlags" to AppFunctionIntTypeMetadata(isNullable = false))
    private val photoType =
        objectType(
            "com.example.photos.Photo",
            "title" to AppFunctionStringTypeMetadata(isNullable = false),
            "image" to uriGrantType,
        )
    private val albumType =
        objectType(
            "com.example.photos.Album",
            "photos" to AppFunctionArrayTypeMetadata(itemType = uriGrantType, isNullable = false),
        )
    private val albumFunction = function(returnType = albumType)
    private val stringFunction = function(returnType = AppFunctionStringTypeMetadata(isNullable = false))

    @Test
    fun `invoke loads a URI grant returned as the value`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val function = function(returnType = uriGrantType)

            val resultImages = loadResultImagesUseCase(function, result(function, uriGrant(photoUri(1))))

            assertEquals(ResultImages(images = listOf(photo(1)), skipped = emptyList()), resultImages)
            // A grant has no MIME type, so the reader asks the provider for it.
            assertEquals(listOf(photoUri(1) to null), resultImageReader.reads)
        }

    @Test
    fun `invoke loads a URI grant in a property of the value`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val function = function(returnType = photoType)
            val value =
                AppFunctionData.Builder(photoType, components)
                    .setString("title", "Sunset")
                    .setAppFunctionData("image", uriGrant(photoUri(1)))
                    .build()

            val resultImages = loadResultImagesUseCase(function, result(function, value))

            assertEquals(ResultImages(images = listOf(photo(1)), skipped = emptyList()), resultImages)
        }

    @Test
    fun `invoke loads every URI grant in a list`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            resultImageReader.addImage(photoUri(2), photo(2))

            val resultImages =
                loadResultImagesUseCase(albumFunction, albumResult(uriGrant(photoUri(1)), uriGrant(photoUri(2))))

            assertEquals(listOf(photo(1), photo(2)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
        }

    @Test
    fun `invoke finds URI grants in types referenced from the components`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val referencedAlbumType =
                objectType(
                    ALBUM,
                    "photos" to
                        AppFunctionArrayTypeMetadata(
                            itemType = AppFunctionReferenceTypeMetadata(URI_GRANT, isNullable = false),
                            isNullable = false,
                        ),
                )
            val sharedTypes =
                AppFunctionComponentsMetadata(dataTypes = mapOf(ALBUM to referencedAlbumType, URI_GRANT to uriGrantType))
            val function =
                function(returnType = AppFunctionReferenceTypeMetadata(ALBUM, isNullable = false), components = sharedTypes)
            val value =
                AppFunctionData.Builder(referencedAlbumType, sharedTypes)
                    .setAppFunctionDataList("photos", listOf(uriGrant(photoUri(1))))
                    .build()

            val resultImages = loadResultImagesUseCase(function, result(function, value))

            assertEquals(listOf(photo(1)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
        }

    @Test
    fun `invoke finds URI grants in the matching type of a oneOf value`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val videoType = objectType("com.example.photos.Video", "preview" to uriGrantType)
            val mediaType =
                AppFunctionOneOfTypeMetadata(
                    matchOneOf = listOf(photoType, videoType),
                    qualifiedName = "com.example.photos.Media",
                    isNullable = false,
                )
            val function = function(returnType = mediaType)
            val video =
                AppFunctionData.Builder(videoType, components)
                    .setAppFunctionData("preview", uriGrant(photoUri(1)))
                    .build()

            val resultImages = loadResultImagesUseCase(function, result(function, video))

            assertEquals(listOf(photo(1)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
        }

    @Test
    fun `invoke finds URI grants in every type of an allOf value`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val sharedType = objectType("com.example.photos.Shared", "sharedBy" to AppFunctionStringTypeMetadata(isNullable = false))
            val sharedPhotoType =
                AppFunctionAllOfTypeMetadata(
                    matchAll = listOf(sharedType, photoType),
                    qualifiedName = "com.example.photos.SharedPhoto",
                    isNullable = false,
                )
            val function = function(returnType = sharedPhotoType)
            val value =
                AppFunctionData.Builder(sharedPhotoType, components)
                    .setString("sharedBy", "Ana")
                    .setString("title", "Sunset")
                    .setAppFunctionData("image", uriGrant(photoUri(1)))
                    .build()

            val resultImages = loadResultImagesUseCase(function, result(function, value))

            assertEquals(listOf(photo(1)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
        }

    @Test
    fun `invoke ignores a URI grant without read permission`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            resultImageReader.addImage(photoUri(2), photo(2))

            val resultImages =
                loadResultImagesUseCase(
                    albumFunction,
                    albumResult(
                        uriGrant(photoUri(1), Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                        uriGrant(photoUri(2), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                    ),
                )

            assertEquals(listOf(photo(2)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
            assertEquals(listOf(photoUri(2) to null), resultImageReader.reads)
        }

    @Test
    fun `invoke loads an image returned more than once only once`() =
        runTest {
            resultImageReader.addImage(photoUri(1), photo(1))
            val editedPhotoType =
                objectType(
                    "com.example.photos.EditedPhoto",
                    "imageUri" to AppFunctionStringTypeMetadata(isNullable = false),
                    "image" to uriGrantType,
                )
            val function = function(returnType = editedPhotoType)
            val value =
                AppFunctionData.Builder(editedPhotoType, components)
                    .setString("imageUri", photoUri(1))
                    .setAppFunctionData("image", uriGrant(photoUri(1)))
                    .build()

            val resultImages =
                loadResultImagesUseCase(
                    function,
                    result(function, value, formattedJson = JSONObject().put("imageUri", photoUri(1)).toString()),
                )

            assertEquals(listOf(photo(1)), resultImages.images)
            assertEquals(emptyList<SkippedResultImage>(), resultImages.skipped)
            assertEquals(listOf(photoUri(1) to null), resultImageReader.reads)
        }

    @Test
    fun `invoke skips the images the reader can't read with the reason`() =
        runTest {
            resultImageReader.addFailure(photoUri(1), "it is not an image (text/plain)")
            resultImageReader.addFailure(photoUri(2), "the agent has no read access to it")
            resultImageReader.addImage(photoUri(3), photo(3))

            val resultImages =
                loadResultImagesUseCase(
                    albumFunction,
                    albumResult(uriGrant(photoUri(1)), uriGrant(photoUri(2)), uriGrant(photoUri(3))),
                )

            assertEquals(
                ResultImages(
                    images = listOf(photo(3)),
                    skipped =
                        listOf(
                            SkippedResultImage(photoUri(1), "it is not an image (text/plain)"),
                            SkippedResultImage(photoUri(2), "the agent has no read access to it"),
                        ),
                ),
                resultImages,
            )
        }

    @Test
    fun `invoke skips an image larger than MAX_IMAGE_BYTES`() =
        runTest {
            val largestImage = ToolOutputImage(mimeType = "image/png", data = ByteArray(MAX_IMAGE_BYTES))
            val tooLargeImage = ToolOutputImage(mimeType = "image/png", data = ByteArray(MAX_IMAGE_BYTES + 1))
            resultImageReader.addImage(photoUri(1), largestImage)
            resultImageReader.addImage(photoUri(2), tooLargeImage)

            val resultImages =
                loadResultImagesUseCase(albumFunction, albumResult(uriGrant(photoUri(1)), uriGrant(photoUri(2))))

            assertEquals(listOf(largestImage), resultImages.images)
            assertEquals(
                listOf(SkippedResultImage(photoUri(2), "it is larger than $MAX_IMAGE_BYTES bytes")),
                resultImages.skipped,
            )
        }

    @Test
    fun `invoke skips the images over MAX_IMAGES without reading them`() =
        runTest {
            val numbers = 1..MAX_IMAGES + 1
            numbers.forEach { resultImageReader.addImage(photoUri(it), photo(it)) }

            val resultImages =
                loadResultImagesUseCase(albumFunction, albumResult(*numbers.map { uriGrant(photoUri(it)) }.toTypedArray()))

            assertEquals((1..MAX_IMAGES).map { photo(it) }, resultImages.images)
            assertEquals(
                listOf(
                    SkippedResultImage(
                        photoUri(MAX_IMAGES + 1),
                        "the limit of $MAX_IMAGES images per result was reached",
                    ),
                ),
                resultImages.skipped,
            )
            assertEquals((1..MAX_IMAGES).map { photoUri(it) to null }, resultImageReader.reads)
        }

    @Test
    fun `invoke doesn't count images that fail to load toward MAX_IMAGES`() =
        runTest {
            resultImageReader.addFailure(photoUri(1), "it was not found")
            val loadable = 2..MAX_IMAGES + 1
            loadable.forEach { resultImageReader.addImage(photoUri(it), photo(it)) }

            val resultImages =
                loadResultImagesUseCase(
                    albumFunction,
                    albumResult(*(1..MAX_IMAGES + 1).map { uriGrant(photoUri(it)) }.toTypedArray()),
                )

            assertEquals(loadable.map { photo(it) }, resultImages.images)
            assertEquals(listOf(SkippedResultImage(photoUri(1), "it was not found")), resultImages.skipped)
        }

    @Test
    fun `invoke loads the image of an imageUri with its mimeType`() =
        runTest {
            val generatedImage = ToolOutputImage(mimeType = "image/jpeg", data = byteArrayOf(1, 2, 3))
            resultImageReader.addImage(GENERATED_IMAGE_URI, generatedImage)

            val resultImages =
                loadResultImagesUseCase(stringFunction, imageUriResult(GENERATED_IMAGE_URI, mimeType = "image/jpeg"))

            assertEquals(ResultImages(images = listOf(generatedImage), skipped = emptyList()), resultImages)
            assertEquals(listOf(GENERATED_IMAGE_URI to "image/jpeg"), resultImageReader.reads)
        }

    @Test
    fun `invoke ignores an imageUri that is not a content URI`() =
        runTest {
            for (imageUri in listOf("https://example.com/dog.png", "file:///sdcard/dog.png", "dog.png")) {
                val resultImages = loadResultImagesUseCase(stringFunction, imageUriResult(imageUri, mimeType = "image/png"))

                assertEquals(ResultImages(images = emptyList(), skipped = emptyList()), resultImages)
            }
            assertEquals(emptyList<Pair<String, String?>>(), resultImageReader.reads)
        }

    @Test
    fun `invoke declares PNG for an imageUri without a mimeType`() =
        runTest {
            resultImageReader.addImage(GENERATED_IMAGE_URI, photo(1))

            loadResultImagesUseCase(stringFunction, imageUriResult(GENERATED_IMAGE_URI, mimeType = null))
            loadResultImagesUseCase(stringFunction, imageUriResult(GENERATED_IMAGE_URI, mimeType = " "))

            // The default type of an attachment. The reader uses it only if the provider reports none.
            assertEquals(listOf(GENERATED_IMAGE_URI to "image/png", GENERATED_IMAGE_URI to "image/png"), resultImageReader.reads)
        }

    @Test
    fun `invoke returns no images for a result without any`() =
        runTest {
            val noImages = ResultImages(images = emptyList(), skipped = emptyList())

            assertEquals(noImages, loadResultImagesUseCase(stringFunction, stringResult("""{"title":"Sunset"}""")))
            // Results that aren't a JSON object have no imageUri field.
            assertEquals(noImages, loadResultImagesUseCase(stringFunction, stringResult("Done")))
            assertEquals(noImages, loadResultImagesUseCase(stringFunction, stringResult("""["$GENERATED_IMAGE_URI"]""")))
            assertEquals(emptyList<Pair<String, String?>>(), resultImageReader.reads)
        }

    @Test
    fun `invoke returns no images when an optional URI grant is not set`() =
        runTest {
            val draftType =
                AppFunctionObjectTypeMetadata(
                    properties =
                        mapOf(
                            "title" to AppFunctionStringTypeMetadata(isNullable = false),
                            "image" to
                                AppFunctionObjectTypeMetadata(
                                    properties = uriGrantType.properties,
                                    required = uriGrantType.required,
                                    qualifiedName = URI_GRANT,
                                    isNullable = true,
                                ),
                        ),
                    required = listOf("title"),
                    qualifiedName = "com.example.photos.Draft",
                    isNullable = false,
                )
            val function = function(returnType = draftType)
            val value = AppFunctionData.Builder(draftType, components).setString("title", "Sunset").build()

            val resultImages = loadResultImagesUseCase(function, result(function, value))

            assertEquals(ResultImages(images = emptyList(), skipped = emptyList()), resultImages)
        }

    @Test
    fun `invoke reports a result that doesn't match the metadata of the function once`() =
        runTest {
            val function = function(returnType = photoType)
            // The function returned a string although its metadata declares a photo.
            val data =
                AppFunctionData.Builder(stringFunction.response, components)
                    .setString(PROPERTY_RETURN_VALUE, "Sunset")
                    .build()

            val resultImages = loadResultImagesUseCase(function, ExecuteAppFunctionResult.Data(data, "Sunset"))

            assertEquals(emptyList<ToolOutputImage>(), resultImages.images)
            val skipped = resultImages.skipped.single()
            assertNull(skipped.uri)
            assertTrue(skipped.reason, skipped.reason.startsWith("the result could not be read"))
        }

    private fun objectType(
        qualifiedName: String,
        vararg properties: Pair<String, AppFunctionDataTypeMetadata>,
    ) = AppFunctionObjectTypeMetadata(
        properties = mapOf(*properties),
        required = properties.map { it.first },
        qualifiedName = qualifiedName,
        isNullable = false,
    )

    private fun function(
        returnType: AppFunctionDataTypeMetadata,
        components: AppFunctionComponentsMetadata = this.components,
    ) = AppFunctionMetadata(
        name = AppFunctionName(PHOTOS_PKG, "getPhotos"),
        schema = null,
        parameters = emptyList(),
        response = AppFunctionResponseMetadata(valueType = returnType, description = ""),
        description = "Gets photos",
        deprecation = null,
        packageMetadata = AppFunctionPackageMetadata(packageName = PHOTOS_PKG, components = components),
    )

    /** A grant of [modeFlags] on [uri], as a function returns it. */
    private fun uriGrant(
        uri: String,
        modeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION,
    ): AppFunctionData =
        AppFunctionData.serialize(
            uriGrantType,
            components,
            AppFunctionUriGrant(uri.toUri(), modeFlags),
            AppFunctionUriGrant::class.java,
        )

    /** The result of [function] returning [value]. */
    private fun result(
        function: AppFunctionMetadata,
        value: AppFunctionData,
        formattedJson: String = "{}",
    ) = ExecuteAppFunctionResult.Data(
        data =
            AppFunctionData.Builder(function.response, function.components)
                .setAppFunctionData(PROPERTY_RETURN_VALUE, value)
                .build(),
        formattedJson = formattedJson,
    )

    /** The result of [albumFunction] returning an album of [photos]. */
    private fun albumResult(vararg photos: AppFunctionData) =
        result(
            albumFunction,
            AppFunctionData.Builder(albumType, components).setAppFunctionDataList("photos", photos.toList()).build(),
        )

    /** The result of [stringFunction] returning [formattedJson]. */
    private fun stringResult(formattedJson: String) =
        ExecuteAppFunctionResult.Data(
            data =
                AppFunctionData.Builder(stringFunction.response, components)
                    .setString(PROPERTY_RETURN_VALUE, formattedJson)
                    .build(),
            formattedJson = formattedJson,
        )

    /** A result with an `imageUri` field, the way the agent's generateImage tool formats it. */
    private fun imageUriResult(
        imageUri: String,
        mimeType: String?,
    ): ExecuteAppFunctionResult.Data {
        val json = JSONObject().put("imageUri", imageUri).put("prompt", "a dog")
        if (mimeType != null) {
            json.put("mimeType", mimeType)
        }
        return stringResult(json.toString())
    }

    private fun photoUri(number: Int) = "content://com.example.photos.provider/photos/$number"

    private fun photo(number: Int) = ToolOutputImage(mimeType = "image/png", data = byteArrayOf(number.toByte()))

    private companion object {
        const val PHOTOS_PKG = "com.example.photos"
        const val URI_GRANT = "androidx.appfunctions.AppFunctionUriGrant"
        const val ALBUM = "com.example.photos.Album"
        const val GENERATED_IMAGE_URI = "content://com.example.appfunctions.agent.fileprovider/cache/generated_1.png"
    }
}
