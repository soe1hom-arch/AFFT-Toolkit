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
 * Status terstruktur hasil analisis payload.bin.
 * Dipakai untuk memetakan kegagalan ke UI tanpa melempar ke pengguna.
 */
enum class ParserStatus {
    /** Parsing & analisis berhasil. */
    SUCCESS,

    /** File tidak ditemukan / tidak dapat diakses. */
    FILE_NOT_FOUND,

    /** Izin baca ditolak. */
    PERMISSION_DENIED,

    /** Bukan format payload.bin yang valid (magic / header salah). */
    INVALID_PAYLOAD,

    /** Header file tidak valid (magic/struktur header tidak cocok). */
    INVALID_HEADER,

    /** Manifest (metadata) rusak atau format tidak dikenali. */
    CORRUPTED_METADATA,

    /** Versi format payload belum didukung oleh AFFT. */
    UNSUPPORTED_VERSION,

    /** Error lain saat membaca file. */
    READ_ERROR,
}
