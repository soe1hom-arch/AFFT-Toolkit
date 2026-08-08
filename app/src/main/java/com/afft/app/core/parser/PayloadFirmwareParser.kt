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
import com.afft.app.ui.components.dashboard.HealthIndicator
import com.afft.app.ui.components.dashboard.IndicatorKind
import com.afft.app.ui.components.dashboard.ValidationState
import java.io.File
import java.io.RandomAccessFile

/**
 * Adapter PayloadParser ke kontrak [FirmwareParser].
 *
 * Tidak menduplikasi logika parsing sama sekali — seluruh pembacaan &
 * pemetaan metadata ditangani oleh [PayloadParser] (satu-satunya sumber
 * kebenaran untuk parsing payload.bin). Adapter hanya:
 *   - memasangkan nama/ekstensi/MIME,
 *   - deteksi cepat (magic "CrAU"),
 *   - mengubah ParserResult menjadi FirmwareParser contract.
 *
 * Kesalahan parsing dilempar sebagai [FirmwareAnalysisException] agar
 * [FirmwareAnalysisEngine] bisa mengembalikan [EngineResult.Failure]
 * (digunakan oleh Workspace untuk mencatat kegagalan).
 */
class PayloadFirmwareParser : FirmwareParser {

    override val name: String = "payload"
    override val version: String = "1.0.0"

    override fun supportedExtensions(): Set<String> = setOf("bin")

    override fun supportedMimeTypes(): Set<String> =
        setOf("application/octet-stream", "application/vnd.android.payload")

    override fun canParse(file: File, context: FirmwareAnalysisContext): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                String(magic, Charsets.UTF_8) == PAYLOAD_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata =
        when (val result = PayloadParser.analyze(file)) {
            is ParserResult.Success -> result.data
            is ParserResult.Failure -> throw toException(result)
        }

    override fun validate(metadata: FirmwareMetadata): FirmwareValidation {
        val panel = metadata.validationPanel
            ?: return FirmwareValidation.ready("Supported format", "Firmware parsed", "Proceed")
        return FirmwareValidation(
            status = panel.status.toFirmwareValidationStatus(),
            reason = panel.reason,
            description = panel.estimatedCompatibility,
            recommendation = panel.suggestedAction,
            issues = metadata.healthIndicators.map { it.toIssue() },
        )
    }

    private fun toException(failure: ParserResult.Failure): FirmwareAnalysisException =
        when (failure.status) {
            ParserStatus.FILE_NOT_FOUND -> FirmwareAnalysisException.MissingMetadata(failure.reason)
            ParserStatus.PERMISSION_DENIED -> FirmwareAnalysisException.PermissionDenied(failure.reason)
            ParserStatus.INVALID_PAYLOAD, ParserStatus.INVALID_HEADER, ParserStatus.UNSUPPORTED_VERSION ->
                FirmwareAnalysisException.UnsupportedFormat(failure.reason)
            ParserStatus.CORRUPTED_METADATA -> FirmwareAnalysisException.CorruptedImage(failure.reason)
            else -> FirmwareAnalysisException.ParserFailure(failure.reason)
        }

    private fun HealthIndicator.toIssue(): FirmwareIssue =
        FirmwareIssue(
            message = text,
            severity =
                when (kind) {
                    IndicatorKind.POSITIVE -> IssueSeverity.MINOR
                    IndicatorKind.WARNING -> IssueSeverity.WARNING
                    IndicatorKind.CRITICAL -> IssueSeverity.CRITICAL
                },
        )

    private fun ValidationState.toFirmwareValidationStatus(): FirmwareValidationStatus =
        when (this) {
            ValidationState.READY -> FirmwareValidationStatus.READY
            ValidationState.WARNING -> FirmwareValidationStatus.WARNING
            ValidationState.ERROR -> FirmwareValidationStatus.ERROR
            ValidationState.UNKNOWN -> FirmwareValidationStatus.UNKNOWN
        }

    companion object {
        private const val PAYLOAD_MAGIC = "CrAU"
    }
}
