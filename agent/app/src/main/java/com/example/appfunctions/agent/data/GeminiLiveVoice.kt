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

/**
 * Prebuilt studio voices supported by Gemini Live (`models/gemini-3.8-live`) via
 * `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`.
 */
enum class GeminiLiveVoice(
    /** Voice identifier sent in `prebuiltVoiceConfig.voiceName` and shown in Settings. */
    val apiName: String,
    /** Short vocal style descriptor for the voice. */
    val style: String,
) {
    ZEPHYR("Zephyr", "Bright"),
    PUCK("Puck", "Upbeat"),
    CHARON("Charon", "Informative"),
    KORE("Kore", "Firm"),
    FENRIR("Fenrir", "Excitable"),
    LEDA("Leda", "Youthful"),
    ORUS("Orus", "Firm"),
    AOEDE("Aoede", "Breezy"),
    CALLIRRHOE("Callirrhoe", "Easy-going"),
    AUTONOE("Autonoe", "Bright"),
    ENCELADUS("Enceladus", "Breathy"),
    IAPETUS("Iapetus", "Clear"),
    UMBRIEL("Umbriel", "Easy-going"),
    ALGIEBA("Algieba", "Smooth"),
    DESPINA("Despina", "Smooth"),
    ERINOME("Erinome", "Clear"),
    ALGENIB("Algenib", "Gravelly"),
    RASALGETHI("Rasalgethi", "Informative"),
    LAOMEDEIA("Laomedeia", "Upbeat"),
    ACHERNAR("Achernar", "Soft"),
    ALNILAM("Alnilam", "Firm"),
    SCHEDAR("Schedar", "Even"),
    GACRUX("Gacrux", "Mature"),
    PULCHERRIMA("Pulcherrima", "Forward"),
    ACHIRD("Achird", "Friendly"),
    ZUBENELGENUBI("Zubenelgenubi", "Casual"),
    VINDEMIATRIX("Vindemiatrix", "Gentle"),
    SADACHBIA("Sadachbia", "Lively"),
    SADALTAGER("Sadaltager", "Knowledgeable"),
    SULAFAT("Sulafat", "Warm"),
    ;

    companion object {
        val DEFAULT: GeminiLiveVoice = ZEPHYR

        /** Resolves a persisted preference string back to a [GeminiLiveVoice], falling back to [DEFAULT]. */
        fun fromStoredValue(value: String?): GeminiLiveVoice =
            value?.let { stored ->
                entries.find {
                    it.name.equals(stored, ignoreCase = true) ||
                        it.apiName.equals(stored, ignoreCase = true)
                }
            } ?: DEFAULT
    }
}
