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

package com.afft.app.service

import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/**
 * Produces a standalone top-level vbmeta copy like fastboot's
 * --disable-verity --disable-verification options. It does not sign images.
 */
object VbmetaDiagnostic {
    private const val HEADER_SIZE = 256
    private const val MAX_IMAGE_SIZE = 16 * 1024 * 1024
    private const val FLAGS_OFFSET = 120

    fun createDisabledCopy(source: File, outputDirectory: File): File {
        if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Pilih file vbmeta biasa, bukan symbolic link")
        }
        val size = Files.size(source.toPath())
        if (size < HEADER_SIZE || size > MAX_IMAGE_SIZE) {
            throw IOException("Ukuran vbmeta tidak didukung")
        }
        val data = ByteArray(size.toInt())
        DataInputStream(Files.newInputStream(source.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use {
            it.readFully(data)
            if (it.read() != -1) throw IOException("File vbmeta berubah saat dibaca")
        }
        val header = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(65, 86, 66, 48))) {
            throw IOException("Bukan image vbmeta AVB0 mandiri")
        }
        if (header.getInt(4) != 1) throw IOException("Versi AVB tidak didukung")
        val authSize = nonNegative(header.getLong(12))
        val auxSize = nonNegative(header.getLong(20))
        if (authSize % 64L != 0L || auxSize % 64L != 0L ||
            authSize > data.size - HEADER_SIZE || auxSize > data.size - HEADER_SIZE - authSize
        ) {
            throw IOException("Panjang blok vbmeta tidak valid")
        }
        val flags = header.getInt(FLAGS_OFFSET)
        if (flags and 3.inv() != 0) throw IOException("Flag vbmeta tidak dikenal")
        val hashOffset = nonNegative(header.getLong(32))
        val hashSize = nonNegative(header.getLong(40))
        val signatureOffset = nonNegative(header.getLong(48))
        val signatureSize = nonNegative(header.getLong(56))
        val publicKeyOffset = nonNegative(header.getLong(64))
        val publicKeySize = nonNegative(header.getLong(72))
        val keyMetadataOffset = nonNegative(header.getLong(80))
        val keyMetadataSize = nonNegative(header.getLong(88))
        val descriptorsOffset = nonNegative(header.getLong(96))
        val descriptorsSize = nonNegative(header.getLong(104))
        checkRange(hashOffset, hashSize, authSize)
        checkRange(signatureOffset, signatureSize, authSize)
        checkRange(publicKeyOffset, publicKeySize, auxSize)
        checkRange(keyMetadataOffset, keyMetadataSize, auxSize)
        checkRange(descriptorsOffset, descriptorsSize, auxSize)

        val algorithm = header.getInt(28)
        if (algorithm == 0) {
            if (hashSize != 0L || signatureSize != 0L || publicKeySize != 0L) {
                throw IOException("Data autentikasi vbmeta unsigned tidak valid")
            }
        } else {
            val digestName = when (algorithm) {
                1, 2, 3 -> "SHA-256"
                4, 5, 6 -> "SHA-512"
                else -> throw IOException("Algoritma AVB tidak didukung")
            }
            val keyBits = when (algorithm) {
                1, 4 -> 2048
                2, 5 -> 4096
                else -> 8192
            }
            val keyBytes = keyBits / 8
            if (signatureSize != keyBytes.toLong() || publicKeySize != (8L + 2L * keyBytes)) {
                throw IOException("Ukuran kunci atau tanda tangan AVB tidak valid")
            }
            val auxStart = HEADER_SIZE + authSize.toInt()
            val keyStart = auxStart + publicKeyOffset.toInt()
            if (header.getInt(keyStart) != keyBits) throw IOException("Ukuran kunci AVB tidak cocok")
            val modulus = BigInteger(1, data.copyOfRange(keyStart + 8, keyStart + 8 + keyBytes))
            val digest = MessageDigest.getInstance(digestName)
            digest.update(data, 0, HEADER_SIZE)
            digest.update(data, auxStart, auxSize.toInt())
            val calculated = digest.digest()
            if (hashSize != calculated.size.toLong() ||
                !MessageDigest.isEqual(calculated, data.copyOfRange(
                    HEADER_SIZE + hashOffset.toInt(),
                    HEADER_SIZE + hashOffset.toInt() + calculated.size,
                ))
            ) {
                throw IOException("Hash vbmeta tidak cocok atau data autentikasi tidak lengkap")
            }
            val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger.valueOf(65537)))
            val verifier = Signature.getInstance(if (digestName == "SHA-256") "SHA256withRSA" else "SHA512withRSA")
            verifier.initVerify(key)
            verifier.update(data, 0, HEADER_SIZE)
            verifier.update(data, auxStart, auxSize.toInt())
            val signatureStart = HEADER_SIZE + signatureOffset.toInt()
            if (!verifier.verify(data, signatureStart, signatureSize.toInt())) {
                throw IOException("Tanda tangan vbmeta tidak cocok dengan kunci di dalam image")
            }
        }

        header.putInt(FLAGS_OFFSET, flags or 3)
        Files.createDirectories(outputDirectory.toPath())
        val runDir = Files.createTempDirectory(outputDirectory.toPath(), "vbmeta-disabled-")
        val output = runDir.resolve("vbmeta_VERIFICATION_DISABLED.img")
        try {
            Files.write(output, data)
            if (!Files.readAllBytes(output).contentEquals(data)) {
                throw IOException("Salinan vbmeta tidak cocok")
            }
        } catch (e: Exception) {
            Files.deleteIfExists(output)
            Files.deleteIfExists(runDir)
            throw e
        }
        return output.toFile()
    }

    private fun nonNegative(value: Long): Long {
        if (value < 0) throw IOException("Ukuran atau offset vbmeta terlalu besar")
        return value
    }

    private fun checkRange(offset: Long, length: Long, blockSize: Long) {
        if (offset > blockSize || length > blockSize - offset) {
            throw IOException("Offset atau ukuran vbmeta tidak valid")
        }
    }
}
