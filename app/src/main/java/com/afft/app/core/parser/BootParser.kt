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
import java.util.Locale

/**
 * Parser boot image — implementasi [FirmwareParser].
 *
 * Membaca metadata header (via [BootAnalyzer]) TANPA membongkar image,
 * memetakan ke [FirmwareMetadata] untuk FirmwareInspector, dan menyuplai
 * [FirmwareValidation] ke [FirmwareHealthCalculator] agar skor tetap
 * konsisten dengan parser lain.
 *
 * Kegagalan struktur dilempar sebagai [FirmwareAnalysisException] agar
 * [FirmwareAnalysisEngine] mengembalikan [EngineResult.Failure] dan
 * Workspace mencatat operasi gagal.
 */
class BootParser : FirmwareParser {

    override val name: String = "boot"
    override val version: String = "1.0.0"

    /** Deteksi berbasis magic "ANDROID!", bukan ekstensi (.img dipakai banyak tipe). */
    override fun supportedExtensions(): Set<String> = emptySet()

    override fun supportedMimeTypes(): Set<String> =
        setOf("application/octet-stream", "application/vnd.android.boot-image")

    override fun canParse(file: File, context: FirmwareAnalysisContext): Boolean =
        BootAnalyzer.isBootImage(file) || BootAnalyzer.isVendorBootImage(file)

    override fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata =
        when (val result = BootAnalyzer.analyze(file)) {
            is ParserResult.Success -> toFirmwareMetadata(result.data)
            is ParserResult.Failure -> throw toException(result)
        }

    override fun validate(metadata: FirmwareMetadata): FirmwareValidation {
        val panel =
            metadata.validationPanel
                ?: return FirmwareValidation.ready("Supported boot image", "Header parsed", "Proceed")
        return FirmwareValidation(
            status = panel.status.toFirmwareValidationStatus(),
            reason = panel.reason,
            description = panel.estimatedCompatibility,
            recommendation = panel.suggestedAction,
            issues = metadata.healthIndicators.map { it.toIssue() },
        )
    }

    // ---------- validasi ----------

    private data class BootValidation(
        val status: FirmwareValidationStatus,
        val reason: String,
        val description: String,
        val recommendation: String,
        val issues: List<FirmwareIssue>,
    )

    private fun buildValidation(r: BootAnalysisResult): BootValidation {
        val issues = mutableListOf<FirmwareIssue>()
        if (r.vendorBoot) {
            if (!r.ramdiskPresent) {
                issues.add(
                    FirmwareIssue(
                        "Vendor ramdisk not present",
                        IssueSeverity.WARNING,
                        "Verify the vendor_boot layout before flashing",
                    ),
                )
            }
            if (r.dtbSize == 0L) {
                issues.add(
                    FirmwareIssue(
                        "Device tree not present",
                        IssueSeverity.WARNING,
                        "Board may not ship a DTB in vendor_boot",
                    ),
                )
            }
        } else if (r.kernelSize == 0L) {
            issues.add(
                FirmwareIssue(
                    "Kernel section is empty",
                    IssueSeverity.CRITICAL,
                    "Image is corrupted or not a boot image",
                ),
            )
        }
        if (!r.vendorBoot) {
            if (!r.ramdiskPresent) {
                issues.add(
                    FirmwareIssue(
                        "Ramdisk not present",
                        IssueSeverity.WARNING,
                        "Verify boot layout (boot vs init_boot)",
                    ),
                )
            }
        }
        if (!r.vendorBoot && r.architecture == null) {
            issues.add(
                FirmwareIssue(
                    "Kernel architecture not detected",
                    IssueSeverity.MINOR,
                    "Kernel is compressed or not an ELF header",
                ),
            )
        }
        if (r.vendorBoot) {
            if (r.cmdline.isBlank()) {
                issues.add(
                    FirmwareIssue(
                        "Vendor command line is empty",
                        IssueSeverity.MINOR,
                        "Some devices boot without a vendor cmdline",
                    ),
                )
            }
        } else {
            if (r.osVersion == null && r.securityPatchLevel == null) {
                issues.add(
                    FirmwareIssue(
                        "OS version metadata is absent",
                        IssueSeverity.MINOR,
                        "Header may omit OS version fields",
                    ),
                )
            }
            if (r.cmdline.isBlank()) {
                issues.add(
                    FirmwareIssue(
                        "Kernel command line is empty",
                        IssueSeverity.MINOR,
                        "Some devices boot without cmdline",
                    ),
                )
            }
        }

        val status =
            if (r.vendorBoot) {
                when {
                    !r.ramdiskPresent -> FirmwareValidationStatus.WARNING
                    else -> FirmwareValidationStatus.READY
                }
            } else {
                when {
                    r.kernelSize == 0L -> FirmwareValidationStatus.ERROR
                    !r.ramdiskPresent -> FirmwareValidationStatus.WARNING
                    r.osVersion == null && r.securityPatchLevel == null && r.cmdline.isBlank() ->
                        FirmwareValidationStatus.UNKNOWN
                    else -> FirmwareValidationStatus.READY
                }
            }
        val (reason, description, recommendation) =
            when (status) {
                FirmwareValidationStatus.ERROR ->
                    if (r.vendorBoot) {
                        Triple(
                            "Vendor boot image is invalid or corrupted",
                            "Vendor ramdisk missing from vendor_boot image",
                            "Re-download or rebuild the vendor_boot image",
                        )
                    } else {
                        Triple(
                            "Boot image is invalid or corrupted",
                            "Kernel section missing from boot image",
                            "Re-download or rebuild the boot image",
                        )
                    }
                FirmwareValidationStatus.WARNING ->
                    if (r.vendorBoot) {
                        Triple(
                            "Vendor boot image parsed with warnings",
                            "Vendor ramdisk or DTB is missing",
                            "Verify the vendor_boot layout before flashing",
                        )
                    } else {
                        Triple(
                            "Boot image parsed with warnings",
                            "Ramdisk is missing or architecture is unknown",
                            "Verify the boot image layout before flashing",
                        )
                    }
                FirmwareValidationStatus.UNKNOWN ->
                    Triple(
                        "Boot image is not recognized",
                        "No OS version, patch level, or cmdline detected",
                        "Verify the file is a standard Android boot image",
                    )
                else ->
                    if (r.vendorBoot) {
                        Triple(
                            "Valid vendor boot image",
                            "Header parsed successfully",
                            "Ready for extraction",
                        )
                    } else {
                        Triple(
                            "Valid boot image",
                            "Header parsed successfully",
                            "Ready for extraction",
                        )
                    }
            }
        return BootValidation(status, reason, description, recommendation, issues)
    }

    // ---------- mapping ke model UI ----------

    private fun toFirmwareMetadata(r: BootAnalysisResult): FirmwareMetadata {
        val validation = buildValidation(r)
        val headerVersion = if (r.vendorBoot) "v3/v4 (vendor)" else "v${r.headerVersion}"

        val general =
            InspectorSection(
                title = "General",
                rows =
                    listOf(
                        InspectorRow("Image Name", r.productName ?: r.filename, copyable = true),
                        InspectorRow("Image Type", if (r.vendorBoot) "Vendor Boot" else "Boot"),
                        InspectorRow("Boot Image", r.filename, copyable = true),
                        InspectorRow("File Size", formatFileSize(r.fileSize)),
                        InspectorRow("Header Version", headerVersion, copyable = true),
                        InspectorRow("Header Size", formatFileSize(r.headerSize)),
                        InspectorRow(
                            "Page Size",
                            r.pageSize?.let { formatFileSize(it) } ?: "n/a ($headerVersion+)",
                        ),
                    ),
            )
        val android =
            InspectorSection(
                title = "Android",
                rows =
                    listOf(
                        InspectorRow("OS Version", r.osVersion ?: "Unknown"),
                        InspectorRow("Security Patch Level", r.securityPatchLevel ?: "Unknown"),
                    ),
            )
        val boot =
            InspectorSection(
                title = "Boot",
                defaultExpanded = false,
                rows =
                    listOf(
                        InspectorRow(
                            "Kernel Size",
                            if (r.vendorBoot) "— (no kernel in vendor_boot)" else formatFileSize(r.kernelSize),
                        ),
                        InspectorRow(
                            "Kernel Address",
                            if (r.vendorBoot) "n/a" else r.kernelAddress?.let { "0x%08x".format(it, Locale.US) } ?: "n/a ($headerVersion+)",
                        ),
                        InspectorRow(
                            if (r.vendorBoot) "Vendor Command Line" else "Kernel Command Line",
                            r.cmdline.ifEmpty { "—" },
                            copyable = true,
                        ),
                        InspectorRow(
                            "Ramdisk Status",
                            if (r.ramdiskPresent) "Present" else "Missing",
                            status = validation.status.toStatusType(),
                        ),
                    ),
            )
        val ramdisk =
            InspectorSection(
                title = "Ramdisk",
                defaultExpanded = false,
                rows =
                    listOf(
                        InspectorRow("Ramdisk Size", formatFileSize(r.ramdiskSize)),
                        InspectorRow(
                            "Ramdisk Address",
                            r.ramdiskAddress?.let { "0x%08x".format(it, Locale.US) } ?: "n/a ($headerVersion+)",
                        ),
                        InspectorRow("Compression", r.ramdiskCompression ?: "Unknown"),
                    ),
            )
        val security =
            InspectorSection(
                title = "Security",
                defaultExpanded = false,
                rows =
                    listOf(
                        InspectorRow("AVB Footer", if (r.avbFooterPresent) "Detected" else "Not detected"),
                        InspectorRow("Device Tree", if (r.deviceTreePresent) "Present" else "Not present"),
                        InspectorRow("Second Stage", if (r.secondSize > 0L) formatFileSize(r.secondSize) else "—"),
                        InspectorRow("DTB Size", if (r.dtbSize > 0L) formatFileSize(r.dtbSize) else "—"),
                        InspectorRow("DTBO Size", if (r.dtboSize > 0L) formatFileSize(r.dtboSize) else "—"),
                    ),
            )

        return FirmwareMetadata(
            headline = if (r.vendorBoot) "Vendor Boot Inspector" else "Boot Inspector",
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
            sections = listOf(general, android, boot, ramdisk, security),
        )
    }

    private fun validationScore(validation: BootValidation): Int =
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
