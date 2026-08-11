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

/** Kelas kesehatan hasil analisis — generik, tidak terikat parser. */
enum class FirmwareHealth { EXCELLENT, GOOD, WARNING, CRITICAL }

/** Hasil perhitungan kesehatan: skor + level + kontribusi isu. */
data class FirmwareHealthResult(
    val score: Int,
    val health: FirmwareHealth,
    val contributions: List<FirmwareIssue>,
)

/**
 * Kalkulator health generik.
 *
 * SKORING SKEM (dokumentasi):
 *   Base = 100
 *   Tiap isu: MINOR -5, WARNING -10, CRITICAL -25.
 *   Jika status == ERROR -> skor dibatasi maks 30.
 *   Jika status == UNKNOWN -> skor 50.
 *   Clamp ke 0..100.
 *   Level: >=85 EXCELLENT, >=70 GOOD, >=50 WARNING, lainnya CRITICAL.
 *
 * Parser HANYA menyuplai [FirmwareValidation]; skor akhir dihitung di sini
 * agar konsisten lintas semua parser.
 */
object FirmwareHealthCalculator {

    private const val BASE_SCORE = 100
    private const val ERROR_CAP = 30
    private const val UNKNOWN_SCORE = 50

    private val PENALTY =
        mapOf(
            IssueSeverity.MINOR to 5,
            IssueSeverity.WARNING to 10,
            IssueSeverity.CRITICAL to 25,
        )

    fun calculate(validation: FirmwareValidation): FirmwareHealthResult {
        var score = BASE_SCORE
        validation.issues.forEach { issue ->
            score -= PENALTY[issue.severity] ?: 10
        }

        when (validation.status) {
            FirmwareValidationStatus.ERROR -> score = minOf(score, ERROR_CAP)
            FirmwareValidationStatus.UNKNOWN -> score = UNKNOWN_SCORE
            else -> Unit
        }
        score = score.coerceIn(0, 100)

        val health =
            when {
                score >= 85 -> FirmwareHealth.EXCELLENT
                score >= 70 -> FirmwareHealth.GOOD
                score >= 50 -> FirmwareHealth.WARNING
                else -> FirmwareHealth.CRITICAL
            }
        return FirmwareHealthResult(score = score, health = health, contributions = validation.issues)
    }
}
