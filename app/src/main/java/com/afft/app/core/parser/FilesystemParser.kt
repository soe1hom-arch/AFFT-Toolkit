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
 * Parser filesystem image (EROFS / EXT4 / F2FS) — implementasi [FirmwareParser].
 *
 * Membaca metadata superblock (via [FilesystemAnalyzer]) TANPA men-mount atau
 * mengekstrak image, memetakan ke [FirmwareMetadata] untuk FirmwareInspector,
 * dan menyuplai [FirmwareValidation] ke [FirmwareHealthCalculator] agar skor
 * konsisten dengan parser lain.
 *
 * Image multi-GB aman: hanya blok awal (< 4 KB) yang dibaca.
 */
class FilesystemParser : FirmwareParser {

    override val name: String = "filesystem"
    override val version: String = "1.0.0"

    /** Deteksi berbasis magic superblock (offset 1024), bukan ekstensi. */
    override fun supportedExtensions(): Set<String> = emptySet()

    override fun supportedMimeTypes(): Set<String> =
        setOf("application/octet-stream", "application/vnd.android.filesystem-image")

    override fun canParse(file: File, context: FirmwareAnalysisContext): Boolean =
        FilesystemAnalyzer.detectType(file) != null

    override fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata =
        when (val result = FilesystemAnalyzer.analyze(file)) {
            is ParserResult.Success -> toFirmwareMetadata(result.data)
            is ParserResult.Failure -> throw toException(result)
        }

    override fun validate(metadata: FirmwareMetadata): FirmwareValidation {
        val panel =
            metadata.validationPanel
                ?: return FirmwareValidation.ready("Supported filesystem image", "Superblock parsed", "Proceed")
        return FirmwareValidation(
            status = panel.status.toFirmwareValidationStatus(),
            reason = panel.reason,
            description = panel.estimatedCompatibility,
            recommendation = panel.suggestedAction,
            issues = metadata.healthIndicators.map { it.toIssue() },
        )
    }

    // ---------- validasi ----------

    private data class FsValidation(
        val status: FirmwareValidationStatus,
        val reason: String,
        val description: String,
        val recommendation: String,
        val issues: List<FirmwareIssue>,
    )

    private fun buildValidation(r: FilesystemAnalysisResult): FsValidation {
        val issues = mutableListOf<FirmwareIssue>()

        if (!r.known) {
            issues.add(
                FirmwareIssue(
                    "Unrecognized filesystem superblock",
                    IssueSeverity.WARNING,
                    "Verify the file is a raw EROFS, EXT4, or F2FS image",
                ),
            )
            return FsValidation(
                status = FirmwareValidationStatus.UNKNOWN,
                reason = "Filesystem not recognized",
                description = "No supported superblock magic found at offset 1024",
                recommendation = "Select a raw EROFS, EXT4, or F2FS filesystem image",
                issues = issues,
            )
        }

        if (r.blockSize <= 0L) {
            issues.add(
                FirmwareIssue(
                    "Invalid block size",
                    IssueSeverity.CRITICAL,
                    "Superblock reports a block size that is zero or unsupported",
                ),
            )
        }
        if (r.fsType == "EROFS" && r.compression == null) {
            issues.add(
                FirmwareIssue(
                    "No compression algorithm advertised",
                    IssueSeverity.MINOR,
                    "EROFS images normally advertise LZ4/LZMA/DEFLATE/ZSTD",
                ),
            )
        }
        if (r.fsType == "EXT4") {
            if (r.state?.contains("Errors") == true) {
                issues.add(
                    FirmwareIssue(
                        "Filesystem state reports errors",
                        IssueSeverity.WARNING,
                        "Run fsck / clean the filesystem before flashing",
                    ),
                )
            }
            if (r.journalSupport == false) {
                issues.add(
                    FirmwareIssue(
                        "Journal not detected",
                        IssueSeverity.MINOR,
                        "Image may use an ext2/ext3 layout",
                    ),
                )
            }
        }
        if (r.fsType == "F2FS" && r.checkpointVersion == null) {
            issues.add(
                FirmwareIssue(
                    "Checkpoint version missing",
                    IssueSeverity.MINOR,
                    "Image may be truncated or the checkpoint area is unreadable",
                ),
            )
        }

        val status =
            when {
                r.blockSize <= 0L -> FirmwareValidationStatus.ERROR
                r.state?.contains("Errors") == true -> FirmwareValidationStatus.WARNING
                else -> FirmwareValidationStatus.READY
            }
        val (reason, description, recommendation) =
            when (status) {
                FirmwareValidationStatus.ERROR ->
                    Triple(
                        "Filesystem superblock is invalid",
                        "Block size is zero or unsupported",
                        "Re-extract or re-download the filesystem image",
                    )
                FirmwareValidationStatus.WARNING ->
                    Triple(
                        "Filesystem parsed with warnings",
                        "Superblock reports errors or missing metadata",
                        "Verify the image before flashing",
                    )
                else ->
                    Triple(
                        "Valid filesystem image",
                        "Superblock parsed successfully",
                        "Ready for extraction",
                    )
            }
        return FsValidation(status, reason, description, recommendation, issues)
    }

    // ---------- mapping ke model UI ----------

    private fun toFirmwareMetadata(r: FilesystemAnalysisResult): FirmwareMetadata {
        val validation = buildValidation(r)

        val general =
            InspectorSection(
                title = "General",
                rows =
                    listOf(
                        InspectorRow("Image Name", r.filename, copyable = true),
                        InspectorRow("File Size", formatFileSize(r.fileSize)),
                        InspectorRow(
                            "Filesystem Type",
                            r.fsType,
                            status = validation.status.toStatusType(),
                            copyable = true,
                        ),
                        InspectorRow("Detected", if (r.known) "Superblock magic" else "Not detected"),
                    ),
            )
        val filesystem =
            InspectorSection(
                title = "Filesystem",
                rows =
                    listOf(
                        InspectorRow("Type", r.fsType, copyable = true),
                        InspectorRow("Version", r.version ?: "Unknown"),
                        InspectorRow(
                            "Block Size",
                            if (r.blockSize > 0L) formatFileSize(r.blockSize) else "Unknown",
                        ),
                        InspectorRow("UUID", r.uuid ?: "Unknown", copyable = true),
                        InspectorRow("Volume Name", r.volumeName ?: "Unknown", copyable = true),
                        InspectorRow("State", r.state ?: "—"),
                        InspectorRow("Features", r.features.ifEmpty { listOf("—") }.joinToString(", ")),
                    ),
            )

        return FirmwareMetadata(
            headline = "Filesystem Inspector",
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
            sections = listOf(general, filesystem, technicalSection(r)),
        )
    }

    private fun technicalSection(r: FilesystemAnalysisResult): InspectorSection =
        when (r.fsType) {
            "EROFS" ->
                InspectorSection(
                    title = "EROFS",
                    defaultExpanded = false,
                    rows =
                        listOf(
                            InspectorRow("Block Count", r.blockCount?.toString() ?: "Unknown"),
                            InspectorRow("Compression", r.compression ?: "None advertised"),
                            InspectorRow("Read Only", if (r.readOnly == true) "Yes (by design)" else "No"),
                            InspectorRow("Features", r.features.ifEmpty { listOf("—") }.joinToString(", ")),
                        ),
                )
            "EXT4" ->
                InspectorSection(
                    title = "EXT4",
                    defaultExpanded = false,
                    rows =
                        listOf(
                            InspectorRow("Block Count", r.blockCount?.toString() ?: "Unknown"),
                            InspectorRow("Inode Count", r.inodeCount?.toString() ?: "Unknown"),
                            InspectorRow("Journal", when (r.journalSupport) {
                                true -> "Present"
                                false -> "Not detected"
                                null -> "Unknown"
                            }),
                            InspectorRow("State", r.state ?: "Unknown"),
                        ),
                )
            "F2FS" ->
                InspectorSection(
                    title = "F2FS",
                    defaultExpanded = false,
                    rows =
                        listOf(
                            InspectorRow("Block Count", r.blockCount?.toString() ?: "Unknown"),
                            InspectorRow("Segment Count", r.segmentCount?.toString() ?: "Unknown"),
                            InspectorRow("Checkpoint Version", r.checkpointVersion ?: "Unknown"),
                            InspectorRow("Kernel Version", r.kernelVersion ?: "Unknown"),
                        ),
                )
            else ->
                InspectorSection(
                    title = "Technical",
                    defaultExpanded = false,
                    rows = listOf(InspectorRow("Analysis", "No filesystem superblock detected")),
                )
        }

    private fun validationScore(validation: FsValidation): Int =
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
