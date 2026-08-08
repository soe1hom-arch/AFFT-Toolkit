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
import com.afft.app.ui.components.dashboard.ValidationPanel
import com.afft.app.ui.components.dashboard.ValidationState
import com.afft.app.util.formatFileSize
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Info satu partisi dari manifest payload. */
data class PartitionInfo(
    val name: String,
    val size: Long,
    val fsType: Int?,
)

/** Hasil decode manifest (protobuf) — best-effort, field tak dikenal di-skip. */
data class ManifestData(
    val partitions: List<PartitionInfo>,
    val minorVersion: Long?,
    val dynamicPartition: Boolean,
    val vabcEnabled: Boolean?,
    val compression: String?,
    val filesystem: String?,
)

/**
 * Parser payload.bin untuk analisis saja (tidak mengekstrak firmware).
 * Membaca header + manifest, menghitung SHA-256 secara streaming,
 * lalu memetakan ke [FirmwareMetadata] untuk FirmwareInspector.
 * Tidak pernah crash — selalu mengembalikan [ParserResult].
 */
object PayloadParser {

    private const val PAYLOAD_MAGIC = "CrAU"
    private const val SUPPORTED_VERSION = 2L

    // Header payload asli (update_engine) = magic(4) + version(4) + manifest_size(8)
    // + metadata_signature_size(4) + padding(4) = 24 byte, semua big-endian.
    private const val PAYLOAD_HEADER_SIZE = 24L

    private const val HEADER_MIN_SIZE = PAYLOAD_HEADER_SIZE
    // Manifest payload nyata < 1 MB (per-partisi ~60 byte). Batas 16 MB
    // cukup longgar untuk firmware sangat besar tanpa mengizinkan alokasi
    // memori berlebih dari header nakal/corrupt (hotfix RC).
    private const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
    private const val STREAM_BUFFER = 1 shl 16

    fun analyze(file: File): ParserResult<FirmwareMetadata> =
        if (!file.exists()) {
            ParserResult.Failure(ParserStatus.FILE_NOT_FOUND, "File not found: ${file.name}")
        } else {
            try {
                ParserResult.Success(analyzeOrThrow(file))
            } catch (e: ParserException) {
                ParserResult.Failure(e.status, e.message)
            } catch (e: Exception) {
                ParserResult.Failure(ParserStatus.READ_ERROR, e.message ?: "Unable to read payload")
            }
        }

    /** Mengubah kegagalan parser menjadi FirmwareMetadata dengan panel ERROR. */
    fun failureMetadata(failure: ParserResult.Failure): FirmwareMetadata {
        val action =
            when (failure.status) {
                ParserStatus.FILE_NOT_FOUND -> "Ensure the file exists and is still available"
                ParserStatus.PERMISSION_DENIED -> "Grant the app read access to this file"
                ParserStatus.INVALID_PAYLOAD, ParserStatus.INVALID_HEADER -> "Select a valid payload.bin file"
                ParserStatus.CORRUPTED_METADATA -> "Re-download the firmware file"
                ParserStatus.UNSUPPORTED_VERSION -> "Update AFFT binaries"
                else -> "Try again or re-select the file"
            }
        return FirmwareMetadata(
            headline = "Firmware Inspector",
            healthScore = 0,
            healthLevel = HealthLevel.CRITICAL,
            healthIndicators = listOf(HealthIndicator(failure.reason, IndicatorKind.CRITICAL)),
            validationPanel =
                ValidationPanel(
                    status = ValidationState.ERROR,
                    reason = failure.reason,
                    suggestedAction = action,
                    estimatedCompatibility = "Analysis failed",
                ),
            sections = emptyList(),
        )
    }

    /**
     * Bantuan diagnostik saat magic tidak cocok: cek apakah file sebenarnya
     * adalah tipe image lain (boot/super/filesystem) supaya pengguna tahu
     * bahwa file yang dipilih bukan payload.bin.
     */
    private fun magicMismatchHint(file: File): String {
        val detected =
            when {
                BootAnalyzer.isBootImage(file) -> "boot image"
                SuperAnalyzer.isSuperImage(file) -> "super image"
                FilesystemAnalyzer.detectType(file) != null -> "filesystem image"
                else -> null
            }
        return detected?.let { " — this file looks like a $it, not a payload.bin" } ?: ""
    }

    private fun analyzeOrThrow(file: File): FirmwareMetadata {
        val raf =
            try {
                RandomAccessFile(file, "r")
            } catch (e: SecurityException) {
                throw ParserException(ParserStatus.PERMISSION_DENIED, "Permission denied", e)
            } catch (e: IOException) {
                throw ParserException(ParserStatus.FILE_NOT_FOUND, "Cannot open file: ${file.name}", e)
            }
        return raf.use { parseWithRandomAccess(it, file) }
    }

    private fun parseWithRandomAccess(raf: RandomAccessFile, file: File): FirmwareMetadata {
        val fileSize = file.length()
        if (fileSize < HEADER_MIN_SIZE) {
            throw ParserException(ParserStatus.INVALID_PAYLOAD, "File too small to be a valid payload")
        }

        raf.seek(0)
        val magic = ByteArray(4)
        raf.readFully(magic)
        val magicString = String(magic, Charsets.UTF_8)
        if (magicString != PAYLOAD_MAGIC) {
            val hex = magic.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            throw ParserException(
                ParserStatus.INVALID_HEADER,
                "Invalid header (expected magic '$PAYLOAD_MAGIC', got bytes: $hex)" + magicMismatchHint(file),
            )
        }

        // Layout nyata payload.bin (update_engine), semua big-endian:
        //   offset 4 : uint32 version
        //   offset 8 : uint64 manifest_size
        //   offset 16: uint32 metadata_signature_size
        //   offset 20: uint32 padding
        //   offset 24: manifest protobuf
        val version = raf.readInt().toLong() and 0xFFFFFFFFL
        if (version != SUPPORTED_VERSION) {
            throw ParserException(
                ParserStatus.UNSUPPORTED_VERSION,
                "Unsupported payload version $version (expected $SUPPORTED_VERSION)",
            )
        }

        val manifestSize = raf.readLong()
        val signatureSize = raf.readInt().toLong() and 0xFFFFFFFFL
        raf.skipBytes(4) // padding

        if (manifestSize == 0L || manifestSize > MAX_MANIFEST_BYTES || raf.filePointer + manifestSize > raf.length()) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Manifest metadata is out of bounds")
        }

        val manifestBytes = ByteArray(manifestSize.toInt())
        raf.readFully(manifestBytes)

        val signaturePresent = signatureSize > 0L && raf.filePointer + signatureSize <= raf.length()

        val manifest = decodeManifest(manifestBytes)
        val minor = manifest?.minorVersion
        val partitionCount = manifest?.partitions?.size ?: 0

        val outcome =
            PayloadAnalyzer.analyze(
                PayloadAnalyzer.AnalyzerInput(
                    formatVersion = version,
                    minorVersion = minor,
                    partitionCount = partitionCount,
                    manifestWellFormed = manifest != null,
                    corruptionDetected = manifest == null,
                    metadataSignaturePresent = signaturePresent,
                    optionalMetadataMissing = optionalMetadataCount(manifest),
                ),
            )

        val result =
            PayloadAnalysisResult(
                filename = file.name,
                fileSize = fileSize,
                // SHA-256 TIDAK dihitung di sini (hotfix RC): menghitung hash
                // payload besar (8+ GB) memblokir analisis metadata. Dihitung
                // on-demand via computeSha256Hex() (lazy/opsional).
                sha256Hex = "", 
                lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified())),
                formatVersion = version,
                minorVersion = minor,
                partitionCount = partitionCount,
                partitionNames = manifest?.partitions?.map { it.name } ?: emptyList(),
                dynamicPartition = manifest?.dynamicPartition ?: false,
                vabcEnabled = manifest?.vabcEnabled,
                compression = manifest?.compression,
                filesystem = manifest?.filesystem,
                architecture = null,
                androidVersion = null,
                deviceCodename = null,
                deviceName = null,
                buildFingerprint = null,
                securityPatch = null,
                healthScore = outcome.healthScore,
                health = outcome.health,
                indicators = outcome.indicators,
                validation = outcome.validation,
                validationReason = outcome.validationReason,
                suggestedAction = outcome.suggestedAction,
                suggestedCompatibility = outcome.suggestedCompatibility,
                warnings = outcome.warnings,
            )

        return toFirmwareMetadata(result)
    }

    private fun optionalMetadataCount(manifest: ManifestData?): Int {
        if (manifest == null) return 0
        var missing = 0
        if (manifest.compression == null) missing++
        if (manifest.filesystem == null) missing++
        return if (missing > 1) 1 else missing
    }

    /**
     * Menghitung SHA-256 payload.bin secara streaming (buffer 64 KB).
     * On-demand / background — TIDAK dipanggil dari jalur analisis metadata
     * agar metadata tampil seketika meski file sangat besar.
     */
    fun computeSha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        DigestInputStream(FileInputStream(file), md).use { input ->
            val buffer = ByteArray(STREAM_BUFFER)
            while (input.read(buffer) >= 0) {
                // digest diperbarui otomatis oleh DigestInputStream
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- decode manifest (protobuf, best-effort & aman) ----------

    private fun decodeManifest(bytes: ByteArray): ManifestData? {
        return try {
            val reader = ProtoReader(bytes)
            val partitions = ArrayList<PartitionInfo>()
            var minor: Long? = null
            var dynamicPartition = false
            var vabc: Boolean? = null
            var compression: String? = null

            while (reader.hasNext) {
                val tag = reader.readTag()
                val field = tag ushr 3
                val wire = tag and 0x07
                when {
                    field == 1 && wire == 2 -> {
                        decodePartition(reader.readBytes())?.let(partitions::add)
                    }
                    field == 6 && wire == 2 -> {
                        val dynamic = decodeDynamicMetadata(reader.readBytes())
                        dynamicPartition = true
                        vabc = dynamic?.first
                        compression = compression ?: dynamic?.second
                    }
                    field == 7 && wire == 0 -> minor = reader.readVarint()
                    else -> reader.skipField(wire)
                }
            }

            val dominantFs =
                partitions.asSequence()
                    .mapNotNull { it.fsType }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

            ManifestData(
                partitions = partitions,
                minorVersion = minor,
                dynamicPartition = dynamicPartition,
                vabcEnabled = vabc,
                compression = compression,
                filesystem = dominantFs?.filesystemName(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun decodePartition(bytes: ByteArray): PartitionInfo? {
        return try {
            val reader = ProtoReader(bytes)
            var name: String? = null
            var size = 0L
            var fs: Int? = null
            while (reader.hasNext) {
                val tag = reader.readTag()
                val field = tag ushr 3
                val wire = tag and 0x07
                when {
                    field == 1 && wire == 2 -> name = reader.readString()
                    field == 2 && wire == 0 -> size = reader.readVarint()
                    field == 7 && wire == 0 -> fs = reader.readVarint().toInt()
                    else -> reader.skipField(wire)
                }
            }
            if (name.isNullOrBlank()) null else PartitionInfo(name = name, size = size, fsType = fs)
        } catch (e: Exception) {
            null
        }
    }

    /** Kembalian: (vabc_enabled?, compression_from_decompressor?) */
    private fun decodeDynamicMetadata(bytes: ByteArray): Pair<Boolean?, String?>? {
        return try {
            val reader = ProtoReader(bytes)
            val list = ArrayList<String>()
            var vabc: Boolean? = null
            var decompressor: String? = null
            while (reader.hasNext) {
                val tag = reader.readTag()
                val field = tag ushr 3
                val wire = tag and 0x07
                when {
                    field == 1 && wire == 2 -> list.add(reader.readString())
                    field == 2 && wire == 0 -> vabc = reader.readVarint() != 0L
                    field == 5 && wire == 2 -> decompressor = reader.readString()
                    field == 6 && wire == 2 -> reader.skipBytes()
                    else -> reader.skipField(wire)
                }
            }
            (vabc to decompressor)
        } catch (e: Exception) {
            null
        }
    }

    private fun Int.filesystemName(): String? =
        when (this) {
            4 -> "ext4"
            5 -> "vfat"
            6 -> "erofs"
            7 -> "squashfs"
            8 -> "f2fs"
            else -> null
        }

    // ---------- pemetaan ke model UI ----------

    private fun toFirmwareMetadata(r: PayloadAnalysisResult): FirmwareMetadata {
        val general =
            InspectorSection(
                title = "General",
                rows =
                    listOf(
                        InspectorRow("Filename", r.filename, copyable = true),
                        InspectorRow("File Size", formatFileSize(r.fileSize)),
                        InspectorRow(
                            "SHA-256",
                            r.sha256Hex.ifEmpty { "Not computed" },
                            copyable = true,
                            tooltip = "SHA-256 hash dari payload.bin (dihitung on-demand)",
                        ),
                        InspectorRow("Last Modified", r.lastModified),
                    ),
            )
        val android =
            InspectorSection(
                title = "Android",
                rows =
                    listOf(
                        InspectorRow("Android Version", r.androidVersion ?: "Unknown"),
                        InspectorRow("Device Codename", r.deviceCodename ?: "Unknown"),
                        InspectorRow("Device Name", r.deviceName ?: "Unknown"),
                        InspectorRow("Build Fingerprint", r.buildFingerprint ?: "Unknown"),
                        InspectorRow("Security Patch", r.securityPatch ?: "Unknown"),
                    ),
            )
        val technical =
            InspectorSection(
                title = "Technical",
                defaultExpanded = false,
                rows =
                    listOf(
                        InspectorRow("Dynamic Partition", if (r.dynamicPartition) "Yes" else "No"),
                        InspectorRow(
                            "VABC",
                            when (r.vabcEnabled) {
                                true -> "Enabled"
                                false -> "Disabled"
                                null -> "Unknown"
                            },
                        ),
                        InspectorRow("Compression", r.compression ?: "Unknown"),
                        InspectorRow("Partition Count", r.partitionCount.toString()),
                        InspectorRow("Filesystem", r.filesystem ?: "Unknown (per-partition)"),
                        InspectorRow("Architecture", r.architecture ?: "Unknown"),
                        InspectorRow(
                            "Partition Names",
                            r.partitionNames.joinToString(", ").ifEmpty { "—" },
                            copyable = true,
                        ),
                    ),
            )
        return FirmwareMetadata(
            headline = "Firmware Inspector",
            healthScore = r.healthScore,
            healthLevel = r.health.toHealthLevel(),
            healthIndicators = r.indicators.map { it.toHealthIndicator() },
            validationPanel =
                ValidationPanel(
                    status = r.validation.toValidationState(),
                    reason = r.validationReason,
                    suggestedAction = r.suggestedAction,
                    estimatedCompatibility = r.suggestedCompatibility,
                ),
            sections = listOf(general, android, technical),
        )
    }

    private fun HealthStatus.toHealthLevel(): HealthLevel =
        when (this) {
            HealthStatus.EXCELLENT -> HealthLevel.EXCELLENT
            HealthStatus.GOOD -> HealthLevel.GOOD
            HealthStatus.WARNING -> HealthLevel.WARNING
            HealthStatus.CRITICAL -> HealthLevel.CRITICAL
        }

    private fun IndicatorSpec.toHealthIndicator(): HealthIndicator =
        HealthIndicator(
            text = text,
            kind =
                when (tone) {
                    IndicatorTone.POSITIVE -> IndicatorKind.POSITIVE
                    IndicatorTone.WARNING -> IndicatorKind.WARNING
                    IndicatorTone.CRITICAL -> IndicatorKind.CRITICAL
                },
        )

    private fun ValidationLevel.toValidationState(): ValidationState =
        when (this) {
            ValidationLevel.READY -> ValidationState.READY
            ValidationLevel.WARNING -> ValidationState.WARNING
            ValidationLevel.ERROR -> ValidationState.ERROR
            ValidationLevel.UNKNOWN -> ValidationState.UNKNOWN
        }
}
