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

/** Status validasi firmware. */
enum class FirmwareValidationStatus { READY, WARNING, ERROR, UNKNOWN }

/** Tingkat keparahan isu yang memengaruhi health score. */
enum class IssueSeverity { MINOR, WARNING, CRITICAL }

/** Satu isu validasi yang ditemukan parser. */
data class FirmwareIssue(
    val message: String,
    val severity: IssueSeverity,
    val recommendation: String? = null,
)

/**
 * Hasil validasi generik — setiap parser wajib mengembalikan ini.
 * Setiap status selalu membawa Reason, Description, dan Recommendation.
 */
data class FirmwareValidation(
    val status: FirmwareValidationStatus,
    val reason: String,
    val description: String,
    val recommendation: String,
    val issues: List<FirmwareIssue> = emptyList(),
) {
    companion object {
        fun ready(
            reason: String,
            description: String,
            recommendation: String,
            issues: List<FirmwareIssue> = emptyList(),
        ) = FirmwareValidation(FirmwareValidationStatus.READY, reason, description, recommendation, issues)

        fun warning(
            reason: String,
            description: String,
            recommendation: String,
            issues: List<FirmwareIssue> = emptyList(),
        ) = FirmwareValidation(FirmwareValidationStatus.WARNING, reason, description, recommendation, issues)

        fun error(
            reason: String,
            description: String,
            recommendation: String,
            issues: List<FirmwareIssue> = emptyList(),
        ) = FirmwareValidation(FirmwareValidationStatus.ERROR, reason, description, recommendation, issues)

        fun unknown(
            reason: String,
            description: String,
            recommendation: String,
            issues: List<FirmwareIssue> = emptyList(),
        ) = FirmwareValidation(FirmwareValidationStatus.UNKNOWN, reason, description, recommendation, issues)
    }
}
