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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SuperParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun engine(): FirmwareAnalysisEngine {
        val registry = FirmwareParserRegistry()
        registry.register(SuperParser())
        return FirmwareAnalysisEngine(registry)
    }

    private fun file(name: String, bytes: ByteArray): File =
        TestSuperImageBuilder.write(tmp.root, name, bytes)

    // ---------------- valid ----------------

    @Test
    fun validSuper_parsesAllMetadata() {
        val result = SuperAnalyzer.analyze(file("super.img", TestSuperImageBuilder.build()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success).data
        assertEquals(10, r.majorVersion)
        assertEquals(2, r.minorVersion)
        assertEquals(4096L, r.blockSize)
        assertEquals(2, r.metadataSlotCount)
        assertTrue(r.virtualAb)
        assertEquals(2, r.groupCount)
        assertEquals(6, r.partitions.size)
        assertEquals(1, r.blockDeviceCount)
        assertTrue(r.headerChecksumValid)
        assertTrue(r.tablesChecksumValid)
        assertTrue(r.partitionTableIntact)

        val system = r.partitions.first { it.name == "system" }
        assertEquals(4L * 1024 * 1024 * 1024, system.size)
        assertEquals("main", system.group)
        assertNull(system.slot)
    }

    @Test
    fun validSuper_engineReturnsReady() {
        val result = engine().analyze(file("super.img", TestSuperImageBuilder.build()))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertEquals(100, metadata.healthScore)
        assertEquals(HealthLevel.EXCELLENT, metadata.healthLevel)
        assertTrue(metadata.sections.any { it.title == "Super" })
        assertTrue(metadata.sections.any { it.title == "Partitions (6)" })
        assertEquals("super", (result as EngineResult.Success).context.parserName)
    }

    @Test
    fun validSuper_slotSuffixDetectedFromName() {
        val specs =
            TestSuperImageBuilder.defaultPartitions.map {
                if (it.name == "vendor_dlkm") it.copy(name = "vendor_dlkm_a") else it
            }
        val result = SuperAnalyzer.analyze(file("super.img", TestSuperImageBuilder.build(partitions = specs)))

        assertTrue(result is ParserResult.Success<*>)
        val partition = (result as ParserResult.Success).data.partitions.first { it.name == "vendor_dlkm_a" }
        assertEquals("a", partition.slot)
    }

    // ---------------- error handling ----------------

    @Test
    fun invalidMagic_returnsInvalidHeader() {
        val bytes = TestSuperImageBuilder.build()
        bytes[0] = 'X'.code.toByte()
        val result = SuperAnalyzer.analyze(file("bad.img", bytes))

        assertEquals(ParserStatus.INVALID_HEADER, (result as ParserResult.Failure).status)
    }

    @Test
    fun sparseMagicOnly_returnsCorruptedMetadata() {
        // Hanya magic sparse (struktur sparse tidak valid) -> jangan crash,
        // kembalikan CORRUPTED_METADATA dengan pesan yang menyebut "Sparse".
        val bytes = ByteArray(4096)
        bytes[0] = 0x3A.toByte()
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0x26.toByte()
        bytes[3] = 0xED.toByte() // android sparse magic
        val result = SuperAnalyzer.analyze(file("fake_sparse.img", bytes))
        assertEquals(ParserStatus.CORRUPTED_METADATA, (result as ParserResult.Failure).status)
        assertTrue((result as ParserResult.Failure).reason.contains("Sparse"))
    }

    @Test
    fun validSparseSuper_isAnalyzed() {
        // Regresi: sparse super (umum di Xiaomi/HyperOS) kini di-unsparse
        // prefix lalu dianalisa — bukan lagi ditolak.
        val raw = TestSuperImageBuilder.build()
        val result = SuperAnalyzer.analyze(file("sparse_super.img", toSparse(raw)))
        assertTrue("should be Success", result is ParserResult.Success)
        val meta = (result as ParserResult.Success).data
        assertTrue("partitions parsed", meta.partitions.isNotEmpty())
        assertEquals(6, meta.partitions.size)
        assertTrue(meta.partitions.any { it.name == "system" })
    }

    @Test
    fun corruptedTables_returnsErrorValidation() {
        val result = engine().analyze(file("corrupt.img", TestSuperImageBuilder.build(corruptTables = true)))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.ERROR, metadata.validationPanel?.status)
        assertTrue(metadata.healthScore <= 30)
    }

    @Test
    fun missingMetadataHeader_returnsCorrupted() {
        val result = SuperAnalyzer.analyze(file("noheader.img", TestSuperImageBuilder.build(headerMagic = 0x12345678L)))

        assertEquals(ParserStatus.CORRUPTED_METADATA, (result as ParserResult.Failure).status)
    }

    @Test
    fun unsupportedVersion_returnsUnsupportedVersion() {
        val result = SuperAnalyzer.analyze(file("v11.img", TestSuperImageBuilder.build(major = 11)))

        assertEquals(ParserStatus.UNSUPPORTED_VERSION, (result as ParserResult.Failure).status)
    }

    @Test
    fun unsupportedMinor_returnsUnsupportedVersion() {
        val result = SuperAnalyzer.analyze(file("minor.img", TestSuperImageBuilder.build(minor = 9)))

        assertEquals(ParserStatus.UNSUPPORTED_VERSION, (result as ParserResult.Failure).status)
    }

    @Test
    fun missingPartitionTable_returnsWarning() {
        val result = engine().analyze(file("empty.img", TestSuperImageBuilder.build(partitions = emptyList())))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.WARNING, metadata.validationPanel?.status)
        assertTrue(metadata.healthScore < 100)
    }

    // ---------------- performance (large image) ----------------

    @Test
    fun largeSuperImage_parsesMetadataWithoutReadingWholeFile() {
        val file = file("large.img", TestSuperImageBuilder.build())
        val size = 11L * 1024L * 1024L * 1024L // > 10 GB
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.setLength(size)
        }

        val result = SuperAnalyzer.analyze(file)

        assertTrue(result is ParserResult.Success<*>)
        assertEquals(size, (result as ParserResult.Success).data.fileSize)
        assertEquals(6, (result as ParserResult.Success).data.partitions.size)
    }

    // ---------------- registry ----------------

    @Test
    fun registry_detectsSuperByMagic() {
        val registry = FirmwareParserRegistry()
        registry.register(SuperParser())

        val file = file("super.bin", TestSuperImageBuilder.build())
        val parser = registry.detect(file, FirmwareAnalysisContext.start(file))

        assertNotNull(parser)
        assertEquals("super", parser?.name)
    }

    @Test
    fun registry_ignoresNonSuperImages() {
        val registry = FirmwareParserRegistry()
        registry.register(SuperParser())

        val file = file("random.bin", ByteArray(64) { 0x41 })
        val parser = registry.detect(file, FirmwareAnalysisContext.start(file))

        assertNull(parser)
    }
}

/**
 * Bungkus raw image menjadi Android sparse image (1 chunk RAW) untuk
 * memverifikasi jalur un-sparse di SuperAnalyzer.
 */
private fun toSparse(raw: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    fun le16(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }
    fun le32(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }
    // block_size harus pangkat dua (aturan format sparse)
    val blockSize = 4096
    val paddedSize = ((raw.size + blockSize - 1) / blockSize) * blockSize
    le32(0xED26FF3A.toInt()) // magic
    le16(1); le16(0) // major/minor
    le16(28); le16(12) // file_hdr_sz / chunk_hdr_sz
    le32(blockSize)
    le32(paddedSize / blockSize) // total_blocks
    le32(1) // total_chunks
    le32(0) // checksum
    le16(0xCAC1); le16(0) // chunk type RAW, reserved
    le32(paddedSize / blockSize) // chunk_blocks
    le32(12 + paddedSize) // chunk_total_size
    out.write(raw)
    repeat(paddedSize - raw.size) { out.write(0) }
    return out.toByteArray()
}