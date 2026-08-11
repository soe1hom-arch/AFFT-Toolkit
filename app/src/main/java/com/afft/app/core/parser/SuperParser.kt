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
import com.afft.app.ui.components.dashboard.InspectorRow
import com.afft.app.ui.components.dashboard.InspectorSection
import com.afft.app.ui.components.dashboard.StatusType
import com.afft.app.ui.components.dashboard.ValidationPanel
import com.afft.app.ui.components.dashboard.ValidationState
import com.afft.app.util.formatFileSize
import java.io.File

/**
 * Parser super.img — implementasi [FirmwareParser].
 *
 * Membaca metadata logical partition (via [SuperAnalyzer]) TANPA
 * mengekstrak/men-mount partisi, memetakan ke [FirmwareMetadata] untuk
 * FirmwareInspector, dan menyuplai [FirmwareValidation] ke
 * [FirmwareHealthCalculator] agar skor konsisten lintas parser.
 */
class SuperParser : FirmwareParser {

    override val name: String = "super"
    override val version: String = "1.0.0"

    /** Deteksi berbasis magic geometry ("gDla"), bukan ekstensi. */
    override fun supportedExtensions(): Set<String> = emptySet()

    override fun supportedMimeTypes(): Set<String> =
        setOf("application/octet-stream", "application/vnd.android.super-image")

    override fun canParse(file: File, context: FirmwareAnalysisContext): Boolean =
        SuperAnalyzer.isSuperImage(file)

    override fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata =
        when (val result = SuperAnalyzer.analyze(file)) {
            is ParserResult.Success -> toFirmwareMetadata(result.data)
            is ParserResult.Failure -> throw toException(result)
        }

    override fun validate(metadata: FirmwareMetadata): FirmwareValidation {
        val panel =
            metadata.validationPanel
                ?: return FirmwareValidation.ready("Supported super image", "Metadata parsed", "Proceed")
        return FirmwareValidation(
            status = panel.status.toFirmwareValidationStatus(),
            reason = panel.reason,
            description = panel.estimatedCompatibility,
            recommendation = panel.suggestedAction,
            issues = metadata.healthIndicators.map { it.toIssue() },
        )
    }

    // ---------- validasi ----------

    private data class SuperValidation(
        val status: FirmwareValidationStatus,
        val reason: String,
        val description: String,
        val recommendation: String,
        val issues: List<FirmwareIssue>,
    )

    private fun buildValidation(r: SuperAnalysisResult): SuperValidation {
        val issues = mutableListOf<FirmwareIssue>()
        if (!r.headerChecksumValid) {
            issues.add(
                FirmwareIssue(
                    "Metadata header checksum mismatch",
                    IssueSeverity.CRITICAL,
                    "Image may be corrupted",
                ),
            )
        }
        if (!r.tablesChecksumValid) {
            issues.add(
                FirmwareIssue(
                    "Metadata tables checksum mismatch",
                    IssueSeverity.CRITICAL,
                    "Image may be corrupted",
                ),
            )
        }
        if (!r.partitionTableIntact) {
            issues.add(
                FirmwareIssue(
                    "Partition table is inconsistent",
                    IssueSeverity.CRITICAL,
                    "Extent or group index out of range",
                ),
            )
        }
        if (r.partitions.isEmpty()) {
            issues.add(
                FirmwareIssue(
                    "Partition table is empty",
                    IssueSeverity.WARNING,
                    "Image has no logical partitions",
                ),
            )
        }
        if (r.blockSize < 512L || (r.blockSize and (r.blockSize - 1L)) != 0L) {
            issues.add(
                FirmwareIssue(
                    "Unusual logical block size",
                    IssueSeverity.WARNING,
                    "Expected power of two, at least 512 bytes",
                ),
            )
        }
        if (r.metadataSlotCount < 1) {
            issues.add(
                FirmwareIssue(
                    "Metadata slot count is zero",
                    IssueSeverity.WARNING,
                    "Geometry may be invalid",
                ),
            )
        }

        val status =
            when {
                !r.headerChecksumValid || !r.tablesChecksumValid || !r.partitionTableIntact ->
                    FirmwareValidationStatus.ERROR
                r.partitions.isEmpty() && r.groups.isEmpty() -> FirmwareValidationStatus.UNKNOWN
                r.partitions.isEmpty() || r.blockSize < 512L || r.metadataSlotCount < 1 ->
                    FirmwareValidationStatus.WARNING
                else -> FirmwareValidationStatus.READY
            }
        val (reason, description, recommendation) =
            when (status) {
                FirmwareValidationStatus.ERROR ->
                    Triple(
                        "Super metadata is corrupted or inconsistent",
                        "Checksum or partition table integrity check failed",
                        "Re-download or re-flash the super image",
                    )
                FirmwareValidationStatus.WARNING ->
                    Triple(
                        "Super metadata parsed with warnings",
                        "Partition table or geometry is incomplete",
                        "Verify the image before flashing",
                    )
                FirmwareValidationStatus.UNKNOWN ->
                    Triple(
                        "Super metadata is not recognized",
                        "No partitions or groups were found",
                        "Verify the file is a valid super.img",
                    )
                else ->
                    Triple(
                        "Valid super image",
                        "Metadata parsed successfully",
                        "Ready for extraction",
                    )
            }
        return SuperValidation(status, reason, description, recommendation, issues)
    }

    // ---------- mapping ke model UI ----------

    private fun toFirmwareMetadata(r: SuperAnalysisResult): FirmwareMetadata {
        val validation = buildValidation(r)
        val versionLabel = "${r.majorVersion}.${r.minorVersion}"

        val general =
            InspectorSection(
                title = "General",
                rows =
                    listOf(
                        InspectorRow("Image Name", r.filename, copyable = true),
                        InspectorRow("File Size", formatFileSize(r.fileSize)),
                        InspectorRow("Metadata Version", versionLabel, copyable = true),
                        InspectorRow("Metadata Header Size", formatFileSize(r.headerSize)),
                        InspectorRow("Tables Size", formatFileSize(r.tablesSize)),
                        InspectorRow("Geometry Struct Size", formatFileSize(r.geometryStructSize)),
                    ),
            )
        val superSection =
            InspectorSection(
                title = "Super",
                rows =
                    listOf(
                        InspectorRow("Dynamic Partitions", "Supported"),
                        InspectorRow("Virtual A/B", if (r.virtualAb) "Enabled" else "Disabled"),
                        InspectorRow("Logical Block Size", formatFileSize(r.blockSize)),
                        InspectorRow("Metadata Max Size", formatFileSize(r.metadataMaxSize)),
                        InspectorRow("Metadata Slots", r.metadataSlotCount.toString()),
                        InspectorRow("Partition Groups", r.groupCount.toString()),
                        InspectorRow("Logical Partitions", r.partitions.size.toString()),
                        InspectorRow("Block Devices", r.blockDeviceCount.toString()),
                        InspectorRow(
                            "Header Checksum",
                            if (r.headerChecksumValid) "Valid" else "Invalid",
                            status = if (r.headerChecksumValid) StatusType.READY else StatusType.ERROR,
                        ),
                        InspectorRow(
                            "Tables Checksum",
                            if (r.tablesChecksumValid) "Valid" else "Invalid",
                            status = if (r.tablesChecksumValid) StatusType.READY else StatusType.ERROR,
                        ),
                    ),
            )
        val partitions =
            InspectorSection(
                title = "Partitions (${r.partitions.size})",
                defaultExpanded = false,
                rows =
                    if (r.partitions.isEmpty()) {
                        listOf(InspectorRow("Partitions", "None found"))
                    } else {
                        r.partitions.map { p ->
                            InspectorRow(
                                label = p.name,
                                value = buildPartitionValue(p),
                                copyable = true,
                            )
                        }
                    },
            )
        val blockDevices =
            InspectorSection(
                title = "Block Devices",
                defaultExpanded = false,
                rows =
                    if (r.blockDevices.isEmpty()) {
                        listOf(InspectorRow("Devices", "None"))
                    } else {
                        r.blockDevices.map { InspectorRow(it, "LUN", copyable = true) }
                    },
            )

        return FirmwareMetadata(
            headline = "Super Inspector",
            healthScore = validationScore(validation),
            healthLevel = HealthLevel.EXCELLENT,
            healthIndicators = validation.issues.map { it.toHealthIndicator() },
            validationPanel =
                ValidationPanel(
                    status = validation.status.toValidationState(),
                    reason = validation.reason,
                    suggestedAction = validation.recommendation,
                    estimatedCompatibility = validation.description,
                ),
            sections = listOf(general, superSection, partitions, blockDevices),
        )
    }

    private fun buildPartitionValue(p: SuperPartition): String {
        val parts = StringBuilder(formatFileSize(p.size))
        parts.append(" \u2022 ").append(p.group)
        p.slot?.let { parts.append(" \u2022 ").append(it) }
        val attrs = attributeLabels(p.attributes)
        if (attrs.isNotEmpty()) parts.append(" \u2022 ").append(attrs.joinToString(","))
        return parts.toString()
    }

    private fun attributeLabels(attributes: Int): List<String> {
        val labels = mutableListOf<String>()
        if (attributes and 0x1 != 0) labels.add("readonly")
        if (attributes and 0x2 != 0) labels.add("slot_suffixed")
        if (attributes and 0x4 != 0) labels.add("updated")
        if (attributes and 0x8 != 0) labels.add("disabled")
        return labels
    }

    private fun validationScore(validation: SuperValidation): Int =
        FirmwareHealthCalculator.calculate(
            FirmwareValidation(
                status = validation.status,
                reason = validation.reason,
                description = validation.description,
                recommendation = validation.recommendation,
                issues = validation.issues,
            ),
        ).score

    private fun toException(failure: ParserResult.Failure): FirmwareAnalysisException =
        when (failure.status) {
            ParserStatus.FILE_NOT_FOUND -> FirmwareAnalysisException.MissingMetadata(failure.reason)
            ParserStatus.PERMISSION_DENIED -> FirmwareAnalysisException.PermissionDenied(failure.reason)
            ParserStatus.INVALID_PAYLOAD, ParserStatus.INVALID_HEADER, ParserStatus.UNSUPPORTED_VERSION ->
                FirmwareAnalysisException.UnsupportedFormat(failure.reason)
            ParserStatus.CORRUPTED_METADATA -> FirmwareAnalysisException.CorruptedImage(failure.reason)
            else -> FirmwareAnalysisException.ParserFailure(failure.reason)
        }

    // ---------- pemetaan enum ----------

    private fun FirmwareIssue.toHealthIndicator(): HealthIndicator =
        HealthIndicator(
            text = message,
            kind =
                when (severity) {
                    IssueSeverity.CRITICAL -> IndicatorKind.CRITICAL
                    else -> IndicatorKind.WARNING
                },
        )

    private fun HealthIndicator.toIssue(): FirmwareIssue =
        FirmwareIssue(
            message = text,
            severity =
                when (kind) {
                    IndicatorKind.CRITICAL -> IssueSeverity.CRITICAL
                    IndicatorKind.WARNING -> IssueSeverity.WARNING
                    else -> IssueSeverity.MINOR
                },
        )

    private fun FirmwareValidationStatus.toValidationState(): ValidationState =
        when (this) {
            FirmwareValidationStatus.READY -> ValidationState.READY
            FirmwareValidationStatus.WARNING -> ValidationState.WARNING
            FirmwareValidationStatus.ERROR -> ValidationState.ERROR
            FirmwareValidationStatus.UNKNOWN -> ValidationState.UNKNOWN
        }

    private fun FirmwareValidationStatus.toStatusType(): StatusType =
        when (this) {
            FirmwareValidationStatus.READY -> StatusType.READY
            FirmwareValidationStatus.WARNING -> StatusType.WARNING
            FirmwareValidationStatus.ERROR -> StatusType.ERROR
            FirmwareValidationStatus.UNKNOWN -> StatusType.INFO
        }

    private fun ValidationState.toFirmwareValidationStatus(): FirmwareValidationStatus =
        when (this) {
            ValidationState.READY -> FirmwareValidationStatus.READY
            ValidationState.WARNING -> FirmwareValidationStatus.WARNING
            ValidationState.ERROR -> FirmwareValidationStatus.ERROR
            ValidationState.UNKNOWN -> FirmwareValidationStatus.UNKNOWN
        }
}
