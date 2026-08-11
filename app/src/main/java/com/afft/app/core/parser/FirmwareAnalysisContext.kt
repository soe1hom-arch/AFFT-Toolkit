/*
 * Copyright (c) 2026 Wandi (soe1hom-arch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afft.app.core.parser

import java.io.File

/**
 * Konteks analisis satu file firmware.
 * Dibuat oleh engine sebelum analisis dan diisi ulang setelah selesai.
 */
data class FirmwareAnalysisContext(
    val inputFile: File,
    val analysisStartedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = inputFile.length(),
    val elapsedMillis: Long = 0L,
    val parserName: String? = null,
    val parserVersion: String? = null,
) {

    fun withParser(name: String, version: String): FirmwareAnalysisContext =
        copy(parserName = name, parserVersion = version)

    fun finished(): FirmwareAnalysisContext =
        copy(elapsedMillis = System.currentTimeMillis() - analysisStartedAt)

    companion object {
        fun start(file: File): FirmwareAnalysisContext = FirmwareAnalysisContext(inputFile = file)
    }
}
