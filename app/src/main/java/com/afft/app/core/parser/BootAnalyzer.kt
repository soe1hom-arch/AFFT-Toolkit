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
 * Hasil analisis boot image — murni, tanpa dependensi UI.
 * Hanya metadata yang dibaca dari header (tidak membongkar isi image).
 */
data class BootAnalysisResult(
    val filename: String,
    val fileSize: Long,
    val headerVersion: Int,
    val headerSize: Long,
    val pageSize: Long?,
    val kernelSize: Long,
    val kernelAddress: Long?,
    val ramdiskSize: Long,
    val ramdiskAddress: Long?,
    val ramdiskPresent: Boolean,
    val ramdiskCompression: String?,
    val secondSize: Long,
    val dtbSize: Long,
    val dtboSize: Long,
    val osVersion: String?,
    val securityPatchLevel: String?,
    val architecture: String?,
    val productName: String?,
    val cmdline: String,
    val deviceTreePresent: Boolean,
    val avbFooterPresent: Boolean,
    val vendorBoot: Boolean = false,
)

/**
 * Pembaca header boot image (Android Boot Image v0-v4).
 *
 * TIDAK membongkar image dan TIDAK membaca seluruh file:
 *   - 2048 byte pertama untuk header (v0-v2 = 1632 byte, v3/v4 jauh lebih kecil)
 *   - 20 byte di offset kernel  -> deteksi arsitektur (ELF e_machine)
 *   - 8 byte di offset ramdisk  -> deteksi kompresi (gzip/lz4/xz/zstd/cpio)
 *   - 64 byte terakhir file     -> deteksi footer AVB ("AVBf")
 *
 * Format header (AOSP bootimg.h):
 *   v0-v2: magic(8) kernel_size(4) kernel_addr(4) ramdisk_size(4)
 *          ramdisk_addr(4) second_size(4) second_addr(4) tags_addr(4)
 *          page_size(4) header_version(4) os_version(4)
 *          name(16) cmdline(512) id(32) extra_cmdline(1024)
 *          + v1: recovery_dtbo_size/offset, header_size
 *          + v2: dtb_size, dtb_addr
 *   v3-v4: magic(8) kernel_size(4) ramdisk_size(4) os_version(4)
 *          header_size(4) reserved(16) header_version(4) cmdline(512)
 */
object BootAnalyzer {

    private const val BOOT_MAGIC = "ANDROID!"
    private const val VENDOR_BOOT_MAGIC = "VNDRBOOT"
    private const val BOOT_MAGIC_SIZE = 8
    private const val HEADER_VERSION_OFFSET = 40
    private const val MAX_SUPPORTED_VERSION = 4
    private const val HEADER_READ_BUDGET = 2048L
    private const val VENDOR_BOOT_HEADER_SIZE = 2112L

    // ---- offset header v0-v2 ----
    private const val V_KERNEL_SIZE = 8
    private const val V_KERNEL_ADDR = 12
    private const val V_RAMDISK_SIZE = 16
    private const val V_RAMDISK_ADDR = 20
    private const val V_SECOND_SIZE = 24
    private const val V_PAGE_SIZE = 36
    private const val V_OS_VERSION = 44
    private const val V_NAME = 48
    private const val V_NAME_SIZE = 16
    private const val V_CMDLINE = 64
    private const val V_CMDLINE_SIZE = 512
    private const val V_EXTRA_CMDLINE = 608
    private const val V_EXTRA_CMDLINE_SIZE = 1024
    private const val V1_DTBO_SIZE = 1632
    private const val V2_DTB_SIZE = 1644

    // ---- offset header v3/v4 ----
    private const val V3_KERNEL_SIZE = 8
    private const val V3_RAMDISK_SIZE = 12
    private const val V3_OS_VERSION = 16
    private const val V3_HEADER_SIZE = 20
    private const val V3_CMDLINE = 44
    private const val V3_CMDLINE_SIZE = 512

    // ---- offset header vendor_boot v3/v4 (AOSP bootimg.h) ----
    private const val VB_PAGE_SIZE = 12
    private const val VB_KERNEL_ADDR = 16
    private const val VB_RAMDISK_ADDR = 20
    private const val VB_VENDOR_RAMDISK_SIZE = 24
    private const val VB_CMDLINE = 28
    private const val VB_CMDLINE_SIZE = 2048
    private const val VB_NAME = 2080
    private const val VB_NAME_SIZE = 16
    private const val VB_HEADER_SIZE = 2096
    private const val VB_DTB_SIZE = 2100

    /** Cek cepat magic (tanpa membuka seluruh file) untuk deteksi parser. */
    fun isBootImage(file: File): Boolean {
        if (!file.exists() || file.length() < BOOT_MAGIC_SIZE) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(BOOT_MAGIC_SIZE)
                raf.readFully(magic)
                String(magic, Charsets.US_ASCII) == BOOT_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Cek cepat magic vendor_boot ("VNDRBOOT") tanpa membuka seluruh file. */
    fun isVendorBootImage(file: File): Boolean {
        if (!file.exists() || file.length() < BOOT_MAGIC_SIZE) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(BOOT_MAGIC_SIZE)
                raf.readFully(magic)
                String(magic, Charsets.US_ASCII) == VENDOR_BOOT_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    fun analyze(file: File): ParserResult<BootAnalysisResult> =
        if (!file.exists()) {
            ParserResult.Failure(ParserStatus.FILE_NOT_FOUND, "File not found: ${file.name}")
        } else {
            try {
                ParserResult.Success(analyzeOrThrow(file))
            } catch (e: ParserException) {
                ParserResult.Failure(e.status, e.message)
            } catch (e: java.io.EOFException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted boot image (truncated header): ${e.message}")
            } catch (e: java.io.IOException) {
                ParserResult.Failure(ParserStatus.CORRUPTED_METADATA, "Corrupted boot image (read error): ${e.message}")
            } catch (e: Exception) {
                ParserResult.Failure(ParserStatus.READ_ERROR, e.message ?: "Unable to read boot image")
            }
        }

    private fun analyzeOrThrow(file: File): BootAnalysisResult {
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

    private fun parseWithRandomAccess(raf: RandomAccessFile, file: File): BootAnalysisResult {
        val fileSize = file.length()
        if (fileSize < BOOT_MAGIC_SIZE + 8L) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "File too small to be a valid boot image")
        }

        raf.seek(0)
        val magic = ByteArray(BOOT_MAGIC_SIZE)
        raf.readFully(magic)
        return when (String(magic, Charsets.US_ASCII)) {
            VENDOR_BOOT_MAGIC -> parseVendorBootHeader(raf, file, fileSize)
            BOOT_MAGIC -> parseBootHeader(raf, file, fileSize)
            else -> throw ParserException(
                ParserStatus.INVALID_HEADER,
                "Invalid boot header (magic is not '$BOOT_MAGIC')",
            )
        }
    }

    private fun parseBootHeader(raf: RandomAccessFile, file: File, fileSize: Long): BootAnalysisResult {
        val headerVersion = u32At(raf, HEADER_VERSION_OFFSET).toInt()
        if (headerVersion > MAX_SUPPORTED_VERSION) {
            throw ParserException(
                ParserStatus.UNSUPPORTED_VERSION,
                "Unsupported boot header version $headerVersion (max $MAX_SUPPORTED_VERSION)",
            )
        }

        // Baca header dalam satu potongan kecil (maks 2048 byte) — tidak load image.
        val header = ByteArray(minOf(HEADER_READ_BUDGET, fileSize).toInt())
        raf.seek(0)
        raf.readFully(header)

        val isV3Plus = headerVersion >= 3
        val kernelSize = if (isV3Plus) u32(header, V3_KERNEL_SIZE).toLong() else u32(header, V_KERNEL_SIZE).toLong()
        val ramdiskSize = if (isV3Plus) u32(header, V3_RAMDISK_SIZE).toLong() else u32(header, V_RAMDISK_SIZE).toLong()
        val osVersionRaw = if (isV3Plus) u32(header, V3_OS_VERSION) else u32(header, V_OS_VERSION)
        val cmdline =
            readCString(header, if (isV3Plus) V3_CMDLINE else V_CMDLINE, V3_CMDLINE_SIZE)
        val pageSize = if (isV3Plus) null else u32(header, V_PAGE_SIZE).toLong()
        val kernelAddress = if (isV3Plus) null else u32(header, V_KERNEL_ADDR).toLong()
        val ramdiskAddress = if (isV3Plus) null else u32(header, V_RAMDISK_ADDR).toLong()
        val secondSize = if (isV3Plus) 0L else u32(header, V_SECOND_SIZE).toLong()
        val dtboSize =
            if (headerVersion == 1 || headerVersion == 2) u32(header, V1_DTBO_SIZE).toLong() else 0L
        val dtbSize = if (headerVersion == 2) u32(header, V2_DTB_SIZE).toLong() else 0L
        val headerSize =
            if (isV3Plus) u32(header, V3_HEADER_SIZE).toLong() else (pageSize ?: HEADER_READ_BUDGET)
        val productName =
            if (isV3Plus) null else readCString(header, V_NAME, V_NAME_SIZE).ifEmpty { null }
        val extraCmdline =
            if (isV3Plus) "" else readCString(header, V_EXTRA_CMDLINE, V_EXTRA_CMDLINE_SIZE)

        if (kernelSize == 0L && ramdiskSize == 0L) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Boot image has neither kernel nor ramdisk")
        }

        // Offset section (best-effort; hanya untuk sniff header kernel/ramdisk).
        val kernelOffset = if (isV3Plus) headerSize else (pageSize ?: headerSize)
        val ramdiskOffset = kernelOffset + kernelSize
        val ramdiskAligned =
            if (!isV3Plus && pageSize != null && pageSize > 0) alignUp(ramdiskOffset, pageSize) else ramdiskOffset
        if (ramdiskSize > 0L && ramdiskAligned + ramdiskSize > fileSize) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Ramdisk section is out of bounds")
        }

        return BootAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            headerVersion = headerVersion,
            headerSize = headerSize,
            pageSize = pageSize,
            kernelSize = kernelSize,
            kernelAddress = kernelAddress,
            ramdiskSize = ramdiskSize,
            ramdiskAddress = ramdiskAddress,
            ramdiskPresent = ramdiskSize > 0L,
            ramdiskCompression = if (ramdiskSize > 0L) peekCompression(raf, ramdiskAligned, fileSize) else null,
            secondSize = secondSize,
            dtbSize = dtbSize,
            dtboSize = dtboSize,
            osVersion = decodeOsVersion(osVersionRaw),
            securityPatchLevel = decodePatchLevel(osVersionRaw),
            architecture = peekElfArchitecture(raf, kernelOffset, fileSize),
            productName = productName,
            cmdline = listOf(cmdline, extraCmdline).filter { it.isNotBlank() }.joinToString(" ").trim(),
            deviceTreePresent = dtbSize > 0L || dtboSize > 0L || secondSize > 0L,
            avbFooterPresent = peekAvbFooter(raf, fileSize),
        )
    }

    private fun parseVendorBootHeader(raf: RandomAccessFile, file: File, fileSize: Long): BootAnalysisResult {
        if (fileSize < 32L) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Vendor boot image is too small")
        }

        // Baca header vendor_boot (2112 byte pada v3/v4) — tidak load image.
        val headerBudget = minOf(VENDOR_BOOT_HEADER_SIZE, fileSize)
        val header = ByteArray(headerBudget.toInt())
        raf.seek(0)
        raf.readFully(header)

        val pageSize = u32(header, VB_PAGE_SIZE)
        val vendorRamdiskSize = u32(header, VB_VENDOR_RAMDISK_SIZE)
        val dtbSize = u32(header, VB_DTB_SIZE)
        val headerSize = u32(header, VB_HEADER_SIZE)
        val kernelAddress = u32(header, VB_KERNEL_ADDR)
        val ramdiskAddress = u32(header, VB_RAMDISK_ADDR)
        val productName = readCString(header, VB_NAME, VB_NAME_SIZE).ifEmpty { null }
        val cmdline = readCString(header, VB_CMDLINE, VB_CMDLINE_SIZE)

        // Layout vendor_boot: page pertama berisi header, ramdisk dimulai page_size.
        val ramdiskOffset = pageSize
        if (vendorRamdiskSize > 0L && ramdiskOffset + vendorRamdiskSize > fileSize) {
            throw ParserException(ParserStatus.CORRUPTED_METADATA, "Vendor ramdisk section is out of bounds")
        }

        return BootAnalysisResult(
            filename = file.name,
            fileSize = fileSize,
            headerVersion = 3,
            headerSize = if (headerSize > 0L) headerSize else pageSize,
            pageSize = pageSize,
            kernelSize = 0L,
            kernelAddress = kernelAddress,
            ramdiskSize = vendorRamdiskSize,
            ramdiskAddress = ramdiskAddress,
            ramdiskPresent = vendorRamdiskSize > 0L,
            ramdiskCompression = if (vendorRamdiskSize > 0L) peekCompression(raf, ramdiskOffset, fileSize) else null,
            secondSize = 0L,
            dtbSize = dtbSize,
            dtboSize = 0L,
            osVersion = null,
            securityPatchLevel = null,
            architecture = null,
            productName = productName,
            cmdline = cmdline,
            deviceTreePresent = dtbSize > 0L,
            avbFooterPresent = peekAvbFooter(raf, fileSize),
            vendorBoot = true,
        )
    }

    // ---------- decode os_version ----------

    /**
     * os_version (uint32) mengkodekan:
     *   bits 0-5  : minor
     *   bits 6-10 : major
     *   bits 16-23: patch year - 2000
     *   bits 24-27: patch month
     */
    private fun decodeOsVersion(raw: Long): String? {
        val major = ((raw ushr 6) and 0x1F).toInt()
        val minor = (raw and 0x3F).toInt()
        return if (major == 0 && minor == 0) null else "$major.$minor"
    }

    private fun decodePatchLevel(raw: Long): String? {
        val year = 2000 + ((raw ushr 16) and 0xFF).toInt()
        val month = ((raw ushr 24) and 0x0F).toInt()
        return if (year >= 2000 && month in 1..12) {
            "%04d-%02d".format(year, month)
        } else {
            null
        }
    }

    // ---------- sniff header kernel / ramdisk / avb ----------

    private fun peekElfArchitecture(raf: RandomAccessFile, offset: Long, fileSize: Long): String? {
        if (offset < 0L || offset + 20L > fileSize) return null
        return try {
            val buf = ByteArray(20)
            raf.seek(offset)
            raf.readFully(buf)
            if (buf[0] != 0x7F.toByte() || buf[1] != 'E'.code.toByte() ||
                buf[2] != 'L'.code.toByte() || buf[3] != 'F'.code.toByte()
            ) {
                return null
            }
            val machine = ((buf[19].toInt() and 0xFF) shl 8) or (buf[18].toInt() and 0xFF)
            when (machine) {
                3 -> "x86"
                8 -> "MIPS"
                20 -> "PowerPC"
                21 -> "PowerPC64"
                40 -> "ARM"
                62 -> "x86_64"
                183 -> "AArch64 (arm64)"
                243 -> "RISC-V"
                else -> "ELF (machine $machine)"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun peekCompression(raf: RandomAccessFile, offset: Long, fileSize: Long): String? {
        if (offset < 0L || offset + 8L > fileSize) return null
        return try {
            val buf = ByteArray(8)
            raf.seek(offset)
            raf.readFully(buf)
            when {
                buf[0] == 0x1F.toByte() && buf[1] == 0x8B.toByte() -> "gzip"
                buf[0] == 0x28.toByte() && buf[1] == 0xB5.toByte() && buf[2] == 0x2F.toByte() && buf[3] == 0xFD.toByte() -> "zstd"
                buf[0] == 0x04.toByte() && buf[1] == 0x22.toByte() && buf[2] == 0x4D.toByte() && buf[3] == 0x18.toByte() -> "lz4"
                buf[0] == 0x02.toByte() && buf[1] == 0x21.toByte() && buf[2] == 0x4C.toByte() && buf[3] == 0x18.toByte() -> "lz4 legacy"
                buf[0] == 0xFD.toByte() && buf[1] == 0x37.toByte() && buf[2] == 0x7A.toByte() && buf[3] == 0x58.toByte() -> "xz"
                buf[0] == 0x89.toByte() && buf[1] == 0x4C.toByte() && buf[2] == 0x5A.toByte() && buf[3] == 0x4F.toByte() -> "lzop"
                String(buf, 0, 6, Charsets.US_ASCII) == "070701" -> "cpio (uncompressed)"
                String(buf, 0, 6, Charsets.US_ASCII) == "070707" -> "cpio (uncompressed)"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun peekAvbFooter(raf: RandomAccessFile, fileSize: Long): Boolean {
        if (fileSize < 64L) return false
        return try {
            val buf = ByteArray(64)
            raf.seek(fileSize - 64L)
            raf.readFully(buf)
            String(buf, Charsets.US_ASCII).contains("AVBf")
        } catch (_: Exception) {
            false
        }
    }

    // ---------- helpers ----------

    private fun u32At(raf: RandomAccessFile, offset: Int): Long {
        val buf = ByteArray(4)
        raf.seek(offset.toLong())
        raf.readFully(buf)
        return u32(buf, 0)
    }

    private fun u32(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF)) or
            ((buf[offset + 1].toLong() and 0xFF) shl 8) or
            ((buf[offset + 2].toLong() and 0xFF) shl 16) or
            ((buf[offset + 3].toLong() and 0xFF) shl 24)

    private fun readCString(buf: ByteArray, offset: Int, max: Int): String {
        var end = offset
        val limit = minOf(offset + max, buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun alignUp(value: Long, alignment: Long): Long =
        if (alignment <= 0L) value else ((value + alignment - 1L) / alignment) * alignment
}
