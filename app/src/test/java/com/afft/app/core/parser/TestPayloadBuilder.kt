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
 * Helper test untuk membangun payload.bin yang valid (dipakai bersama oleh
 * PayloadParserTest dan test integrasi workspace). Menghindari duplikasi
 * logika protobuf kecil di banyak kelas test.
 */
object TestPayloadBuilder {

    data class PartitionSpec(val name: String, val size: Long, val fsType: Int)

    val defaultPartitions =
        listOf(
            PartitionSpec("system", 4_000_000_000L, 4),
            PartitionSpec("vendor", 2_000_000_000L, 4),
        )

    fun buildPayload(
        version: Long = 2L,
        magic: String = "CrAU",
        partitions: List<PartitionSpec> = defaultPartitions,
    ): ByteArray {
        val manifest = buildManifest(partitions)
        // Layout header asli payload.bin (update_engine), big-endian:
        // magic(4) + version u32(4) + manifest_size u64(8) + sig_size u32(4) + padding(4).
        return concat(
            magic.toByteArray(),
            be4(version.toInt()),
            be8(manifest.size.toLong()),
            be4(0), // metadata_signature_size
            be4(0), // padding
            manifest,
        )
    }

    private fun buildManifest(partitions: List<PartitionSpec>): ByteArray {
        val body = ByteArrayOutputStream()
        partitions.forEach { spec ->
            body.write(fieldDelimited(1, partition(spec)))
        }
        body.write(fieldDelimited(6, concat(fieldVarint(2, 1), fieldDelimited(5, "lz4".toByteArray()))))
        body.write(fieldVarint(7, 0))
        return body.toByteArray()
    }

    private fun partition(spec: PartitionSpec): ByteArray =
        concat(
            fieldDelimited(1, spec.name.toByteArray()),
            fieldVarint(2, spec.size),
            fieldDelimited(3, ByteArray(32) { 0x55 }),
            fieldVarint(7, spec.fsType.toLong()),
        )

    private fun fieldDelimited(field: Int, value: ByteArray): ByteArray =
        concat(varintBytes(((field shl 3) or 2).toLong()), varintBytes(value.size.toLong()), value)

    private fun fieldVarint(field: Int, value: Long): ByteArray =
        concat(varintBytes((field shl 3).toLong()), varintBytes(value))

    private fun varintBytes(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            val low = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(low)
                break
            }
            out.write(low or 0x80)
        }
        return out.toByteArray()
    }

    private fun be4(value: Int): ByteArray =
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

    private fun be8(value: Long): ByteArray =
        ByteArray(8) { i -> ((value ushr (56 - 8 * i)) and 0xFF).toByte() }

    private fun concat(vararg blocks: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            blocks.forEach { write(it) }
        }.toByteArray()

    fun write(dir: File, name: String, bytes: ByteArray): File {
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }
}
