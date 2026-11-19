/*
 * Copyright (c) 2026 Wandi (soe1hom-arch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afft.app.service

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VbmetaDiagnosticTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun image(): ByteArray {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val bytes = ByteArray(256 + 320 + 576)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        bytes[0] = 65
        bytes[1] = 86
        bytes[2] = 66
        bytes[3] = 48
        header.putInt(4, 1)
        header.putLong(12, 320)
        header.putLong(20, 576)
        header.putInt(28, 1)
        header.putLong(40, 32)
        header.putLong(48, 32)
        header.putLong(56, 256)
        header.putLong(72, 520)
        val auxStart = 576
        header.putInt(auxStart, 2048)
        val modulus = (pair.public as RSAPublicKey).modulus.toByteArray()
        modulus.copyInto(bytes, auxStart + 8, modulus.size - 256, modulus.size)
        val hash = MessageDigest.getInstance("SHA-256")
        hash.update(bytes, 0, 256)
        hash.update(bytes, auxStart, 576)
        hash.digest().copyInto(bytes, 256)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(pair.private)
        signature.update(bytes, 0, 256)
        signature.update(bytes, auxStart, 576)
        signature.sign().copyInto(bytes, 288)
        return bytes
    }

    @Test
    fun createsSeparateCopiesAndPreservesOriginal() {
        val source = tmp.newFile("vbmeta.img")
        val original = image()
        source.writeBytes(original)
        val outputDir = tmp.newFolder("output")

        val first = VbmetaDiagnostic.createDisabledCopy(source, outputDir)
        val second = VbmetaDiagnostic.createDisabledCopy(source, outputDir)

        assertArrayEquals(original, source.readBytes())
        assertNotEquals(first, second)
        val modified = first.readBytes()
        assertEquals(3, ByteBuffer.wrap(modified).order(ByteOrder.BIG_ENDIAN).getInt(120))
        modified[123] = original[123]
        assertArrayEquals(original, modified)
    }

    @Test
    fun rejectsDamagedHashAndInvalidBlockRange() {
        val source = tmp.newFile("vbmeta.img")
        val output = tmp.newFolder("output")
        val damaged = image()
        damaged[700] = 1
        source.writeBytes(damaged)
        assertThrows(IOException::class.java) { VbmetaDiagnostic.createDisabledCopy(source, output) }

        val badSignature = image()
        badSignature[300] = (badSignature[300].toInt() xor 1).toByte()
        source.writeBytes(badSignature)
        assertThrows(IOException::class.java) { VbmetaDiagnostic.createDisabledCopy(source, output) }

        val invalid = image()
        ByteBuffer.wrap(invalid).order(ByteOrder.BIG_ENDIAN).putLong(104, 577)
        source.writeBytes(invalid)
        assertThrows(IOException::class.java) { VbmetaDiagnostic.createDisabledCopy(source, output) }
        assertEquals(0, output.listFiles()!!.size)
    }

    @Test
    fun supportsUnsignedImageWithoutTreatingItAsAuthenticated() {
        val source = tmp.newFile("vbmeta.img")
        val bytes = ByteArray(256)
        bytes[0] = 65
        bytes[1] = 86
        bytes[2] = 66
        bytes[3] = 48
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(4, 1)
        source.writeBytes(bytes)

        val output = VbmetaDiagnostic.createDisabledCopy(source, tmp.newFolder("output"))

        assertEquals(3, ByteBuffer.wrap(output.readBytes()).order(ByteOrder.BIG_ENDIAN).getInt(120))
        assertArrayEquals(bytes, source.readBytes())
        assertTrue(output.name.contains("VERIFICATION_DISABLED"))
    }
}
