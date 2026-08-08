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

import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseImage {
    private val SPARSE_MAGIC = 0xED26FF3A.toInt()
    private const val SPARSE_FILE_HDR_SIZE = 28
    private const val SPARSE_CHUNK_HDR_SIZE = 12
    private const val RAW_CHUNK_TYPE = 0xCAC1
    private const val FILL_CHUNK_TYPE = 0xCAC2
    private const val DONTCARE_CHUNK_TYPE = 0xCAC3
    private const val CRC32_CHUNK_TYPE = 0xCAC4
    private const val MAX_BLOCK_SIZE = 16 * 1024 * 1024

    /**
     * Hasil validasi sparse image terstruktur.
     * [validate] TIDAK pernah melempar exception / crash pada input apa pun.
     */
    data class SparseValidation(
        val valid: Boolean,
        val reason: String? = null,
        val blockSize: Int = 0,
        val totalBlocks: Long = 0,
        val totalChunks: Long = 0,
        val expectedRawSize: Long = 0,
    )

    fun isSparseImage(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                val bb = ByteBuffer.wrap(magic).order(ByteOrder.LITTLE_ENDIAN)
                bb.getInt() == SPARSE_MAGIC
            }
        } catch (e: Exception) {
            false
        }
    }

    fun detectFilesystemType(file: File): String {
        if (!file.exists()) return "unknown"

        // Jika sparse image, konversi dulu ke raw baru deteksi
        var targetFile = file
        val tempRawFile: File? =
            if (isSparseImage(file)) {
                try {
                    val raw = File(file.parentFile, "${file.nameWithoutExtension}_raw_detect.img")
                    if (sparseToRaw(file, raw)) {
                        android.util.Log.d("SparseImage", "detectFilesystemType: converted sparse->raw for detection")
                        raw
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SparseImage", "detectFilesystemType: sparse->raw failed: ${e.message}")
                    null
                }
            } else {
                null
            }

        if (tempRawFile != null && tempRawFile.exists() && tempRawFile.length() > 0) {
            targetFile = tempRawFile
        }

        val result =
            try {
                RandomAccessFile(targetFile, "r").use { raf ->
                    val fileLength = raf.length()

                    // ==================== EROFS CHECK ====================
                    // EROFS superblock is at offset 0x400 with magic 0xE0F5E1E2
                    // (hanya dicek jika file cukup besar agar tidak EOF pada file kecil)
                    if (fileLength >= 0x404) {
                        raf.seek(0x400)
                        val erofsMagic = ByteArray(4)
                        raf.readFully(erofsMagic)
                        if ((erofsMagic[0].toInt() and 0xFF) == 0xE2 &&
                            (erofsMagic[1].toInt() and 0xFF) == 0xE1 &&
                            (erofsMagic[2].toInt() and 0xFF) == 0xF5 &&
                            (erofsMagic[3].toInt() and 0xFF) == 0xE0
                        ) {
                            "erofs"
                        } else {
                            detectSmallFileType(raf, fileLength)
                        }
                    } else {
                        detectSmallFileType(raf, fileLength)
                    }
                }
            } catch (e: Exception) {
                "unknown"
            } finally {
                // Hapus file temporary raw jika ada
                if (tempRawFile != null && tempRawFile.exists()) {
                    tempRawFile.delete()
                }
            }

        return result
    }

    /**
     * Deteksi tipe filesystem pada offset-awal untuk file yang tidak punya
     * superblock EROFS (file pendek / non-EROFS): F2FS, gzip, ext4.
     */
    private fun detectSmallFileType(
        raf: RandomAccessFile,
        fileLength: Long,
    ): String {
        raf.seek(0)
        val readLen = minOf(4L, fileLength).toInt()
        val magic4 = ByteArray(readLen)
        raf.readFully(magic4)
        val b0 = if (readLen > 0) magic4[0].toInt() and 0xFF else 0
        val b1 = if (readLen > 1) magic4[1].toInt() and 0xFF else 0
        val b2 = if (readLen > 2) magic4[2].toInt() and 0xFF else 0
        val b3 = if (readLen > 3) magic4[3].toInt() and 0xFF else 0

        if ((b0 == 0x10 && b1 == 0x20 && b2 == 0xF5 && b3 == 0xF2) ||
            (b0 == 0xF2 && b1 == 0xF5 && b2 == 0x20 && b3 == 0x10)
        ) {
            return "f2fs"
        }
        if (b0 == 0x1F && b1 == 0x8B) {
            return "gzip"
        }
        if (fileLength >= 0x43A) {
            raf.seek(0x438)
            val ext4magic = ByteArray(2)
            raf.readFully(ext4magic)
            val ext4b0 = ext4magic[0].toInt() and 0xFF
            val ext4b1 = ext4magic[1].toInt() and 0xFF
            if (ext4b0 == 0x53 && ext4b1 == 0xEF) return "ext4"
        }
        return "unknown"
    }

    /**
     * Validasi penuh struktur sparse image (header + semua chunk header)
     * TANPA konversi dan tanpa membaca payload.
     *
     * Menolak: magic salah, versi tak dikenal, ukuran header/block tak wajar,
     * chunk count mustahil, chunk terpotong, ukuran chunk tidak konsisten,
     * dan total blok tidak cocok dengan isi chunk (mencegah disk exhaustion).
     */
    fun validate(file: File): SparseValidation {
        if (!file.exists()) return SparseValidation(false, "File not found: ${file.name}")
        return try {
            RandomAccessFile(file, "r").use { raf -> validateWith(raf, file.length()) }
        } catch (e: Exception) {
            SparseValidation(false, "Invalid sparse image: ${e.message}")
        }
    }

    private fun validateWith(
        raf: RandomAccessFile,
        fileLength: Long,
    ): SparseValidation {
        if (fileLength < SPARSE_FILE_HDR_SIZE) {
            return SparseValidation(false, "File too small for sparse header")
        }
        val header = ByteArray(SPARSE_FILE_HDR_SIZE)
        raf.readFully(header)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getInt() != SPARSE_MAGIC) {
            return SparseValidation(false, "Not a sparse image (magic mismatch)")
        }
        val majorVer = bb.getShort().toInt() and 0xFFFF
        val minorVer = bb.getShort().toInt() and 0xFFFF
        val fileHdrSize = bb.getShort().toInt() and 0xFFFF
        val chunkHdrSize = bb.getShort().toInt() and 0xFFFF
        val blockSize = bb.getInt()
        val totalBlocks = bb.getInt().toLong() and 0xFFFF_FFFFL
        val totalChunks = bb.getInt().toLong() and 0xFFFF_FFFFL

        if (majorVer != 1) {
            return SparseValidation(false, "Unsupported sparse version $majorVer.$minorVer")
        }
        if (fileHdrSize < SPARSE_FILE_HDR_SIZE) {
            return SparseValidation(false, "Invalid file header size $fileHdrSize")
        }
        if (chunkHdrSize < SPARSE_CHUNK_HDR_SIZE) {
            return SparseValidation(false, "Invalid chunk header size $chunkHdrSize")
        }
        if (blockSize <= 0 || blockSize > MAX_BLOCK_SIZE || (blockSize and (blockSize - 1)) != 0) {
            return SparseValidation(false, "Invalid block size $blockSize")
        }
        if (totalBlocks <= 0L) {
            return SparseValidation(false, "Image contains no blocks")
        }
        val expectedRawSize = totalBlocks * blockSize
        val minChunkBytes = maxOf(chunkHdrSize, SPARSE_CHUNK_HDR_SIZE).toLong()
        val maxPossibleChunks = (fileLength - fileHdrSize) / minChunkBytes
        if (totalChunks > maxPossibleChunks) {
            return SparseValidation(false, "Chunk count $totalChunks exceeds file capacity")
        }

        var cursor = fileHdrSize.toLong()
        var written = 0L
        for (i in 0 until totalChunks) {
            if (cursor + chunkHdrSize > fileLength) {
                return SparseValidation(false, "Chunk $i header is truncated")
            }
            raf.seek(cursor)
            val chunkHeader = ByteArray(SPARSE_CHUNK_HDR_SIZE)
            raf.readFully(chunkHeader)
            val cb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
            val chunkType = cb.getShort().toInt() and 0xFFFF
            cb.getShort() // reserved
            val chunkBlocks = cb.getInt().toLong() and 0xFFFF_FFFFL
            val chunkTotalSize = cb.getInt().toLong() and 0xFFFF_FFFFL

            if (chunkTotalSize < chunkHdrSize) {
                return SparseValidation(false, "Chunk $i total size smaller than its header")
            }
            if (cursor + chunkTotalSize > fileLength) {
                return SparseValidation(false, "Chunk $i data is truncated")
            }
            val blockBytes = chunkBlocks * blockSize
            when (chunkType) {
                RAW_CHUNK_TYPE -> {
                    val dataSize = chunkTotalSize - chunkHdrSize
                    if (dataSize != blockBytes) {
                        return SparseValidation(false, "RAW chunk $i size mismatch")
                    }
                    written += blockBytes
                }
                FILL_CHUNK_TYPE, DONTCARE_CHUNK_TYPE -> {
                    written += blockBytes
                }
                CRC32_CHUNK_TYPE -> {
                    if (chunkTotalSize - chunkHdrSize != 4L) {
                        return SparseValidation(false, "CRC32 chunk $i must carry exactly 4 bytes")
                    }
                }
                else -> {
                    // Tipe tak dikenal: toleransi, lewati sesuai ukuran
                }
            }
            if (written > expectedRawSize) {
                return SparseValidation(false, "Chunk data exceeds expected image size")
            }
            cursor += chunkTotalSize
        }

        if (written != expectedRawSize) {
            return SparseValidation(
                false,
                "Chunk blocks ($written bytes) do not match header total ($expectedRawSize bytes)",
            )
        }
        return SparseValidation(
            valid = true,
            blockSize = blockSize,
            totalBlocks = totalBlocks,
            totalChunks = totalChunks,
            expectedRawSize = expectedRawSize,
        )
    }

    /**
     * Convert Android sparse image ke raw image (pure Kotlin, no external binary).
     *
     * Konversi HANYA berjalan setelah [validate] lulus. Setiap inkonsistensi
     * saat konversi membatalkan output (file raw parsial dihapus) dan
     * mengembalikan false — analisis TIDAK pernah dilanjutkan pada image
     * yang korup, dan fungsi tidak pernah crash.
     */
    fun sparseToRaw(
        sparseFile: File,
        rawFile: File,
        isCancelled: () -> Boolean = { false },
    ): Boolean {
        val validation = validate(sparseFile)
        if (!validation.valid) {
            android.util.Log.w("SparseImage", "sparseToRaw: rejected (${validation.reason})")
            return false
        }

        val expectedRawSize = validation.expectedRawSize
        val chunkHdrSize = SPARSE_CHUNK_HDR_SIZE
        return try {
            RandomAccessFile(sparseFile, "r").use { raf ->
                val header = ByteArray(SPARSE_FILE_HDR_SIZE)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                bb.getInt() // magic
                bb.getShort() // major
                bb.getShort() // minor
                bb.getShort() // file_hdr_sz
                bb.getShort() // chunk_hdr_sz
                bb.getInt() // block_size
                bb.getInt() // total_blocks
                val totalChunks = bb.getInt().toLong() and 0xFFFF_FFFFL

                var pos = SPARSE_FILE_HDR_SIZE.toLong()
                var written = 0L

                rawFile.outputStream().use { out ->
                    for (i in 0 until totalChunks) {
                        raf.seek(pos)
                        val chunkHeader = ByteArray(chunkHdrSize)
                        raf.readFully(chunkHeader)
                        val cb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkType = cb.getShort().toInt() and 0xFFFF
                        cb.getShort() // reserved
                        val chunkBlocks = cb.getInt().toLong() and 0xFFFF_FFFFL
                        val chunkTotalSize = cb.getInt().toLong() and 0xFFFF_FFFFL
                        val dataSize = chunkTotalSize - chunkHdrSize
                        val blockBytes = chunkBlocks * validation.blockSize

                        when (chunkType) {
                            RAW_CHUNK_TYPE -> {
                                // Streaming, hindari OOM pada chunk besar
                                raf.seek(pos + chunkHdrSize)
                                var remaining = dataSize
                                val buf = ByteArray(minOf(dataSize, 65536L).toInt())
                                while (remaining > 0) {
                                    if (isCancelled()) error("Cancelled")
                                    val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                    raf.readFully(buf, 0, toRead)
                                    out.write(buf, 0, toRead)
                                    remaining -= toRead
                                }
                                written += blockBytes
                            }
                            FILL_CHUNK_TYPE -> {
                                raf.seek(pos + chunkHdrSize)
                                val fillBytes = ByteArray(4)
                                raf.readFully(fillBytes)
                                writeFill(out, fillBytes, blockBytes) {
                                    if (isCancelled()) error("Cancelled")
                                }
                                written += blockBytes
                            }
                            DONTCARE_CHUNK_TYPE -> {
                                writeZeros(out, blockBytes) {
                                    if (isCancelled()) error("Cancelled")
                                }
                                written += blockBytes
                            }
                            CRC32_CHUNK_TYPE -> {
                                raf.seek(pos + chunkHdrSize)
                                val crcData = ByteArray(4)
                                raf.readFully(crcData)
                            }
                            else -> {
                                // Tipe tak dikenal: lewati payload chunk
                            }
                        }
                        if (written > expectedRawSize) {
                            error("Chunk data exceeds expected image size")
                        }
                        pos += chunkTotalSize
                    }
                }

                if (written != expectedRawSize) {
                    error("Converted size $written != expected $expectedRawSize")
                }
            }

            rawFile.exists() && rawFile.length() == expectedRawSize
        } catch (e: CancellationException) {
            // Teruskan pembatalan ke coroutine pemanggil (jangan ditelan),
            // tapi bersihkan output parsial lebih dulu.
            android.util.Log.w("SparseImage", "sparseToRaw: cancelled")
            if (rawFile.exists()) rawFile.delete()
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SparseImage", "sparseToRaw failed: ${e.message}")
            // Jangan tinggalkan file raw parsial yang bisa dipakai oleh pemanggil
            if (rawFile.exists()) rawFile.delete()
            false
        }
    }


    /**
     * Konversi prefix sparse image ke raw (hanya [maxBytes] pertama dari
     * image logis). Dipakai analisis metadata (super/partisi) agar tidak
     * perlu menulis seluruh image multi-GB ke penyimpanan.
     */
    fun sparseToRawPrefix(
        sparseFile: File,
        rawFile: File,
        maxBytes: Long,
        isCancelled: () -> Boolean = { false },
    ): Boolean {
        val validation = validate(sparseFile)
        if (!validation.valid) {
            android.util.Log.w("SparseImage", "sparseToRawPrefix: rejected (${validation.reason})")
            return false
        }
        val blockSize = validation.blockSize
        val expectedRawSize = validation.expectedRawSize
        val limit = minOf(maxBytes, expectedRawSize)
        return try {
            RandomAccessFile(sparseFile, "r").use { raf ->
                val header = ByteArray(SPARSE_FILE_HDR_SIZE)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                bb.getInt() // magic
                bb.getShort() // major
                bb.getShort() // minor
                bb.getShort() // file_hdr_sz
                val chunkHdrSize = bb.getShort().toInt() and 0xFFFF
                bb.getInt() // block_size
                bb.getInt() // total_blocks
                val totalChunks = bb.getInt().toLong() and 0xFFFF_FFFFL

                var pos = SPARSE_FILE_HDR_SIZE.toLong()
                var logical = 0L
                var written = 0L

                rawFile.outputStream().use { out ->
                    var index = 0L
                    while (logical < limit && index < totalChunks) {
                        raf.seek(pos)
                        val chunkHeader = ByteArray(chunkHdrSize)
                        raf.readFully(chunkHeader)
                        val cb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkType = cb.getShort().toInt() and 0xFFFF
                        cb.getShort() // reserved
                        val chunkBlocks = cb.getInt().toLong() and 0xFFFF_FFFFL
                        val chunkTotalSize = cb.getInt().toLong() and 0xFFFF_FFFFL
                        val chunkBytes = chunkBlocks * blockSize
                        val lo = logical
                        val hi = minOf(logical + chunkBytes, limit)
                        if (lo < hi) {
                            val need = hi - lo
                            when (chunkType) {
                                RAW_CHUNK_TYPE -> {
                                    raf.seek(pos + chunkHdrSize + (lo - logical))
                                    var remaining = need
                                    val buf = ByteArray(minOf(need, 65536L).toInt())
                                    while (remaining > 0) {
                                        if (isCancelled()) error("Cancelled")
                                        val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                        raf.readFully(buf, 0, toRead)
                                        out.write(buf, 0, toRead)
                                        remaining -= toRead
                                    }
                                }
                                FILL_CHUNK_TYPE -> {
                                    raf.seek(pos + chunkHdrSize)
                                    val fillBytes = ByteArray(4)
                                    raf.readFully(fillBytes)
                                    writeFill(out, fillBytes, need) {
                                        if (isCancelled()) error("Cancelled")
                                    }
                                }
                                DONTCARE_CHUNK_TYPE -> {
                                    writeZeros(out, need) {
                                        if (isCancelled()) error("Cancelled")
                                    }
                                }
                                CRC32_CHUNK_TYPE -> {
                                    // tidak menulis apa pun (chunk checksum)
                                }
                                else -> {
                                    // tipe tak dikenal: isi dengan nol agar offset tetap konsisten
                                    writeZeros(out, need) {
                                        if (isCancelled()) error("Cancelled")
                                    }
                                }
                            }
                            written += need
                        }
                        logical += chunkBytes
                        pos += chunkTotalSize
                        index++
                    }
                    // Image logis lebih pendek dari prefix: pad dengan nol.
                    if (logical < limit) {
                        writeZeros(out, limit - logical) {
                            if (isCancelled()) error("Cancelled")
                        }
                    }
                }
            }
            rawFile.exists() && rawFile.length() == limit
        } catch (e: CancellationException) {
            android.util.Log.w("SparseImage", "sparseToRawPrefix: cancelled")
            if (rawFile.exists()) rawFile.delete()
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SparseImage", "sparseToRawPrefix failed: ${e.message}")
            if (rawFile.exists()) rawFile.delete()
            false
        }
    }

    private fun writeFill(
        out: java.io.OutputStream,
        fillBytes: ByteArray,
        size: Long,
        checkCancelled: () -> Unit,
    ) {
        var remaining = size
        val buf = ByteArray(8192)
        var i = 0
        while (i < buf.size) {
            buf[i] = fillBytes[i % 4]
            i++
        }
        while (remaining > 0) {
            checkCancelled()
            val toWrite = minOf(remaining, buf.size.toLong()).toInt()
            out.write(buf, 0, toWrite)
            remaining -= toWrite
        }
    }

    private fun writeZeros(
        out: java.io.OutputStream,
        size: Long,
        checkCancelled: () -> Unit,
    ) {
        var remaining = size
        val buf = ByteArray(8192)
        while (remaining > 0) {
            checkCancelled()
            val toWrite = minOf(remaining, buf.size.toLong()).toInt()
            out.write(buf, 0, toWrite)
            remaining -= toWrite
        }
    }
}
