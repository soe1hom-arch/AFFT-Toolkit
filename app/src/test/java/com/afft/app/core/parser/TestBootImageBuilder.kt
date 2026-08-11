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

/**
 * Helper test untuk membangun boot image (v0-v2 & v3) yang valid.
 * Dipakai bersama oleh BootParserTest dan test integrasi workspace.
 */
object TestBootImageBuilder {

    const val BOOT_MAGIC = "ANDROID!"
    const val V0_HEADER_SIZE = 1632
    const val V3_HEADER_SIZE = 1580

    fun buildV0(
        kernelSize: Int = 512,
        ramdiskSize: Int = 64,
        pageSize: Int = 2048,
        headerVersion: Int = 0,
        osVersionRaw: Long = encodeOsVersion(15, 0, 2026, 7),
        cmdline: String = "console=ttyMSM0,115200n8 androidboot.hardware=qcom",
        name: String = "boot_test",
        magic: String = BOOT_MAGIC,
        kernel: ByteArray = elfKernel(183),
        ramdisk: ByteArray = cpioRamdisk(ramdiskSize),
    ): ByteArray {
        val header = ByteArray(V0_HEADER_SIZE)
        putString(header, 0, magic)
        putU32(header, 8, kernelSize)
        putU32(header, 12, 0x00008000) // kernel_addr
        putU32(header, 16, ramdiskSize)
        putU32(header, 20, 0x01000000) // ramdisk_addr
        putU32(header, 24, 0) // second_size
        putU32(header, 32, 0x00000100) // tags_addr
        putU32(header, 36, pageSize)
        putU32(header, 40, headerVersion)
        putU32(header, 44, osVersionRaw.toInt())
        putString(header, 48, name)
        putString(header, 64, cmdline)

        val kernelOffset = pageSize
        val ramdiskOffset = alignUp(kernelOffset + kernelSize, pageSize)
        val out = ByteArray(ramdiskOffset + ramdisk.size)
        header.copyInto(out, 0)
        kernel.copyInto(out, kernelOffset)
        ramdisk.copyInto(out, ramdiskOffset)
        return out
    }

    fun buildV3(
        kernelSize: Int = 512,
        ramdiskSize: Int = 64,
        osVersionRaw: Long = encodeOsVersion(14, 2, 2026, 3),
        cmdline: String = "androidboot.hardware=exynos",
        magic: String = BOOT_MAGIC,
        kernel: ByteArray = elfKernel(183),
        ramdisk: ByteArray = cpioRamdisk(ramdiskSize),
    ): ByteArray {
        val header = ByteArray(V3_HEADER_SIZE)
        putString(header, 0, magic)
        putU32(header, 8, kernelSize)
        putU32(header, 12, ramdiskSize)
        putU32(header, 16, osVersionRaw.toInt())
        putU32(header, 20, V3_HEADER_SIZE) // header_size
        putU32(header, 40, 3) // header_version
        putString(header, 44, cmdline)

        val kernelOffset = V3_HEADER_SIZE
        val ramdiskOffset = kernelOffset + kernelSize
        val out = ByteArray(ramdiskOffset + ramdisk.size)
        header.copyInto(out, 0)
        kernel.copyInto(out, kernelOffset)
        ramdisk.copyInto(out, ramdiskOffset)
        return out
    }

    /** Kernel ELF64 dengan e_machine tertentu (default 183 = AArch64). */
    fun elfKernel(machine: Int = 183): ByteArray {
        val buf = ByteArray(64)
        buf[0] = 0x7F
        buf[1] = 'E'.code.toByte()
        buf[2] = 'L'.code.toByte()
        buf[3] = 'F'.code.toByte()
        buf[4] = 2 // EI_CLASS: ELFCLASS64
        putU16(buf, 16, 2) // e_type: ET_EXEC
        putU16(buf, 18, machine) // e_machine
        return buf
    }

    /** Ramdisk cpio (magic "070701") dengan padding sesuai ukuran. */
    fun cpioRamdisk(size: Int): ByteArray {
        if (size <= 0) return ByteArray(0)
        val body = ByteArray(size)
        putString(body, 0, "070701")
        return body
    }

    fun encodeOsVersion(major: Int, minor: Int, year: Int, month: Int): Long {
        val version = ((major and 0x1F) shl 6) or (minor and 0x3F)
        val patch = (((year - 2000) and 0xFF) shl 16) or ((month and 0x0F) shl 24)
        return (version or patch).toLong()
    }

    fun write(dir: File, name: String, bytes: ByteArray): File {
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    private fun putString(buf: ByteArray, offset: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        bytes.copyInto(buf, offset)
    }

    private fun putU32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun putU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun alignUp(value: Int, alignment: Int): Int =
        ((value + alignment - 1) / alignment) * alignment
}
