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
import com.afft.app.ui.components.dashboard.HealthLevel
import com.afft.app.ui.components.dashboard.IndicatorKind
import com.afft.app.ui.components.dashboard.ValidationPanel
import com.afft.app.ui.components.dashboard.ValidationState
import java.io.File

/** Hasil analisis engine. */
sealed class EngineResult {
    data class Success(
        val metadata: FirmwareMetadata,
        val context: FirmwareAnalysisContext,
    ) : EngineResult()

    data class Failure(
        val error: FirmwareAnalysisException,
        val context: FirmwareAnalysisContext,
    ) : EngineResult()
}

/**
 * Mesin analisis firmware generik.
 *
 * Tanggung jawab:
 *   1. terima file
 *   2. deteksi tipe firmware → [FirmwareParserRegistry.detect]
 *   3. pilih parser
 *   4. [FirmwareParser.analyze]
 *   5. [FirmwareParser.validate] → [FirmwareValidation]
 *   6. [FirmwareParser.calculateHealth] → [FirmwareHealthResult]
 *   7. kembalikan [FirmwareMetadata] final
 *
 * Engine TIDAK tahu detail implementasi parser — hanya kontrak interface.
 * Kegagalan selalu dibungkus [EngineResult.Failure] dengan
 * [FirmwareAnalysisException] terstruktur, tidak pernah dilempar ke caller.
 */
class FirmwareAnalysisEngine(
    private val registry: FirmwareParserRegistry,
) {

    /** Mencari parser terdaftar berdasarkan nama (untuk deteksi/auto-select file). */
    fun parser(name: String): FirmwareParser? = registry.find(name)

    fun analyze(file: File): EngineResult {
        var context = FirmwareAnalysisContext.start(file)
        if (!file.exists()) {
            return fail(context, FirmwareAnalysisException.MissingMetadata("File not found: ${file.name}"))
        }

        val parser =
            registry.detect(file, context)
                ?: return fail(context, FirmwareAnalysisException.UnsupportedFormat("No parser supports: ${file.name}"))

        context = context.withParser(parser.name, parser.version)
        return try {
            val raw = parser.analyze(file, context)
            val validation = parser.validate(raw)
            val health = parser.calculateHealth(validation)
            EngineResult.Success(merge(parser, raw, validation, health), context.finished())
        } catch (e: FirmwareAnalysisException) {
            fail(context, e)
        } catch (e: SecurityException) {
            fail(context, FirmwareAnalysisException.PermissionDenied("Permission denied: ${file.name}", e))
        } catch (e: Exception) {
            fail(context, FirmwareAnalysisException.ParserFailure("Parser '${parser.name}' failed: ${e.message}", e))
        }
    }

    private fun merge(
        parser: FirmwareParser,
        metadata: FirmwareMetadata,
        validation: FirmwareValidation,
        health: FirmwareHealthResult,
    ): FirmwareMetadata =
        metadata.copy(
            healthScore = health.score,
            healthLevel = health.health.toHealthLevel(),
            healthIndicators = health.contributions.map { it.toHealthIndicator() },
            validationPanel =
                ValidationPanel(
                    status = validation.status.toValidationState(),
                    reason = validation.reason,
                    suggestedAction = validation.recommendation,
                    estimatedCompatibility = validation.description,
                ),
        )

    private fun fail(context: FirmwareAnalysisContext, error: FirmwareAnalysisException): EngineResult.Failure =
        EngineResult.Failure(error, context.finished())

    private fun FirmwareHealth.toHealthLevel(): HealthLevel =
        when (this) {
            FirmwareHealth.EXCELLENT -> HealthLevel.EXCELLENT
            FirmwareHealth.GOOD -> HealthLevel.GOOD
            FirmwareHealth.WARNING -> HealthLevel.WARNING
            FirmwareHealth.CRITICAL -> HealthLevel.CRITICAL
        }

    private fun IssueSeverity.toIndicatorKind(): IndicatorKind =
        when (this) {
            IssueSeverity.MINOR -> IndicatorKind.WARNING
            IssueSeverity.WARNING -> IndicatorKind.WARNING
            IssueSeverity.CRITICAL -> IndicatorKind.CRITICAL
        }

    private fun FirmwareIssue.toHealthIndicator(): HealthIndicator =
        HealthIndicator(message, severity.toIndicatorKind())

    private fun FirmwareValidationStatus.toValidationState(): ValidationState =
        when (this) {
            FirmwareValidationStatus.READY -> ValidationState.READY
            FirmwareValidationStatus.WARNING -> ValidationState.WARNING
            FirmwareValidationStatus.ERROR -> ValidationState.ERROR
            FirmwareValidationStatus.UNKNOWN -> ValidationState.UNKNOWN
        }
}
