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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BootParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun engine(): FirmwareAnalysisEngine {
        val registry = FirmwareParserRegistry()
        registry.register(BootParser())
        return FirmwareAnalysisEngine(registry)
    }

    private fun file(name: String, bytes: ByteArray): File =
        TestBootImageBuilder.write(tmp.root, name, bytes)

    // ---------------- valid ----------------

    @Test
    fun validBoot_v0_parsesAllMetadata() {
        val result = BootAnalyzer.analyze(file("boot.img", TestBootImageBuilder.buildV0()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success).data
        assertEquals(0, r.headerVersion)
        assertEquals(512L, r.kernelSize)
        assertEquals(64L, r.ramdiskSize)
        assertTrue(r.ramdiskPresent)
        assertEquals("15.0", r.osVersion)
        assertEquals("2026-07", r.securityPatchLevel)
        assertEquals(2048L, r.pageSize)
        assertEquals("AArch64 (arm64)", r.architecture)
        assertEquals("cpio (uncompressed)", r.ramdiskCompression)
        assertTrue(r.cmdline.contains("androidboot.hardware=qcom"))
    }

    @Test
    fun validBoot_v0_engineReturnsReadyMetadata() {
        val result = engine().analyze(file("boot.img", TestBootImageBuilder.buildV0()))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertEquals(100, metadata.healthScore)
        assertEquals(HealthLevel.EXCELLENT, metadata.healthLevel)
        assertTrue(metadata.sections.any { it.title == "Boot" })
        assertTrue(metadata.sections.any { it.title == "Ramdisk" })
        assertTrue(metadata.sections.any { it.title == "Security" })
        assertEquals("boot", (result as EngineResult.Success).context.parserName)
    }

    @Test
    fun validBoot_v3_parsesCompactHeader() {
        val result = BootAnalyzer.analyze(file("boot_v3.img", TestBootImageBuilder.buildV3()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success).data
        assertEquals(3, r.headerVersion)
        assertEquals("14.2", r.osVersion)
        assertEquals("2026-03", r.securityPatchLevel)
        assertNull(r.pageSize)
        assertTrue(r.cmdline.contains("androidboot.hardware=exynos"))
        assertTrue(r.ramdiskPresent)
    }

    // ---------------- error handling ----------------

    @Test
    fun invalidMagic_returnsInvalidHeader() {
        val bytes = TestBootImageBuilder.buildV0(magic = "XXXXXXX!")
        val result = BootAnalyzer.analyze(file("bad.img", bytes))

        assertEquals(ParserStatus.INVALID_HEADER, (result as ParserResult.Failure).status)
    }

    @Test
    fun invalidMagic_engineReturnsFailure() {
        val result = engine().analyze(file("bad.img", TestBootImageBuilder.buildV0(magic = "XXXXXXX!")))

        assertTrue(result is EngineResult.Failure)
        assertEquals(FirmwareAnalysisError.UNSUPPORTED_FORMAT, (result as EngineResult.Failure).error.error)
    }

    @Test
    fun unsupportedHeaderVersion_returnsUnsupportedVersion() {
        val bytes = TestBootImageBuilder.buildV0(headerVersion = 9)
        val result = BootAnalyzer.analyze(file("v9.img", bytes))

        assertEquals(ParserStatus.UNSUPPORTED_VERSION, (result as ParserResult.Failure).status)
    }

    @Test
    fun missingRamdisk_returnsWarningAndLowerHealth() {
        val result = engine().analyze(file("noramdisk.img", TestBootImageBuilder.buildV0(ramdiskSize = 0)))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.WARNING, metadata.validationPanel?.status)
        assertEquals(90, metadata.healthScore)
        assertTrue(metadata.healthIndicators.any { it.text.contains("Ramdisk", ignoreCase = true) })
    }

    @Test
    fun strippedHeader_returnsUnknownValidation() {
        val bytes = TestBootImageBuilder.buildV3(osVersionRaw = 0L, cmdline = "")
        val result = engine().analyze(file("stripped.img", bytes))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.UNKNOWN, metadata.validationPanel?.status)
        assertEquals(50, metadata.healthScore)
    }

    @Test
    fun missingFile_returnsFileNotFound() {
        val result = BootAnalyzer.analyze(File(tmp.root, "absent.img"))
        assertEquals(ParserStatus.FILE_NOT_FOUND, (result as ParserResult.Failure).status)
    }

    @Test
    fun truncatedHeader_returnsCorruptedMetadata() {
        // Hotfix RC: EOF saat baca header = file terpotong -> CORRUPTED_METADATA
        // (sebelumnya jatuh ke READ_ERROR/ParserFailure generik).
        val bytes = TestBootImageBuilder.buildV0().copyOfRange(0, 24)
        val result = BootAnalyzer.analyze(file("trunc.img", bytes))

        assertEquals(ParserStatus.CORRUPTED_METADATA, (result as ParserResult.Failure).status)
    }

    @Test
    fun corruptedBounds_returnsCorruptedMetadata() {
        // ramdisk_size besar -> section di luar batas file
        val bytes = TestBootImageBuilder.buildV0(ramdiskSize = 4096)
        val file = file("bounds.img", bytes)
        RandomAccessFile(file, "rw").use { raf -> raf.setLength(2000) }

        val result = BootAnalyzer.analyze(file)

        assertEquals(ParserStatus.CORRUPTED_METADATA, (result as ParserResult.Failure).status)
    }

    // ---------------- performance (large image) ----------------

    @Test
    fun largeBootImage_parsesHeaderWithoutReadingWholeFile() {
        val file = file("large.img", TestBootImageBuilder.buildV0())
        val size = 300L * 1024L * 1024L // > 256 MB
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length())
            raf.setLength(size)
        }

        val result = BootAnalyzer.analyze(file)

        assertTrue(result is ParserResult.Success<*>)
        assertEquals(size, (result as ParserResult.Success).data.fileSize)
        assertEquals(0, (result as ParserResult.Success).data.headerVersion)
    }

    // ---------------- registry ----------------

    @Test
    fun registry_detectsBootByMagic() {
        val registry = FirmwareParserRegistry()
        registry.register(BootParser())

        val file = file("boot.bin", TestBootImageBuilder.buildV0()) // tanpa ekstensi .img
        val parser = registry.detect(file, FirmwareAnalysisContext.start(file))

        assertNotNull(parser)
        assertEquals("boot", parser?.name)
    }

    @Test
    fun registry_ignoresNonBootImages() {
        val registry = FirmwareParserRegistry()
        registry.register(BootParser())

        val file = file("random.bin", ByteArray(64) { 0x41 }) // bukan "ANDROID!"
        val parser = registry.detect(file, FirmwareAnalysisContext.start(file))

        assertNull(parser)
    }
}
