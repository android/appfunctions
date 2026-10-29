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
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.example.appfunctions.agent.data.FileDownloadHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ResolveRemoteFileToLocalUriUseCaseTest {
    private val components = AppFunctionComponentsMetadata(emptyMap())

    private class FakeFileDownloadHelper(
        private val onDownload: (String) -> String = {
            "content://com.example.appfunctions.agent.fileprovider/cache/resolved.png"
        },
    ) : FileDownloadHelper {
        val downloadedUrls = mutableListOf<String>()

        override suspend fun downloadToLocalUri(url: String): String {
            downloadedUrls.add(url)
            return onDownload(url)
        }
    }

    @Test
    fun invoke_resolvesRemoteFileReferencesWhileLeavingRegularUrlParametersUntouched() =
        runTest {
            val fakeHelper = FakeFileDownloadHelper()
            val useCase = ResolveRemoteFileToLocalUriUseCase(fakeHelper)

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
            val componentsWithRef =
                AppFunctionComponentsMetadata(
                    dataTypes = mapOf("android.net.Uri" to uriObjectType),
                )
            val parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "imageUri",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                    AppFunctionParameterMetadata(
                        name = "attachment",
                        isRequired = true,
                        dataType = AppFunctionReferenceTypeMetadata("android.net.Uri", false),
                    ),
                    AppFunctionParameterMetadata(
                        name = "payload",
                        isRequired = true,
                        dataType = payloadObjectType,
                    ),
                    AppFunctionParameterMetadata(
                        name = "websiteUrl",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                )
            val inputs =
                mapOf(
                    "imageUri" to "https://example.com/cat.png",
                    "attachment" to "https://example.com/doc.png",
                    "payload" to mapOf("nestedUris" to listOf("https://example.com/nested.png")),
                    "websiteUrl" to "https://example.com/home",
                )

            val resolved = useCase(parameters, componentsWithRef, inputs)

            assertEquals(
                listOf(
                    "https://example.com/cat.png",
                    "https://example.com/doc.png",
                    "https://example.com/nested.png",
                ),
                fakeHelper.downloadedUrls,
            )
            val expectedLocalUri =
                "content://com.example.appfunctions.agent.fileprovider/cache/resolved.png"
            assertEquals(expectedLocalUri, resolved["imageUri"])
            assertEquals(expectedLocalUri, resolved["attachment"])
            assertEquals(
                mapOf("nestedUris" to listOf(expectedLocalUri)),
                resolved["payload"],
            )
            assertEquals("https://example.com/home", resolved["websiteUrl"])
        }

    @Test
    fun invoke_throwsIOExceptionWhenRemoteFileDownloadFails() =
        runTest {
            val fakeHelper =
                FakeFileDownloadHelper {
                    throw IOException("HTTP 404")
                }
            val useCase = ResolveRemoteFileToLocalUriUseCase(fakeHelper)

            val parameters =
                listOf(
                    AppFunctionParameterMetadata(
                        name = "wallpaperUri",
                        isRequired = true,
                        dataType = AppFunctionStringTypeMetadata(false),
                    ),
                )

            try {
                useCase(
                    parameters,
                    components,
                    mapOf("wallpaperUri" to "https://example.com/missing.png"),
                )
                fail("Expected IOException to be thrown")
            } catch (e: IOException) {
                assertTrue(
                    e.message?.contains("Failed to download remote file reference: HTTP 404") ==
                        true,
                )
            }
        }
}
