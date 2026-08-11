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

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Helper test untuk membangun super.img dengan metadata logical partition
 * yang valid (format liblp AOSP). Dipakai bersama oleh SuperParserTest dan
 * test integrasi workspace.
 */
object TestSuperImageBuilder {

    const val GEOMETRY_MAGIC = 0x616c4467L // "gDla"
    const val HEADER_MAGIC = 0x414C5030L // "0PLA"
    const val HEADER_SIZE = 256
    const val GEOMETRY_SIZE = 4096
    const val SECTOR_SIZE = 512L
    private const val HDR_FLAG_VIRTUAL_AB = 0x1L

    data class PartitionSpec(
        val name: String,
        val size: Long,
        val groupIndex: Int = 1,
        val attributes: Int = 0,
    )

    val defaultPartitions =
        listOf(
            PartitionSpec("system", 4L * 1024 * 1024 * 1024, groupIndex = 1, attributes = 0x1),
            PartitionSpec("system_ext", 1L * 1024 * 1024 * 1024, groupIndex = 1, attributes = 0x1),
            PartitionSpec("product", 2L * 1024 * 1024 * 1024, groupIndex = 1, attributes = 0x1),
            PartitionSpec("vendor", 2L * 1024 * 1024 * 1024, groupIndex = 1, attributes = 0x1),
            PartitionSpec("odm", 512L * 1024 * 1024, groupIndex = 1, attributes = 0x1),
            PartitionSpec("vendor_dlkm", 256L * 1024 * 1024, groupIndex = 1),
        )

    fun build(
        major: Int = 10,
        minor: Int = 2,
        blockSize: Int = 4096,
        slotCount: Int = 2,
        virtualAb: Boolean = true,
        partitions: List<PartitionSpec> = defaultPartitions,
        groups: List<Pair<String, Long>> = listOf("default" to 0L, "main" to (8L * 1024 * 1024 * 1024)),
        corruptTables: Boolean = false,
        headerMagic: Long = HEADER_MAGIC,
    ): ByteArray {
        val metadataMaxSize = 65536

        // ---- tabel ----
        val partBytes = ByteArrayOutputStream()
        val extBytes = ByteArrayOutputStream()
        partitions.forEachIndexed { index, spec ->
            partBytes.write(bytes(partitionEntry(spec)))
            val numSectors = spec.size / SECTOR_SIZE
            extBytes.write(bytes(extentEntry(numSectors, index * 1_000_000L)))
        }
        val groupBytes = ByteArrayOutputStream()
        groups.forEach { (name, maxSize) ->
            groupBytes.write(bytes(groupEntry(name, maxSize)))
        }
        val deviceBytes = ByteArrayOutputStream()
        deviceBytes.write(bytes(blockDeviceEntry("super", partitions.sumOf { it.size })))

        val tables = concat(partBytes.toByteArray(), extBytes.toByteArray(), groupBytes.toByteArray(), deviceBytes.toByteArray())

        val partitionsDesc = tableDesc(0, partitions.size, 52)
        val extentsDesc = tableDesc(partitions.size * 52, partitions.size, 24)
        val groupsDesc = tableDesc(partitions.size * 52 + partitions.size * 24, groups.size, 48)
        val devicesDesc = tableDesc(partitions.size * 52 + partitions.size * 24 + groups.size * 48, 1, 64)

        // ---- header (checksum tabel dulu, lalu checksum header) ----
        val header = ByteArray(HEADER_SIZE)
        putU32(header, 0, headerMagic)
        putU16(header, 4, major)
        putU16(header, 6, minor)
        putU32(header, 8, HEADER_SIZE.toLong())
        putU32(header, 44, tables.size.toLong())
        putDesc(header, 80, partitionsDesc)
        putDesc(header, 92, extentsDesc)
        putDesc(header, 104, groupsDesc)
        putDesc(header, 116, devicesDesc)
        putU32(header, 128, if (virtualAb) HDR_FLAG_VIRTUAL_AB else 0L)
        // tables checksum di header[48..80]
        sha256(tables).copyInto(header, 48)
        // header checksum dengan field checksum (12..44) di-nol-kan
        val headerForChecksum = header.copyOf()
        java.util.Arrays.fill(headerForChecksum, 12, 44, 0)
        sha256(headerForChecksum).copyInto(header, 12)

        // ---- susun file ----
        val metadataStart = blockSize * 2
        val out = ByteArray(metadataStart + HEADER_SIZE + tables.size)
        putU32(out, 0, GEOMETRY_MAGIC) // geometry block 0
        putU32(out, 4, 52L)
        putU32(out, 40, metadataMaxSize.toLong())
        putU32(out, 44, slotCount.toLong())
        putU32(out, 48, blockSize.toLong())
        header.copyInto(out, metadataStart)
        tables.copyInto(out, metadataStart + HEADER_SIZE)
        if (corruptTables && tables.isNotEmpty()) {
            // korupsi SETELAH checksum dihitung -> checksum tables jadi tidak valid
            val corruptAt = metadataStart + HEADER_SIZE
            out[corruptAt] = (out[corruptAt].toInt() xor 0x55).toByte()
        }
        return out
    }

    fun write(dir: File, name: String, bytes: ByteArray): File {
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    // ---------- entry ----------

    private fun partitionEntry(spec: PartitionSpec): ByteArray {
        val buf = ByteArray(52)
        putString(buf, 0, spec.name)
        putU32(buf, 36, spec.attributes.toLong())
        putU32(buf, 44, 1L) // satu extent per partisi
        putU32(buf, 48, spec.groupIndex.toLong())
        return buf
    }

    private fun extentEntry(numSectors: Long, targetData: Long): ByteArray {
        val buf = ByteArray(24)
        putU64(buf, 0, numSectors)
        putU32(buf, 8, 0L) // target_type = LINEAR
        putU64(buf, 12, targetData)
        putU32(buf, 20, 0L)
        return buf
    }

    private fun groupEntry(name: String, maximumSize: Long): ByteArray {
        val buf = ByteArray(48)
        putString(buf, 0, name)
        putU64(buf, 40, maximumSize)
        return buf
    }

    private fun blockDeviceEntry(name: String, size: Long): ByteArray {
        val buf = ByteArray(64)
        putU64(buf, 0, 2048L) // first_logical_sector
        putU32(buf, 8, 1_048_576L) // alignment 1 MiB
        putU64(buf, 16, size)
        putString(buf, 24, name)
        return buf
    }

    private fun tableDesc(offset: Int, count: Int, entrySize: Int): LongArray =
        longArrayOf(offset.toLong(), count.toLong(), entrySize.toLong())

    // ---------- helpers ----------

    private fun putDesc(buf: ByteArray, offset: Int, desc: LongArray) {
        putU32(buf, offset, desc[0])
        putU32(buf, offset + 4, desc[1])
        putU32(buf, offset + 8, desc[2])
    }

    private fun putString(buf: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.UTF_8).copyInto(buf, offset)
    }

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(buf: ByteArray, offset: Int, value: Long) {
        val v = value.toInt()
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((v ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun putU64(buf: ByteArray, offset: Int, value: Long) {
        putU32(buf, offset, value and 0xFFFFFFFFL)
        putU32(buf, offset + 4, (value ushr 32) and 0xFFFFFFFFL)
    }

    private fun concat(vararg blocks: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            blocks.forEach { write(it) }
        }.toByteArray()

    private fun bytes(arr: ByteArray): ByteArray = arr

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
