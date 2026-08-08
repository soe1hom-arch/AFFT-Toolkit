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

package com.afft.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SparseImageTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val magic = 0xED26FF3A.toInt()
    private val rawChunk = 0xCAC1
    private val fillChunk = 0xCAC2
    private val dontcareChunk = 0xCAC3
    private val crc32Chunk = 0xCAC4

    private data class ChunkSpec(
        val type: Int,
        val chunkBlocks: Int,
        val payload: ByteArray,
    )

    private fun writeSparse(
        file: File,
        blockSize: Int = 4096,
        totalBlocks: Int,
        specs: List<ChunkSpec>,
        totalChunksOverride: Int? = null,
        majorVer: Int = 1,
        blockSizeOverride: Int? = null,
    ) {
        RandomAccessFile(file, "rw").use { raf ->
            val header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(magic)
            header.putShort(majorVer.toShort())
            header.putShort(0) // minor
            header.putShort(28) // file_hdr_sz
            header.putShort(12) // chunk_hdr_sz
            header.putInt(blockSizeOverride ?: blockSize)
            header.putInt(totalBlocks)
            header.putInt(totalChunksOverride ?: specs.size)
            header.putInt(0) // crc
            raf.write(header.array())

            for (spec in specs) {
                val chunkHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                chunkHeader.putShort(spec.type.toShort())
                chunkHeader.putShort(0) // reserved
                chunkHeader.putInt(spec.chunkBlocks)
                chunkHeader.putInt(12 + spec.payload.size)
                raf.write(chunkHeader.array())
                raf.write(spec.payload)
            }
        }
    }

    @Test
    fun isSparseImage_detectsMagic() {
        val sparse = temp.newFile("sparse.img")
        writeSparse(sparse, totalBlocks = 1, specs = listOf(ChunkSpec(rawChunk, 1, ByteArray(4096))))

        assertTrue(SparseImage.isSparseImage(sparse))

        val plain = temp.newFile("plain.bin")
        plain.writeBytes(ByteArray(128) { it.toByte() })
        assertFalse(SparseImage.isSparseImage(plain))
        assertFalse(SparseImage.isSparseImage(File(temp.root, "nonexistent.img")))
    }

    @Test
    fun validate_acceptsValidMixedImage() {
        val sparse = temp.newFile("mixed.img")
        writeSparse(
            sparse,
            totalBlocks = 3,
            specs =
                listOf(
                    ChunkSpec(rawChunk, 1, ByteArray(4096) { (it % 251).toByte() }),
                    ChunkSpec(fillChunk, 1, byteArrayOf(0x11, 0x22, 0x33, 0x44)),
                    ChunkSpec(dontcareChunk, 1, ByteArray(0)),
                ),
        )

        val result = SparseImage.validate(sparse)
        assertTrue(result.valid)
        assertEquals(3 * 4096L, result.expectedRawSize)
        assertEquals(3L, result.totalChunks)
    }

    @Test
    fun sparseToRaw_combinesRawFillAndDontcareChunks() {
        val sparse = temp.newFile("valid.img")
        val rawData = ByteArray(4096) { (it % 251).toByte() }
        val fillPattern = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        writeSparse(
            sparse,
            totalBlocks = 3,
            specs =
                listOf(
                    ChunkSpec(rawChunk, 1, rawData),
                    ChunkSpec(fillChunk, 1, fillPattern),
                    ChunkSpec(dontcareChunk, 1, ByteArray(0)),
                ),
        )

        val raw = File(temp.root, "raw.img")
        val ok = SparseImage.sparseToRaw(sparse, raw)

        assertTrue(ok)
        assertEquals(3 * 4096L, raw.length())
        val bytes = raw.readBytes()
        assertTrue(rawData.contentEquals(bytes.copyOfRange(0, 4096)))
        for (i in 0 until 4096) {
            assertEquals(fillPattern[i % 4], bytes[4096 + i])
        }
        for (i in 0 until 4096) {
            assertEquals(0, bytes[8192 + i].toInt())
        }
    }

    @Test
    fun sparseToRaw_rejectsNonSparseFile() {
        val plain = temp.newFile("plain.img")
        val content = ByteArray(512) { 0x5A.toByte() }
        plain.writeBytes(content)

        val out = File(temp.root, "out.img")
        val ok = SparseImage.sparseToRaw(plain, out)

        assertFalse(ok)
        assertFalse(out.exists()) // tidak meninggalkan output
    }

    @Test
    fun sparseToRaw_rejectsTruncatedChunk() {
        val sparse = temp.newFile("truncated.img")
        // Chunk mengklaim 4 blok (16 KB) tapi file hanya berisi 4 KB payload
        RandomAccessFile(sparse, "rw").use { raf ->
            val header = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(magic).putShort(1).putShort(0).putShort(28).putShort(12)
            header.putInt(4096).putInt(4).putInt(1).putInt(0)
            raf.write(header.array())
            val chunk = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            chunk.putShort(rawChunk.toShort()).putShort(0).putInt(4).putInt(12 + 4 * 4096)
            raf.write(chunk.array())
            raf.write(ByteArray(4096)) // hanya 1 dari 4 blok yang diklaim
        }

        assertFalse(SparseImage.validate(sparse).valid)
        val out = File(temp.root, "out.img")
        assertFalse(SparseImage.sparseToRaw(sparse, out))
        assertFalse(out.exists())
    }

    @Test
    fun sparseToRaw_rejectsHugeTotalBlocks() {
        val sparse = temp.newFile("huge.img")
        // Header mengklaim miliaran blok tapi isi chunk tak pernah menutupinya
        writeSparse(
            sparse,
            blockSize = 4096,
            totalBlocks = 1_000_000_000,
            specs = listOf(ChunkSpec(rawChunk, 1, ByteArray(4096))),
        )

        assertFalse(SparseImage.validate(sparse).valid)
        val out = File(temp.root, "out.img")
        assertFalse(SparseImage.sparseToRaw(sparse, out))
        assertFalse(out.exists())
    }

    @Test
    fun sparseToRaw_rejectsChunkExceedingExpected() {
        val sparse = temp.newFile("overflow.img")
        // Header bilang total 2 blok, tapi single FILL chunk mengklaim 4 blok
        writeSparse(
            sparse,
            totalBlocks = 2,
            specs = listOf(ChunkSpec(fillChunk, 4, byteArrayOf(0x01, 0x02, 0x03, 0x04))),
        )

        assertFalse(SparseImage.validate(sparse).valid)
        val out = File(temp.root, "out.img")
        assertFalse(SparseImage.sparseToRaw(sparse, out))
        assertFalse(out.exists())
    }

    @Test
    fun sparseToRaw_rejectsInvalidBlockSize() {
        val sparse = temp.newFile("badblocksize.img")
        writeSparse(
            sparse,
            totalBlocks = 1,
            specs = listOf(ChunkSpec(rawChunk, 1, ByteArray(4096))),
        )
        // Timpa block_size di header (offset 12) dengan 0
        RandomAccessFile(sparse, "rw").use { raf ->
            raf.seek(12)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array())
        }

        assertFalse(SparseImage.validate(sparse).valid)
        val out = File(temp.root, "out.img")
        assertFalse(SparseImage.sparseToRaw(sparse, out))
        assertFalse(out.exists())
    }

    @Test
    fun sparseToRaw_rejectsUnsupportedVersion() {
        val sparse = temp.newFile("badver.img")
        writeSparse(
            sparse,
            totalBlocks = 1,
            specs = listOf(ChunkSpec(rawChunk, 1, ByteArray(4096))),
            majorVer = 2,
        )
        assertFalse(SparseImage.validate(sparse).valid)
    }

    @Test
    fun sparseToRaw_crc32ChunkIsTolerated() {
        val sparse = temp.newFile("crc.img")
        writeSparse(
            sparse,
            totalBlocks = 1,
            specs =
                listOf(
                    ChunkSpec(crc32Chunk, 0, byteArrayOf(0x01, 0x02, 0x03, 0x04)),
                    ChunkSpec(rawChunk, 1, ByteArray(4096) { 0x42.toByte() }),
                ),
        )
        assertTrue(SparseImage.validate(sparse).valid)
        val out = File(temp.root, "out.img")
        assertTrue(SparseImage.sparseToRaw(sparse, out))
        assertEquals(4096L, out.length())
    }

    @Test
    fun detectFilesystemType_detectsErofs() {
        val file = temp.newFile("erofs.img")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0x500)
            raf.seek(0x400)
            raf.write(byteArrayOf(0xE2.toByte(), 0xE1.toByte(), 0xF5.toByte(), 0xE0.toByte()))
        }
        assertEquals("erofs", SparseImage.detectFilesystemType(file))
    }

    @Test
    fun detectFilesystemType_detectsExt4() {
        val file = temp.newFile("ext4.img")
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0x500)
            raf.seek(0x438)
            raf.write(byteArrayOf(0x53.toByte(), 0xEF.toByte()))
        }
        assertEquals("ext4", SparseImage.detectFilesystemType(file))
    }

    @Test
    fun detectFilesystemType_detectsGzip() {
        val file = temp.newFile("fs.gz")
        file.writeBytes(byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08))
        assertEquals("gzip", SparseImage.detectFilesystemType(file))
    }
}
