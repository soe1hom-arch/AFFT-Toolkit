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

/**
 * Factory parser firmware.
 *
 * Tujuan: menghindari konstruksi parser langsung di seluruh kode.
 * Cukup daftarkan builder parser sekali; konsumen memanggil [create].
 * Cocok untuk injeksi dependensi di masa depan (mis. ServiceLocator/DI).
 */
object FirmwareParserFactory {

    private val builders = linkedMapOf<String, () -> FirmwareParser>()

    init {
        register("payload") { PayloadFirmwareParser() }
        register("boot") { BootParser() }
        register("super") { SuperParser() }
        register("filesystem") { FilesystemParser() }
    }

    /** Mendaftarkan builder parser. Mengembalikan false jika nama sudah ada. */
    @Synchronized
    fun register(name: String, builder: () -> FirmwareParser): Boolean =
        builders.putIfAbsent(name, builder) == null

    /** Membuat instance parser baru (setiap panggilan instance baru). */
    fun create(name: String): FirmwareParser? = builders[name]?.invoke()

    fun names(): Set<String> = builders.keys

    fun clear() {
        builders.clear()
    }

    /**
     * Parser bawaan AFFT Toolkit.
     * Parser baru (Boot/Super/Filesystem/dll.) cukup ditambahkan di sini —
     * engine & registry tidak perlu diubah.
     */
    fun createDefault(): List<FirmwareParser> =
        listOf(
            PayloadFirmwareParser(),
            BootParser(),
            SuperParser(),
            FilesystemParser(),
        )
}
