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

/**
 * Reader protobuf wire-format minimal & aman untuk membaca manifest payload.
 * Field tak dikenal selalu di-skip; data rusak dilempar sebagai
 * [IndexOutOfBoundsException] yang ditangani oleh [PayloadParser].
 */
internal class ProtoReader(private val data: ByteArray) {

    private var pos = 0

    val hasNext: Boolean
        get() = pos < data.size

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= data.size && shift > 0) throw IndexOutOfBoundsException("truncated varint")
            val b = data[pos++]
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b.toInt() and 0x80 == 0) break
            shift += 7
            if (shift > 70) throw IndexOutOfBoundsException("varint too long")
        }
        return result
    }

    /** Mengambil payload field dengan wire-type panjang (prefix length). */
    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        if (length < 0 || pos + length > data.size) throw IndexOutOfBoundsException("length prefix out of bounds")
        val out = data.copyOfRange(pos, pos + length)
        pos += length
        return out
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun skipBytes() {
        val length = readVarint().toInt()
        if (length < 0 || pos + length > data.size) throw IndexOutOfBoundsException("skip out of bounds")
        pos += length
    }

    fun skipField(wire: Int) {
        when (wire) {
            0 -> readVarint()
            1 -> skipFixed(8)
            2 -> skipBytes()
            5 -> skipFixed(4)
            3 -> throw IndexOutOfBoundsException("unsupported group wire type")
            else -> throw IndexOutOfBoundsException("unknown wire type $wire")
        }
    }

    private fun skipFixed(bytes: Int) {
        if (pos + bytes > data.size) throw IndexOutOfBoundsException("fixed field out of bounds")
        pos += bytes
    }
}
