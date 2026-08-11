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

import com.afft.app.util.SparseImage
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Satu partisi logis di dalam super.img (metadata hanya, isi tidak dibaca).
 */
data class SuperPartition(
    val name: String,
    val size: Long,
    val group: String,
    val slot: String?,
    val attributes: Int,
    val extentCount: Int,
)

/**
 * Hasil analisis super.img — murni, tanpa dependensi UI.
 */
data class SuperAnalysisResult(
    val filename: String,
    val fileSize: Long,
    val majorVersion: Int,
    val minorVersion: Int,
    val headerSize: Long,
    val tablesSize: Long,
    val geometryStructSize: Long,
    val blockSize: Long,
    val metadataMaxSize: Long,
    val metadataSlotCount: Int,
    val virtualAb: Boolean,
    val headerChecksumValid: Boolean,
    val tablesChecksumValid: Boolean,
    val partitionTableIntact: Boolean,
    val groupCount: Int,
    val blockDeviceCount: Int,
    val partitions: List<SuperPartition>,
    val groups: List<String>,
    val blockDevices: List<String>,
)

/**
 * Pembaca metadata super.img (format logical partition manager AOSP).
 *
 * Hanya membaca metadata — TIDAK mengekstrak/men-mount partisi dan TIDAK
 * pernah memuat seluruh image (cukup 4096 byte geometry + area metadata
 * kecil; image >10 GB tetap didukung).
 *
 * Layout (liblp/metadata_format.h):
 *   block 0      : LpMetadataGeometry (magic 0x616c4467, "gDla")
 *   block 1      : salinan geometry
 *   metadata 0   : LpMetadataHeader ("0PLA") + tabel (partisi, extent,
 *                  group, block device) di offset logical_block_size*2
 *   metadata backup : setelah semua slot primary
 *
 * Semua field little-endian.
 */
object SuperAnalyzer {

    private const val GEOMETRY_MAGIC = 0x616c4467L // bytes: 67 44 6c 61 = "gDla"
    private const val HEADER_MAGIC = 0x414C5030L // bytes: 30 50 4c 41 = "0PLA"
    private const val SPARSE_MAGIC = 0xED26FF3AL // android sparse image
    private const val SUPPORTED_MAJOR_VERSION = 10
    private const val MAX_MINOR_VERSION = 2
    private const val SECTOR_SIZE = 512L
    private const val GEOMETRY_SIZE = 4096L
    private const val SPARSE_META_PREFIX_BYTES = 8L * 1024L * 1024L
    private const val HEADER_FIXED_SIZE = 256

    // ---- offset LpMetadataGeometry (52 byte packed) ----
    private const val GEO_STRUCT_SIZE = 4
    private const val GEO_METADATA_MAX_SIZE = 40
    private const val GEO_SLOT_COUNT = 44
    private const val GEO_BLOCK_SIZE = 48

    // ---- offset LpMetadataHeader (256 byte packed) ----
    private const val HDR_MAJOR = 4
    private const val HDR_MINOR = 6
    private const val HDR_HEADER_SIZE = 8
    private const val HDR_CHECKSUM = 12
    private const val HDR_CHECKSUM_SIZE = 32
    private const val HDR_TABLES_SIZE = 44
    private const val HDR_TABLES_CHECKSUM = 48
    private const val HDR_PARTITIONS = 80
    private const val HDR_EXTENTS = 92
    private const val HDR_GROUPS = 104
    private const val HDR_BLOCK_DEVICES = 116
    private const val HDR_FLAGS = 128
    private const val HDR_FLAG_VIRTUAL_AB = 0x1L

    // ---- offset LpMetadataPartition (52 byte) ----
    private const val PART_NAME = 0
    private const val PART_NAME_SIZE = 36
    private const val PART_ATTRIBUTES = 36
    private const val PART_FIRST_EXTENT = 40
    private const val PART_NUM_EXTENTS = 44
    private const val PART_GROUP_INDEX = 48

    // ---- offset LpMetadataExtent (24 byte) ----
    private const val EXT_NUM_SECTORS = 0

    // ---- offset LpMetadataBlockDevice (64 byte) ----
    private const val DEV_NAME = 24
    private const val DEV_NAME_SIZE = 36

    // ---- offset LpMetadataPartitionGroup (48 byte) ----
    private const val GROUP_NAME = 0
    private const val GROUP_NAME_SIZE = 36

    /** Cek magic geometry ("gDla") — deteksi parser cepat, tanpa load file. */
    fun isSuperImage(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                u32At(raf, 0) == GEOMETRY_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    fun analyze(file: File): ParserResult<SuperAnalysisResult> =
        if (!file.exists()) {
            ParserResult.Failure(ParserStatus.FILE_NOT_FOUND, "File not found: ${file.name}")
        } else {
            try {
                ParserResult.Success(analyzeOrThrow(file))
            } catch (e: ParserException) {
                ParserResult.Failure(e.status, e.message)
            } catch (e: java.io.EOFException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted super image (truncated metadata): ${e.message}")
            } catch (e: java.io.IOException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted super image (read error): ${e.message}")
            } catch (e: Exception) {
                ParserResult.Failure(ParserStatus.READ_ERROR, e.message ?: "Unable to read super image")
            }
        }

    private fun analyzeOrThrow(file: File): SuperAnalysisResult {
        if (SparseImage.isSparseImage(file)) {
            return analyzeSparse(file)
        }
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

    /**
     * Super.img sparse (umum di firmware Xiaomi/HyperOS): un-sparse cukup
     * prefix metadata (bukan seluruh image) ke raw sementara, lalu parse.
     * File temp dihapus setelah selesai; file asli tidak pernah diubah.
     */
    private fun analyzeSparse(file: File): SuperAnalysisResult {
        val parent = file.parentFile ?: file.absoluteFile.parentFile
        val temp = File(parent, "super_meta_prefix.img")
        val converted =
            try {
                SparseImage.sparseToRawPrefix(file, temp, SPARSE_META_PREFIX_BYTES)
            } catch (e: Exception) {
                false
            }
        if (!converted) {
            throw ParserException(
                ParserStatus.CORRUPTED_METADATA,
                "Sparse super image: metadata cannot be read (invalid sparse structure)",
            )
        }
        try {
            return RandomAccessFile(temp, "r").use { parseWithRandomAccess(it, temp) }
                .copy(filename = file.name)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun parseWithRandomAccess(raf: RandomAccessFile, file: File): SuperAnalysisResult {
        val fileSize = file.length()
        if (fileSize < GEOMETRY_SIZE) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "File too small to be a valid super image")
        }

        val geometry = ByteArray(64)
        raf.seek(0)
        raf.readFully(geometry)
        when {
            u32(geometry, 0) != GEOMETRY_MAGIC && u32(geometry, 0) == SPARSE_MAGIC ->
                throw ParserException(
                    ParserStatus.INVALID_PAYLOAD,
                    "Sparse super image detected — convert to raw (simg2img) before analysis",
                )
            u32(geometry, 0) != GEOMETRY_MAGIC ->
                throw ParserException(ParserStatus.INVALID_HEADER, "Invalid super image (geometry magic mismatch)")
        }

        val geometryStructSize = u32(geometry, GEO_STRUCT_SIZE)
        val metadataMaxSize = u32(geometry, GEO_METADATA_MAX_SIZE)
        val slotCount = u32(geometry, GEO_SLOT_COUNT).toInt()
        val blockSize = u32(geometry, GEO_BLOCK_SIZE)
        if (metadataMaxSize == 0L || blockSize == 0L) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Invalid geometry (zero block size or metadata size)")
        }

        // Cari metadata slot 0 (header "0PLA") di offset yang masuk akal.
        val candidates =
            listOf(blockSize * 2, GEOMETRY_SIZE * 2, GEOMETRY_SIZE, 8192L)
                .distinct()
                .filter { it + HEADER_FIXED_SIZE <= fileSize }
        val metaStart = candidates.firstOrNull { u32At(raf, it) == HEADER_MAGIC }
            ?: throw ParserException(
                ParserStatus.CORRUPTED_METADATA,
                "Super metadata header not found (slot 0)",
            )

        val header = ByteArray(HEADER_FIXED_SIZE)
        raf.seek(metaStart)
        raf.readFully(header)

        val major = u16(header, HDR_MAJOR)
        val minor = u16(header, HDR_MINOR)
        if (major != SUPPORTED_MAJOR_VERSION) {
            throw ParserException(
                ParserStatus.UNSUPPORTED_VERSION,
                "Unsupported metadata version $major.$minor (supported major: $SUPPORTED_MAJOR_VERSION)",
            )
        }
        if (minor > MAX_MINOR_VERSION) {
            throw ParserException(
                ParserStatus.UNSUPPORTED_VERSION,
                "Unsupported metadata minor version $minor (max: $MAX_MINOR_VERSION)",
            )
        }

        val headerSize = u32(header, HDR_HEADER_SIZE).coerceIn(116L, HEADER_FIXED_SIZE.toLong())
        val tablesSize = u32(header, HDR_TABLES_SIZE)
        if (tablesSize > metadataMaxSize) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Tables size exceeds metadata max size")
        }
        val virtualAb = u32(header, HDR_FLAGS) and HDR_FLAG_VIRTUAL_AB != 0L

        val headerChecksumValid = verifyHeaderChecksum(header, headerSize)
        val expectedTablesChecksum = header.copyOfRange(HDR_TABLES_CHECKSUM, HDR_TABLES_CHECKSUM + HDR_CHECKSUM_SIZE)
        val tablesChecksumValid =
            verifyTablesChecksum(raf, metaStart + headerSize, tablesSize, fileSize, expectedTablesChecksum)

        // Tabel (offset relatif terhadap akhir header).
        val tableBase = metaStart + headerSize
        val partitionsDesc = desc(header, HDR_PARTITIONS)
        val extentsDesc = desc(header, HDR_EXTENTS)
        val groupsDesc = desc(header, HDR_GROUPS)
        val devicesDesc = desc(header, HDR_BLOCK_DEVICES)

        val partitionsRaw = readTable(raf, tableBase, partitionsDesc, fileSize) ?: emptyList()
        val extentsRaw = readTable(raf, tableBase, extentsDesc, fileSize) ?: emptyList()
        val groupsRaw = readTable(raf, tableBase, groupsDesc, fileSize) ?: emptyList()
        val devicesRaw = readTable(raf, tableBase, devicesDesc, fileSize) ?: emptyList()

        val groups = groupsRaw.map { cstr(it, GROUP_NAME, GROUP_NAME_SIZE) }
        val blockDevices = devicesRaw.map { cstr(it, DEV_NAME, DEV_NAME_SIZE) }

        var intact = true
        val partitions =
            partitionsRaw.map { entry ->
                val firstExtent = u32(entry, PART_FIRST_EXTENT).toInt()
                val numExtents = u32(entry, PART_NUM_EXTENTS).toInt()
                val groupIndex = u32(entry, PART_GROUP_INDEX).toInt()
                var size = 0L
                if (numExtents > 0) {
                    for (i in firstExtent until firstExtent + numExtents) {
                        val extent = extentsRaw.getOrNull(i)
                        if (extent == null) {
                            intact = false
                            break
                        }
                        size += u64(extent, EXT_NUM_SECTORS) * SECTOR_SIZE
                    }
                }
                if (groupIndex !in groups.indices) intact = false
                SuperPartition(
                    name = cstr(entry, PART_NAME, PART_NAME_SIZE),
                    size = size,
                    group = groups.getOrNull(groupIndex) ?: "?",
                    slot = slotFromName(cstr(entry, PART_NAME, PART_NAME_SIZE)),
                    attributes = u32(entry, PART_ATTRIBUTES).toInt(),
                    extentCount = numExtents,
                )
            }

        return SuperAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            majorVersion = major,
            minorVersion = minor,
            headerSize = headerSize,
            tablesSize = tablesSize,
            geometryStructSize = geometryStructSize,
            blockSize = blockSize,
            metadataMaxSize = metadataMaxSize,
            metadataSlotCount = slotCount,
            virtualAb = virtualAb,
            headerChecksumValid = headerChecksumValid,
            tablesChecksumValid = tablesChecksumValid,
            partitionTableIntact = intact,
            groupCount = groups.size,
            blockDeviceCount = blockDevices.size,
            partitions = partitions,
            groups = groups,
            blockDevices = blockDevices,
        )
    }

    // ---------- checksum ----------

    private fun verifyHeaderChecksum(header: ByteArray, headerSize: Long): Boolean {
        val size = headerSize.toInt().coerceAtMost(header.size)
        val copy = header.copyOf(size)
        java.util.Arrays.fill(copy, HDR_CHECKSUM, HDR_CHECKSUM + HDR_CHECKSUM_SIZE, 0)
        val actual = sha256(copy)
        return actual.contentEquals(header.copyOfRange(HDR_CHECKSUM, HDR_CHECKSUM + HDR_CHECKSUM_SIZE))
    }

    private fun verifyTablesChecksum(
        raf: RandomAccessFile,
        offset: Long,
        tablesSize: Long,
        fileSize: Long,
        expected: ByteArray,
    ): Boolean {
        if (tablesSize <= 0L || offset + tablesSize > fileSize) return false
        return try {
            val tables = ByteArray(tablesSize.toInt())
            raf.seek(offset)
            raf.readFully(tables)
            sha256(tables).contentEquals(expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    // ---------- tabel ----------

    private fun desc(header: ByteArray, offset: Int): Triple<Long, Long, Long> =
        Triple(u32(header, offset), u32(header, offset + 4), u32(header, offset + 8))

    private fun readTable(
        raf: RandomAccessFile,
        tableBase: Long,
        descriptor: Triple<Long, Long, Long>,
        fileSize: Long,
    ): List<ByteArray>? {
        val (offset, count, entrySize) = descriptor
        if (count <= 0L || entrySize <= 0L || count > 10_000L || entrySize > 4096L) {
            return if (count == 0L) emptyList() else null
        }
        val start = tableBase + offset
        val bytes = count * entrySize
        if (start < tableBase || start + bytes > fileSize) return null
        return try {
            raf.seek(start)
            List(count.toInt()) { ByteArray(entrySize.toInt()).also { raf.readFully(it) } }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- helper baca ----------

    private fun u32At(raf: RandomAccessFile, offset: Long): Long {
        val buf = ByteArray(4)
        raf.seek(offset)
        raf.readFully(buf)
        return u32(buf, 0)
    }

    private fun u16(buf: ByteArray, offset: Int): Int =
        (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(buf: ByteArray, offset: Int): Long =
        (buf[offset].toLong() and 0xFF) or
            ((buf[offset + 1].toLong() and 0xFF) shl 8) or
            ((buf[offset + 2].toLong() and 0xFF) shl 16) or
            ((buf[offset + 3].toLong() and 0xFF) shl 24)

    private fun u64(buf: ByteArray, offset: Int): Long =
        u32(buf, offset) or (u32(buf, offset + 4) shl 32)

    private fun cstr(buf: ByteArray, offset: Int, max: Int): String {
        var end = offset
        val limit = minOf(offset + max, buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun slotFromName(name: String): String? {
        val suffix = name.takeLast(2)
        return if (suffix == "_a" || suffix == "_b") suffix.removePrefix("_") else null
    }
}
