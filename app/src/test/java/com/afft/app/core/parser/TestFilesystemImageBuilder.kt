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
import java.io.RandomAccessFile

/**
 * Helper test untuk membangun superblock filesystem (EROFS / EXT4 / F2FS).
 *
 * Semua superblock diletakkan di offset 1024 (sesuai F2FS_SUPER_OFFSET dan
 * EROFS_SUPER_OFFSET pada kernel Linux; EXT4 juga di offset 1024).
 * Field diisi relatif terhadap awal superblock.
 */
object TestFilesystemImageBuilder {

    const val SUPERBLOCK_OFFSET = 1024L
    private const val SB_BYTES = 2560

    // ---------- EROFS ----------

    fun buildErofs(
        blkszbits: Int = 12, // 4096
        blocksLo: Long = 262_144L,
        uuid: ByteArray = defaultUuid(),
        volumeName: String = "erofs_test",
        incompat: Long = 0x10L, // ztailpacking
        comprAlgs: Int = 0x1, // LZ4
        magic: Long = 0xE0F5E1E2L,
    ): ByteArray {
        val sb = ByteArray(SB_BYTES)
        putU32(sb, 0, magic.toInt())
        putU32(sb, 8, 0) // feature_compat
        sb[12] = blkszbits.toByte()
        putU32(sb, 36, blocksLo.toInt())
        uuid.copyInto(sb, 48)
        putString(sb, 64, volumeName)
        putU32(sb, 80, incompat.toInt())
        putU16(sb, 84, comprAlgs)
        return image(sb)
    }

    // ---------- EXT4 ----------

    fun buildExt4(
        inodesCount: Long = 262_144L,
        blocksCountLo: Long = 262_144L,
        logBlockSize: Long = 2, // 4096
        state: Int = 1, // clean
        revLevel: Long = 1, // dynamic
        compat: Long = 0x4L, // has_journal
        incompat: Long = 0xC0L, // extents | 64bit
        roCompat: Long = 0x21L, // sparse_super | gdt_csum
        journalInum: Long = 8L,
        uuid: ByteArray = defaultUuid(),
        volumeName: String = "ext4_test",
        magic: Int = 0xEF53,
    ): ByteArray {
        val sb = ByteArray(SB_BYTES)
        putU32(sb, 0, inodesCount.toInt())
        putU32(sb, 4, blocksCountLo.toInt())
        putU32(sb, 24, logBlockSize.toInt())
        putU16(sb, 56, magic)
        putU16(sb, 58, state)
        putU32(sb, 76, revLevel.toInt())
        putU32(sb, 92, compat.toInt())
        putU32(sb, 96, incompat.toInt())
        putU32(sb, 100, roCompat.toInt())
        uuid.copyInto(sb, 104)
        putString(sb, 120, volumeName)
        putU32(sb, 224, journalInum.toInt())
        putU32(sb, 336, 0) // s_blocks_count_hi
        return image(sb)
    }

    // ---------- F2FS ----------

    fun buildF2fs(
        major: Int = 1,
        minor: Int = 16,
        logBlockSize: Long = 12, // 4096
        blockCount: Long = 2_000_000L,
        segmentCount: Long = 1024L,
        cpBlkaddr: Long = 4L,
        uuid: ByteArray = defaultUuid(),
        volumeName: String = "f2fs_test",
        version: String = "6.1.25-android14-2",
        feature: Long = 0x11L, // encrypt | sb_checksum
        checkpointVersion: Long = 1001L,
        magic: Long = 0xF2F52010L,
    ): ByteArray {
        val sb = ByteArray(SB_BYTES)
        putU32(sb, 0, magic.toInt())
        putU16(sb, 4, major)
        putU16(sb, 6, minor)
        putU32(sb, 16, logBlockSize.toInt())
        putU64(sb, 36, blockCount)
        putU32(sb, 48, segmentCount.toInt())
        putU32(sb, 76, cpBlkaddr.toInt())
        uuid.copyInto(sb, 108)
        putUtf16Le(sb, 124, volumeName)
        putString(sb, 1668, version)
        putU32(sb, 2180, feature.toInt())

        // checkpoint version berada di offset absolut device: cp_blkaddr * block_size + 8
        val cpOffset = cpBlkaddr * 4096L + 8
        val out = image(sb, minBytes = cpOffset + 8)
        putU64(out, cpOffset.toInt(), checkpointVersion)
        return out
    }

    // ---------- korup / tak dikenal ----------

    /** File dengan superblock non-magic (garbage) — ukuran layak seperti image. */
    fun buildCorrupted(): ByteArray {
        val sb = ByteArray(SB_BYTES)
        putString(sb, 0, "NOTAFILESYSTEM")
        return image(sb)
    }

    /** File pendek (< 1024 + 4) yang pasti gagal dibaca sebagai superblock. */
    fun buildTooSmall(): ByteArray = ByteArray(512)

    // ---------- helper ----------

    /** Image: zero padding 1024 lalu superblock (offset superblock sudah benar). */
    private fun image(sb: ByteArray, minBytes: Long = SUPERBLOCK_OFFSET + SB_BYTES): ByteArray {
        val size = maxOf(minBytes, SUPERBLOCK_OFFSET + sb.size)
        val out = ByteArray(size.toInt())
        sb.copyInto(out, SUPERBLOCK_OFFSET.toInt())
        return out
    }

    fun defaultUuid(): ByteArray = ByteArray(16) { (it + 1).toByte() }

    fun write(dir: File, name: String, bytes: ByteArray): File {
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    /** Menulis superblock EROFS ke sparse file besar (mis. 12 GB). */
    fun writeSparse(dir: File, name: String, size: Long, bytes: ByteArray): File {
        val file = File(dir, name)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(size)
            raf.seek(0)
            raf.write(bytes)
        }
        return file
    }

    private fun putString(buf: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        bytes.copyInto(buf, offset)
    }

    private fun putUtf16Le(buf: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_16LE)
        bytes.copyInto(buf, offset)
    }

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun putU64(buf: ByteArray, offset: Int, value: Long) {
        putU32(buf, offset, (value and 0xFFFF_FFFFL).toInt())
        putU32(buf, offset + 4, ((value ushr 32) and 0xFFFF_FFFFL).toInt())
    }
}
