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

import com.afft.app.ui.components.dashboard.HealthLevel
import com.afft.app.ui.components.dashboard.ValidationState
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PayloadParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun validPayload_isParsedIntoMetadata() {
        val file = TestPayloadBuilder.write(tmp.root, "valid.bin", TestPayloadBuilder.buildPayload())

        val result = PayloadParser.analyze(file)

        assertTrue(result is ParserResult.Success<*>)
        val metadata = (result as ParserResult.Success).data
        assertEquals(
            "valid.bin",
            metadata.sections.first { it.title == "General" }.rows.first { it.label == "Filename" }.value,
        )
        assertEquals(
            "2",
            metadata.sections.first { it.title == "Technical" }.rows.first { it.label == "Partition Count" }.value,
        )
        assertTrue("health should be >= 85", metadata.healthScore >= 85)
        assertEquals(HealthLevel.EXCELLENT, metadata.healthLevel)
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertTrue(metadata.healthIndicators.any { it.text == "Valid payload" })
    }

    @Test
    fun analyze_metadataAppearsWithoutComputingSha256() {
        // Hotfix RC: analisis metadata TIDAK boleh menghitung SHA-256
        // (hash payload besar memblokir metadata). Hash dihitung on-demand.
        val bytes = TestPayloadBuilder.buildPayload()
        val file = TestPayloadBuilder.write(tmp.root, "sha.bin", bytes)

        val result = PayloadParser.analyze(file)

        assertTrue(result is ParserResult.Success<*>)
        val metadata = (result as ParserResult.Success).data
        val actual =
            metadata.sections.first { it.title == "General" }
                .rows.first { it.label == "SHA-256" }.value
        assertEquals("Not computed", actual)
    }

    @Test
    fun computeSha256Hex_returnsCorrectHashOnDemand() {
        val bytes = TestPayloadBuilder.buildPayload()
        val file = TestPayloadBuilder.write(tmp.root, "sha.bin", bytes)

        assertEquals(sha256Hex(bytes), PayloadParser.computeSha256Hex(file))
    }

    @Test
    fun missingFile_returnsFileNotFound() {
        val result = PayloadParser.analyze(File(tmp.root, "nope.bin"))
        assertEquals(ParserStatus.FILE_NOT_FOUND, (result as ParserResult.Failure).status)
    }

    @Test
    fun invalidMagic_returnsInvalidHeader() {
        val file = TestPayloadBuilder.write(tmp.root, "badmagic.bin", TestPayloadBuilder.buildPayload(magic = "XXXX"))
        val result = PayloadParser.analyze(file)
        assertEquals(ParserStatus.INVALID_HEADER, (result as ParserResult.Failure).status)
    }

    @Test
    fun corruptManifest_returnsCorruptedMetadata() {
        val bytes = TestPayloadBuilder.buildPayload()
        bytes[12] = 0x7F // manifest size menjadi raksasa (> MAX_MANIFEST_BYTES)
        val file = TestPayloadBuilder.write(tmp.root, "corrupt.bin", bytes)

        val result = PayloadParser.analyze(file)

        assertEquals(ParserStatus.CORRUPTED_METADATA, (result as ParserResult.Failure).status)
    }

    @Test
    fun unsupportedVersion_returnsUnsupportedVersion() {
        val file = TestPayloadBuilder.write(tmp.root, "v3.bin", TestPayloadBuilder.buildPayload(version = 3L))
        val result = PayloadParser.analyze(file)
        assertEquals(ParserStatus.UNSUPPORTED_VERSION, (result as ParserResult.Failure).status)
    }

    @Test
    fun emptyPartitions_returnsUnknownValidation() {
        val file = TestPayloadBuilder.write(tmp.root, "empty.bin", TestPayloadBuilder.buildPayload(partitions = emptyList()))
        val result = PayloadParser.analyze(file)
        val metadata = (result as ParserResult.Success).data
        assertEquals(ValidationState.UNKNOWN, metadata.validationPanel?.status)
    }

    @Test
    fun largePayload_parsesHeaderWithoutReadingWholeFile() {
        val file = TestPayloadBuilder.write(tmp.root, "large.bin", TestPayloadBuilder.buildPayload())
        val size = 64L * 1024L * 1024L
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.setLength(size)
        }

        val result = PayloadParser.analyze(file)

        assertTrue(result is ParserResult.Success<*>)
        assertEquals(size, file.length())
        val metadata = (result as ParserResult.Success).data
        assertEquals(
            "2",
            metadata.sections.first { it.title == "Technical" }.rows.first { it.label == "Partition Count" }.value,
        )
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
