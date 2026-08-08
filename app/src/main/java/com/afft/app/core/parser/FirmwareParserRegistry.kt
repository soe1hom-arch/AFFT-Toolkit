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
 * Registry parser firmware.
 *
 * WORKFLOW DETEKSI:
 *   1. Cari parser yang ekstensi filenya cocok (kasus-insensitif).
 *   2. Jika tidak ada, coba [FirmwareParser.canParse] satu per satu.
 *
 * Parser masa depan cukup mendaftarkan dirinya sendiri via [register],
 * tanpa mengubah engine maupun file lain.
 */
class FirmwareParserRegistry {

    private val parsers = linkedMapOf<String, FirmwareParser>()

    /** Mendaftarkan parser. Mengembalikan false jika nama sudah terpakai. */
    @Synchronized
    fun register(parser: FirmwareParser): Boolean =
        parsers.putIfAbsent(parser.name, parser) == null

    /** Menghapus parser berdasarkan nama. */
    @Synchronized
    fun remove(name: String): Boolean = parsers.remove(name) != null

    fun find(name: String): FirmwareParser? = parsers[name]

    fun all(): List<FirmwareParser> = parsers.values.toList()

    fun size(): Int = parsers.size

    /** Mendeteksi parser yang cocok untuk [file]. */
    fun detect(file: File, context: FirmwareAnalysisContext): FirmwareParser? {
        val extension = file.extension.lowercase()
        val byExtension = all().firstOrNull { extension in it.supportedExtensions() }
        if (byExtension != null) return byExtension
        return all().firstOrNull { it.canParse(file, context) }
    }
}
