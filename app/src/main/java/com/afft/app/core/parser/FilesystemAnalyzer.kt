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

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Hasil analisis image filesystem — murni, tanpa dependensi UI.
 */
data class FilesystemAnalysisResult(
    val filename: String,
    val fileSize: Long,
    val fsType: String,
    val known: Boolean,
    val version: String?,
    val blockSize: Long,
    val uuid: String?,
    val volumeName: String?,
    val features: List<String>,
    val state: String?,
    val journalSupport: Boolean?,
    val blockCount: Long?,
    val inodeCount: Long?,
    val segmentCount: Long?,
    val checkpointVersion: String?,
    val compression: String?,
    val readOnly: Boolean?,
    val kernelVersion: String?,
)

/**
 * Pembaca metadata filesystem image — EROFS / EXT4 / F2FS.
 *
 * HANYA membaca superblock (offset 1024, < 4 KB). Tidak pernah men-mount,
 * mengekstrak, atau mengubah image; image multi-GB tetap aman.
 *
 * Deteksi otomatis berdasarkan magic (semua superblock di offset 1024):
 *   - EROFS : 0xE0F5E1E2 (u32)
 *   - F2FS  : 0xF2F52010 (u32)
 *   - EXT4  : 0xEF53     (u16 di superblock + 0x38)
 * Format tidak dikenal -> Success dengan fsType "Unknown" (TIDAK pernah crash).
 */
object FilesystemAnalyzer {

    private const val SUPERBLOCK_OFFSET = 1024L
    private const val SUPERBLOCK_READ_SIZE = 2560 // cukup untuk ext4 + f2fs feature/version
    private const val EROFS_MAGIC = 0xE0F5E1E2L
    private const val F2FS_MAGIC = 0xF2F52010L
    private const val EXT4_MAGIC = 0xEF53

    // ---- EROFS ----
    private const val EROFS_FEATURE_COMPAT = 8
    private const val EROFS_BLKSZBITS = 12
    private const val EROFS_BLOCKS_LO = 36
    private const val EROFS_UUID = 48
    private const val EROFS_VOLUME_NAME = 64
    private const val EROFS_FEATURE_INCOMPAT = 80
    private const val EROFS_COMPR_ALGS = 84
    private const val EROFS_NAME_SIZE = 16

    // ---- EXT4 ----
    private const val EXT4_INODES_COUNT = 0
    private const val EXT4_BLOCKS_COUNT_LO = 4
    private const val EXT4_LOG_BLOCK_SIZE = 24
    private const val EXT4_MAGIC_OFFSET = 56
    private const val EXT4_STATE = 58
    private const val EXT4_REV_LEVEL = 76
    private const val EXT4_FEATURE_COMPAT = 92
    private const val EXT4_FEATURE_INCOMPAT = 96
    private const val EXT4_FEATURE_RO_COMPAT = 100
    private const val EXT4_UUID = 104
    private const val EXT4_VOLUME_NAME = 120
    private const val EXT4_JOURNAL_INUM = 224
    private const val EXT4_BLOCKS_COUNT_HI = 336
    private const val EXT4_NAME_SIZE = 16

    // ---- F2FS ----
    private const val F2FS_MAJOR = 4
    private const val F2FS_MINOR = 6
    private const val F2FS_LOG_BLOCKSIZE = 16
    private const val F2FS_BLOCK_COUNT = 36
    private const val F2FS_SEGMENT_COUNT = 48
    private const val F2FS_CP_BLKADDR = 76
    private const val F2FS_UUID = 108
    private const val F2FS_VOLUME_NAME = 124
    private const val F2FS_EXTENSION_COUNT = 1148
    private const val F2FS_VERSION = 1668
    private const val F2FS_FEATURE = 2180
    private const val F2FS_VERSION_SIZE = 256
    private const val F2FS_CP_VERSION_OFFSET = 8

    /** Cek magic superblock — deteksi parser cepat tanpa load image. */
    fun detectType(file: File): String? {
        if (!file.exists() || file.length() < SUPERBLOCK_OFFSET + 4) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(SUPERBLOCK_OFFSET)
                val sb = ByteArray(64)
                raf.readFully(sb)
                when {
                    u32(sb, 0) == EROFS_MAGIC -> "EROFS"
                    u32(sb, 0) == F2FS_MAGIC -> "F2FS"
                    u16(sb, EXT4_MAGIC_OFFSET) == EXT4_MAGIC -> "EXT4"
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun analyze(file: File): ParserResult<FilesystemAnalysisResult> =
        if (!file.exists()) {
            ParserResult.Failure(ParserStatus.FILE_NOT_FOUND, "File not found: ${file.name}")
        } else {
            try {
                ParserResult.Success(analyzeOrThrow(file))
            } catch (e: ParserException) {
                ParserResult.Failure(e.status, e.message)
            } catch (e: java.io.EOFException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted filesystem image (truncated): ${e.message}")
            } catch (e: java.io.IOException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted filesystem image (read error): ${e.message}")
            } catch (e: Exception) {
                ParserResult.Failure(ParserStatus.READ_ERROR, e.message ?: "Unable to read filesystem image")
            }
        }

    private fun analyzeOrThrow(file: File): FilesystemAnalysisResult {
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

    private fun parseWithRandomAccess(raf: RandomAccessFile, file: File): FilesystemAnalysisResult {
        val fileSize = file.length()
        if (fileSize < SUPERBLOCK_OFFSET + 4L) {
            return unknownResult(file, fileSize)
        }

        val sb = ByteArray(minOf(SUPERBLOCK_READ_SIZE.toLong(), fileSize - SUPERBLOCK_OFFSET).toInt())
        raf.seek(SUPERBLOCK_OFFSET)
        raf.readFully(sb)

        return when {
            u32(sb, 0) == EROFS_MAGIC -> parseErofs(file, fileSize, sb, raf)
            u32(sb, 0) == F2FS_MAGIC -> parseF2fs(file, fileSize, sb, raf)
            u16(sb, EXT4_MAGIC_OFFSET) == EXT4_MAGIC -> parseExt4(file, fileSize, sb)
            else -> unknownResult(file, fileSize)
        }
    }

    // ---------- EROFS ----------

    private fun parseErofs(file: File, fileSize: Long, sb: ByteArray, raf: RandomAccessFile): FilesystemAnalysisResult {
        val blkszbits = sb.getOrNull(EROFS_BLKSZBITS)?.toInt() ?: 0
        val compat = u32(sb, EROFS_FEATURE_COMPAT)
        val incompat = u32(sb, EROFS_FEATURE_INCOMPAT)
        val comprAlgs = u16(sb, EROFS_COMPR_ALGS)
        val features =
            erofsCompatLabels(compat) + erofsIncompatLabels(incompat)
        val compression = compressionLabels(comprAlgs).takeIf { it.isNotEmpty() }?.joinToString(", ")
        return FilesystemAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            fsType = "EROFS",
            known = true,
            version = null, // erofs tidak punya field versi eksplisit (berbasis fitur)
            blockSize = if (blkszbits in 9..16) 1L shl blkszbits else 0L,
            uuid = formatUuid(sb, EROFS_UUID),
            volumeName = cstr(sb, EROFS_VOLUME_NAME, EROFS_NAME_SIZE).ifEmpty { null },
            features = features,
            state = null,
            journalSupport = null,
            blockCount = u32(sb, EROFS_BLOCKS_LO),
            inodeCount = null,
            segmentCount = null,
            checkpointVersion = null,
            compression = compression,
            readOnly = true, // EROFS read-only by design
            kernelVersion = null,
        )
    }

    // ---------- EXT4 ----------

    private fun parseExt4(file: File, fileSize: Long, sb: ByteArray): FilesystemAnalysisResult {
        val logBlockSize = u32(sb, EXT4_LOG_BLOCK_SIZE)
        val state = u16(sb, EXT4_STATE)
        val revLevel = u32(sb, EXT4_REV_LEVEL)
        val compat = u32(sb, EXT4_FEATURE_COMPAT)
        val incompat = u32(sb, EXT4_FEATURE_INCOMPAT)
        val roCompat = u32(sb, EXT4_FEATURE_RO_COMPAT)
        val blocksLo = u32(sb, EXT4_BLOCKS_COUNT_LO)
        val blocksHi = if (incompat and 0x80L != 0L) u32(sb, EXT4_BLOCKS_COUNT_HI) else 0L
        val journalInum = u32(sb, EXT4_JOURNAL_INUM)
        val blockSize = if (logBlockSize < 32L) 1024L shl logBlockSize.toInt() else 0L
        return FilesystemAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            fsType = "EXT4",
            known = true,
            version = when (revLevel) {
                0L -> "0 (good old)"
                1L -> "1 (dynamic)"
                else -> revLevel.toString()
            },
            blockSize = blockSize,
            uuid = formatUuid(sb, EXT4_UUID),
            volumeName = cstr(sb, EXT4_VOLUME_NAME, EXT4_NAME_SIZE).ifEmpty { null },
            features = ext4FeatureLabels(compat, incompat, roCompat),
            state = ext4StateLabel(state),
            journalSupport = journalInum != 0L || (compat and 0x4L) != 0L,
            blockCount = blocksLo or (blocksHi shl 32),
            inodeCount = u32(sb, EXT4_INODES_COUNT),
            segmentCount = null,
            checkpointVersion = null,
            compression = null,
            readOnly = null,
            kernelVersion = null,
        )
    }

    // ---------- F2FS ----------

    private fun parseF2fs(file: File, fileSize: Long, sb: ByteArray, raf: RandomAccessFile): FilesystemAnalysisResult {
        val major = u16(sb, F2FS_MAJOR)
        val minor = u16(sb, F2FS_MINOR)
        val logBlockSize = u32(sb, F2FS_LOG_BLOCKSIZE)
        val blockSize = if (logBlockSize < 32L) 1L shl logBlockSize.toInt() else 0L
        val cpBlkaddr = u32(sb, F2FS_CP_BLKADDR)
        val feature = if (sb.size >= F2FS_FEATURE + 4) u32(sb, F2FS_FEATURE) else 0L
        return FilesystemAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            fsType = "F2FS",
            known = true,
            version = "$major.$minor",
            blockSize = blockSize,
            uuid = formatUuid(sb, F2FS_UUID),
            volumeName = utf16LeCstr(sb, F2FS_VOLUME_NAME).ifEmpty { null },
            features = f2fsFeatureLabels(feature),
            state = null,
            journalSupport = null,
            blockCount = u64(sb, F2FS_BLOCK_COUNT),
            inodeCount = null,
            segmentCount = u32(sb, F2FS_SEGMENT_COUNT),
            checkpointVersion = readCheckpointVersion(raf, blockSize, cpBlkaddr, fileSize),
            compression = null,
            readOnly = null,
            kernelVersion = cstr(sb, F2FS_VERSION, F2FS_VERSION_SIZE).ifEmpty { null },
        )
    }

    private fun readCheckpointVersion(raf: RandomAccessFile, blockSize: Long, cpBlkaddr: Long, fileSize: Long): String? {
        if (blockSize <= 0L || cpBlkaddr <= 0L) return null
        val offset = cpBlkaddr * blockSize + F2FS_CP_VERSION_OFFSET
        if (offset + 8L > fileSize) return null
        return try {
            raf.seek(offset)
            val buf = ByteArray(8)
            raf.readFully(buf)
            val v = u64(buf, 0)
            if (v == 0L) null else v.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun unknownResult(file: File, fileSize: Long): FilesystemAnalysisResult =
        FilesystemAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            fsType = "Unknown",
            known = false,
            version = null,
            blockSize = 0L,
            uuid = null,
            volumeName = null,
            features = emptyList(),
            state = null,
            journalSupport = null,
            blockCount = null,
            inodeCount = null,
            segmentCount = null,
            checkpointVersion = null,
            compression = null,
            readOnly = null,
            kernelVersion = null,
        )

    // ---------- label ----------

    private fun erofsCompatLabels(flags: Long): List<String> {
        val labels = mutableListOf<String>()
        if (flags and 0x1L != 0L) labels.add("sb_checksum")
        if (flags and 0x2L != 0L) labels.add("mtime")
        if (flags and 0x4L != 0L) labels.add("xattr_filter")
        if (flags and 0x8L != 0L) labels.add("shared_ea_in_metabox")
        if (flags and 0x10L != 0L) labels.add("plain_xattr_prefix")
        if (flags and 0x20L != 0L) labels.add("ishare_xattrs")
        return labels
    }

    private fun erofsIncompatLabels(flags: Long): List<String> {
        val labels = mutableListOf<String>()
        if (flags and 0x1L != 0L) labels.add("lz4_0padding")
        if (flags and 0x2L != 0L) labels.add("comp_cfgs/big_pcluster")
        if (flags and 0x4L != 0L) labels.add("chunked_file")
        if (flags and 0x8L != 0L) labels.add("device_table/compr_head2")
        if (flags and 0x10L != 0L) labels.add("ztailpacking")
        if (flags and 0x20L != 0L) labels.add("fragments/dedupe")
        if (flags and 0x40L != 0L) labels.add("xattr_prefixes")
        if (flags and 0x80L != 0L) labels.add("48bit")
        if (flags and 0x100L != 0L) labels.add("metabox")
        return labels
    }

    private fun compressionLabels(bitmap: Int): List<String> {
        val labels = mutableListOf<String>()
        if (bitmap and 0x1 != 0) labels.add("LZ4")
        if (bitmap and 0x2 != 0) labels.add("LZMA")
        if (bitmap and 0x4 != 0) labels.add("DEFLATE")
        if (bitmap and 0x8 != 0) labels.add("ZSTD")
        return labels
    }

    private fun ext4FeatureLabels(compat: Long, incompat: Long, roCompat: Long): List<String> {
        val labels = mutableListOf<String>()
        if (compat and 0x4L != 0L) labels.add("has_journal")
        if (compat and 0x8L != 0L) labels.add("ext_attr")
        if (compat and 0x10L != 0L) labels.add("resize_inode")
        if (compat and 0x20L != 0L) labels.add("dir_index")
        if (incompat and 0x2L != 0L) labels.add("filetype")
        if (incompat and 0x4L != 0L) labels.add("recover")
        if (incompat and 0x40L != 0L) labels.add("extents")
        if (incompat and 0x80L != 0L) labels.add("64bit")
        if (incompat and 0x200L != 0L) labels.add("flex_bg")
        if (roCompat and 0x1L != 0L) labels.add("sparse_super")
        if (roCompat and 0x2L != 0L) labels.add("large_file")
        if (roCompat and 0x8L != 0L) labels.add("huge_file")
        if (roCompat and 0x10L != 0L) labels.add("gdt_csum")
        if (roCompat and 0x20L != 0L) labels.add("dir_nlink")
        return labels
    }

    private fun f2fsFeatureLabels(feature: Long): List<String> {
        val labels = mutableListOf<String>()
        if (feature and 0x1L != 0L) labels.add("encrypt")
        if (feature and 0x2L != 0L) labels.add("blkzoned")
        if (feature and 0x4L != 0L) labels.add("atomic_write")
        if (feature and 0x8L != 0L) labels.add("extra_attr")
        if (feature and 0x10L != 0L) labels.add("sb_checksum")
        if (feature and 0x20L != 0L) labels.add("casefold")
        if (feature and 0x40L != 0L) labels.add("compression")
        if (feature and 0x80L != 0L) labels.add("ro")
        return labels
    }

    private fun ext4StateLabel(state: Int): String =
        when {
            state and 0x2 != 0 -> "Errors detected"
            state and 0x4 != 0 -> "Orphans being recovered"
            state and 0x1 != 0 -> "Clean"
            else -> "Unknown"
        }

    // ---------- helpers ----------

    private fun formatUuid(buf: ByteArray, offset: Int): String? {
        if (offset + 16 > buf.size) return null
        val parts =
            listOf(
                hex(buf, offset, 4),
                hex(buf, offset + 4, 2),
                hex(buf, offset + 6, 2),
                hex(buf, offset + 8, 2),
                hex(buf, offset + 10, 6),
            )
        return parts.joinToString("-")
    }

    private fun hex(buf: ByteArray, offset: Int, len: Int): String =
        buildString {
            for (i in offset until offset + len) {
                append("%02x".format(buf[i].toInt() and 0xFF))
            }
        }

    private fun u16(buf: ByteArray, offset: Int): Int =
        (buf.getOrElse(offset) { 0 }.toInt() and 0xFF) or
            ((buf.getOrElse(offset + 1) { 0 }.toInt() and 0xFF) shl 8)

    private fun u32(buf: ByteArray, offset: Int): Long =
        (buf.getOrElse(offset) { 0 }.toLong() and 0xFF) or
            ((buf.getOrElse(offset + 1) { 0 }.toLong() and 0xFF) shl 8) or
            ((buf.getOrElse(offset + 2) { 0 }.toLong() and 0xFF) shl 16) or
            ((buf.getOrElse(offset + 3) { 0 }.toLong() and 0xFF) shl 24)

    private fun u64(buf: ByteArray, offset: Int): Long =
        u32(buf, offset) or (u32(buf, offset + 4) shl 32)

    private fun cstr(buf: ByteArray, offset: Int, max: Int): String {
        var end = offset
        val limit = minOf(offset + max, buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun utf16LeCstr(buf: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        var i = offset
        while (i + 1 < buf.size) {
            val code = (buf[i].toInt() and 0xFF) or ((buf[i + 1].toInt() and 0xFF) shl 8)
            if (code == 0) break
            sb.append(code.toChar())
            i += 2
        }
        return sb.toString().trim()
    }
}
