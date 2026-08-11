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
 * Tingkat hasil kesehatan analisis. Dipetakan ke model UI saat konversi akhir.
 */
enum class HealthStatus { EXCELLENT, GOOD, WARNING, CRITICAL }

/** Nada indikator health (menentukan ikon & warna di UI). */
enum class IndicatorTone { POSITIVE, WARNING, CRITICAL }

/** Status validasi firmware. */
enum class ValidationLevel { READY, WARNING, ERROR, UNKNOWN }

/** Spesifikasi indikator health: teks + nada. */
data class IndicatorSpec(
    val text: String,
    val tone: IndicatorTone = IndicatorTone.POSITIVE,
)

/**
 * Model hasil analisis payload.bin — murni, tanpa dependensi UI.
 * Dari model ini PayloadParser memetakan ke FirmwareMetadata.
 */
data class PayloadAnalysisResult(
    val filename: String,
    val fileSize: Long,
    val sha256Hex: String,
    val lastModified: String,
    val formatVersion: Long,
    val minorVersion: Long?,
    val partitionCount: Int,
    val partitionNames: List<String>,
    val dynamicPartition: Boolean,
    val vabcEnabled: Boolean?,
    val compression: String?,
    val filesystem: String?,
    val architecture: String?,
    val androidVersion: String?,
    val deviceCodename: String?,
    val deviceName: String?,
    val buildFingerprint: String?,
    val securityPatch: String?,
    val healthScore: Int,
    val health: HealthStatus,
    val indicators: List<IndicatorSpec>,
    val validation: ValidationLevel,
    val validationReason: String,
    val suggestedAction: String,
    val suggestedCompatibility: String,
    val warnings: List<String>,
)
