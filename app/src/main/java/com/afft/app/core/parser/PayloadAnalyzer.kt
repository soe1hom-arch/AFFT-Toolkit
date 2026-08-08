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
 * Menghitung Health Score, level validasi, dan rekomendasi untuk payload.bin
 * berdasarkan data manifest yang sudah dibaca [PayloadParser].
 *
 * SKORING SKEM:
 *   Base 100
 *   - corrupted / unsupported format  -> 30   (maksimum)
 *   - missing recognized partitions   -> 30   (dari 100)
 *   - optional metadata tidak terisi -> 20   (android version/architecture/etc.)
 *   Skor di-clamp ke rentang 20..100.
 *
 * Validasi:
 *   - corrupted / unsupported  -> ERROR
 *   - tanpa partisi dikenali    -> UNKNOWN
 *   - score >= 85               -> READY
 *   - score >= 60               -> WARNING
 *   - lainnya                   -> ERROR
 */
object PayloadAnalyzer {

    data class AnalyzerInput(
        val formatVersion: Long,
        val minorVersion: Long?,
        val partitionCount: Int,
        val manifestWellFormed: Boolean,
        val corruptionDetected: Boolean,
        val metadataSignaturePresent: Boolean,
        val optionalMetadataMissing: Int,
    )

    data class Outcome(
        val healthScore: Int,
        val health: HealthStatus,
        val indicators: List<IndicatorSpec>,
        val validation: ValidationLevel,
        val validationReason: String,
        val suggestedAction: String,
        val suggestedCompatibility: String,
        val warnings: List<String>,
    )

    fun analyze(input: AnalyzerInput): Outcome {
        val supported = input.formatVersion == 2L
        val corrupted = input.corruptionDetected || !input.manifestWellFormed

        if (corrupted || !supported) {
            val unsupported = !supported
            val base = 30
            val indicators =
                buildList {
                    if (corrupted) add(IndicatorSpec("Corrupted payload", IndicatorTone.CRITICAL))
                    if (unsupported) add(IndicatorSpec("Unsupported payload version", IndicatorTone.CRITICAL))
                }
            val reason =
                when {
                    corrupted -> "Corrupted metadata or unsupported format"
                    else -> "Unsupported payload version"
                }
            val action =
                when {
                    corrupted -> "Re-download the firmware file"
                    else -> "Update AFFT binaries"
                }
            return Outcome(
                healthScore = base,
                health = HealthStatus.CRITICAL,
                indicators = indicators,
                validation = ValidationLevel.ERROR,
                validationReason = reason,
                suggestedAction = action,
                suggestedCompatibility = "Unable to analyze",
                warnings = listOf(reason),
            )
        }

        var score = 100
        val warnings = mutableListOf<String>()

        if (input.partitionCount == 0) {
            score -= 30
            warnings += "No recognized partitions"
        }
        if (input.optionalMetadataMissing > 0) {
            score -= 20
            warnings += "Minor metadata missing (${input.optionalMetadataMissing} field)"
        }
        score = score.coerceIn(20, 100)

        val validation =
            when {
                input.partitionCount == 0 -> ValidationLevel.UNKNOWN
                score >= 85 -> ValidationLevel.READY
                score >= 60 -> ValidationLevel.WARNING
                else -> ValidationLevel.ERROR
            }

        val indicators =
            buildList {
                if (input.corruptionDetected) {
                    add(IndicatorSpec("Corrupted payload", IndicatorTone.CRITICAL))
                } else {
                    add(IndicatorSpec("Valid payload"))
                    if (supported) add(IndicatorSpec("Supported version"))
                    if (input.metadataSignaturePresent) add(IndicatorSpec("Metadata signed"))
                }
                if (input.optionalMetadataMissing > 0) {
                    add(IndicatorSpec("Minor metadata missing", IndicatorTone.WARNING))
                }
                if (input.partitionCount == 0) {
                    add(IndicatorSpec("No recognized partitions", IndicatorTone.WARNING))
                }
            }

        val reason =
            when (validation) {
                ValidationLevel.READY -> "Supported firmware"
                ValidationLevel.WARNING -> "Minor metadata missing; extraction may still succeed"
                ValidationLevel.ERROR -> "Unsupported or damaged metadata"
                ValidationLevel.UNKNOWN -> "No recognizable partition table"
            }

        val action =
            when (validation) {
                ValidationLevel.READY -> "Ready for Extraction"
                ValidationLevel.WARNING -> "Extraction may still succeed"
                ValidationLevel.ERROR -> "Update AFFT binaries"
                ValidationLevel.UNKNOWN -> "Manual inspection required"
            }

        return Outcome(
            healthScore = score,
            health = healthFor(score),
            indicators = indicators,
            validation = validation,
            validationReason = reason,
            suggestedAction = action,
            suggestedCompatibility =
                when (validation) {
                    ValidationLevel.READY -> "Estimated extraction supported"
                    ValidationLevel.WARNING -> "Estimated extraction supported with warnings"
                    ValidationLevel.ERROR -> "Extraction not recommended"
                    ValidationLevel.UNKNOWN -> "Not determined"
                },
            warnings = warnings,
        )
    }

    private fun healthFor(score: Int): HealthStatus =
        when {
            score >= 85 -> HealthStatus.EXCELLENT
            score >= 70 -> HealthStatus.GOOD
            score >= 50 -> HealthStatus.WARNING
            else -> HealthStatus.CRITICAL
        }
}
