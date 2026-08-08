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

import com.afft.app.ui.components.dashboard.FirmwareMetadata
import java.io.File

/**
 * Kontrak tunggal semua parser firmware di AFFT Toolkit.
 *
 * Setiap parser (PayloadParser, BootParser, SuperParser, FilesystemParser,
 * RecoveryParser, VendorBootParser, InitBootParser, VBMetaParser, DTBOParser,
 * KernelParser, ROMProjectParser, APKParser, ...) WAJIB mengimplementasikan
 * antarmuka ini agar dapat dipakai oleh [FirmwareAnalysisEngine].
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │                        UML (Arsitektur)                     │
 * └──────────────────────────────────────────────────────────────┘
 *
 *            ┌──────────────────────┐
 *            │  FirmwareAnalysisEngine │
 *            └───────────┬──────────┘
 *                        │ menggunakan
 *                        ▼
 *            ┌──────────────────────┐
 *            │  FirmwareParserRegistry │ ───► detect() ──┐
 *            └───────────┬──────────┘                    │
 *                        │                               ▼
 *                        │                    ┌──────────────────────┐
 *                        │                    │  FirmwareParserFactory │
 *                        │                    └───────────┬──────────┘
 *                        │                                │ create()
 *                        ▼                                ▼
 *            ┌──────────────────────┐          ┌──────────────────────┐
 *            │   FirmwareParser     │◄─────────│  *PayloadParser       │
 *            │   (interface)        │          │  *BootParser          │
 *            │   analyze/validate   │          │  *SuperParser         │
 *            │   calculateHealth    │          │  (masa depan)         │
 *            └──────────┬───────────┘          └──────────────────────┘
 *                       │
 *                       ▼
 *            ┌──────────────────────┐      ┌──────────────────────┐
 *            │  FirmwareValidation   │─────►│  FirmwareHealthCalculator│
 *            └──────────────────────┘      └──────────────────────┘
 *                       │                            │
 *                       └───────────► FirmwareMetadata ◄──────────┘
 *                                    (hasil akhir engine)
 *
 * LIFECYCLE parser:
 *   1. Engine membuat [FirmwareAnalysisContext] (file, waktu, ukuran).
 *   2. Engine memanggil [FirmwareParserRegistry.detect] — mencocokkan
 *      [supportedExtensions]/[supportedMimeTypes] lalu [canParse].
 *   3. Engine memanggil [analyze] → metadata mentah.
 *   4. Engine memanggil [validate] → [FirmwareValidation].
 *   5. Engine memanggil [calculateHealth] (default: kalkulator generik).
 *   6. Engine menggabungkan hasil ke [FirmwareMetadata] final.
 */
interface FirmwareParser {

    /** Nama unik parser (mis. "payload", "boot", "super"). */
    val name: String

    /** Versi parser (mis. "1.0.0"). */
    val version: String

    /** Ekstensi file yang didukung, tanpa titik, huruf kecil. */
    fun supportedExtensions(): Set<String>

    /** MIME types yang didukung (dipakai saat memilih dari sistem file). */
    fun supportedMimeTypes(): Set<String>

    /** Deteksi cepat: apakah file ini cocok dengan parser ini? */
    fun canParse(file: File, context: FirmwareAnalysisContext): Boolean

    /** Analisis utama: baca metadata dan kembalikan model UI. */
    fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata

    /** Validasi hasil analisis. */
    fun validate(metadata: FirmwareMetadata): FirmwareValidation

    /**
     * Hitung health. Default memakai [FirmwareHealthCalculator] generik
     * agar skor konsisten lintas parser; parser tidak perlu menimpanya.
     */
    fun calculateHealth(validation: FirmwareValidation): FirmwareHealthResult =
        FirmwareHealthCalculator.calculate(validation)
}
