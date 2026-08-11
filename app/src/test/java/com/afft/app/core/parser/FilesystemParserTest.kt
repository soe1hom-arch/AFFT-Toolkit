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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilesystemParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun engine(): FirmwareAnalysisEngine {
        val registry = FirmwareParserRegistry()
        registry.register(FilesystemParser())
        return FirmwareAnalysisEngine(registry)
    }

    private fun file(name: String, bytes: ByteArray): File =
        TestFilesystemImageBuilder.write(tmp.root, name, bytes)

    // ---------------- deteksi ----------------

    @Test
    fun detectsErofsExt4F2fsByMagic() {
        assertEquals("EROFS", FilesystemAnalyzer.detectType(file("erofs.img", TestFilesystemImageBuilder.buildErofs())))
        assertEquals("EXT4", FilesystemAnalyzer.detectType(file("ext4.img", TestFilesystemImageBuilder.buildExt4())))
        assertEquals("F2FS", FilesystemAnalyzer.detectType(file("f2fs.img", TestFilesystemImageBuilder.buildF2fs())))
    }

    @Test
    fun registrySelectsFilesystemParser() {
        val registry = FirmwareParserRegistry()
        registry.register(FilesystemParser())
        val f = file("system.img", TestFilesystemImageBuilder.buildErofs())
        val parser = registry.detect(f, FirmwareAnalysisContext.start(f))
        assertNotNull(parser)
        assertEquals("filesystem", parser?.name)
    }

    @Test
    fun factoryCreatesFilesystemParserByDefault() {
        val factory = FirmwareParserFactory
        assertTrue(factory.names().contains("filesystem"))
        val parser = factory.create("filesystem")
        assertNotNull(parser)
        assertEquals("filesystem", parser?.name)
    }

    // ---------------- EROFS ----------------

    @Test
    fun validErofs_parsesAllMetadata() {
        val result = FilesystemAnalyzer.analyze(file("erofs.img", TestFilesystemImageBuilder.buildErofs()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertEquals("EROFS", r.fsType)
        assertTrue(r.known)
        assertEquals(4096L, r.blockSize)
        assertEquals("LZ4", r.compression)
        assertEquals(true, r.readOnly)
        assertEquals(262_144L, r.blockCount)
        assertTrue(r.features.contains("ztailpacking"))
        assertNotNull(r.uuid)
        assertEquals("erofs_test", r.volumeName)
    }

    @Test
    fun validErofs_engineReturnsReady() {
        val result = engine().analyze(file("erofs.img", TestFilesystemImageBuilder.buildErofs()))

        assertTrue(result is EngineResult.Success)
        val success = result as EngineResult.Success
        val metadata = success.metadata
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertEquals(100, metadata.healthScore)
        assertEquals(HealthLevel.EXCELLENT, metadata.healthLevel)
        assertEquals("filesystem", success.context.parserName)
        assertTrue(metadata.sections.any { it.title == "Filesystem" })
        assertTrue(metadata.sections.any { it.title == "EROFS" })
    }

    // ---------------- EXT4 ----------------

    @Test
    fun validExt4_parsesAllMetadata() {
        val result = FilesystemAnalyzer.analyze(file("ext4.img", TestFilesystemImageBuilder.buildExt4()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertEquals("EXT4", r.fsType)
        assertTrue(r.known)
        assertEquals(4096L, r.blockSize)
        assertEquals(262_144L, r.blockCount)
        assertEquals(262_144L, r.inodeCount)
        assertEquals(true, r.journalSupport)
        assertTrue(r.features.contains("has_journal"))
        assertTrue(r.features.contains("64bit"))
        assertEquals("Clean", r.state)
        assertEquals("ext4_test", r.volumeName)
    }

    @Test
    fun validExt4_engineReturnsReady() {
        val result = engine().analyze(file("ext4.img", TestFilesystemImageBuilder.buildExt4()))

        assertTrue(result is EngineResult.Success)
        val success = result as EngineResult.Success
        val metadata = success.metadata
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertEquals(100, metadata.healthScore)
        assertTrue(metadata.sections.any { it.title == "EXT4" })
    }

    @Test
    fun ext4WithErrors_stateRaisesWarning() {
        val result = engine().analyze(file("dirty.img", TestFilesystemImageBuilder.buildExt4(state = 2)))

        assertTrue(result is EngineResult.Success)
        val metadata = (result as EngineResult.Success).metadata
        assertEquals(ValidationState.WARNING, metadata.validationPanel?.status)
        assertTrue(metadata.validationPanel?.reason?.contains("warnings") == true)
    }

    // ---------------- F2FS ----------------

    @Test
    fun validF2fs_parsesAllMetadata() {
        val result = FilesystemAnalyzer.analyze(file("f2fs.img", TestFilesystemImageBuilder.buildF2fs()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertEquals("F2FS", r.fsType)
        assertTrue(r.known)
        assertEquals("1.16", r.version)
        assertEquals(4096L, r.blockSize)
        assertEquals(2_000_000L, r.blockCount)
        assertEquals(1024L, r.segmentCount)
        assertEquals("1001", r.checkpointVersion)
        assertEquals("6.1.25-android14-2", r.kernelVersion)
        assertTrue(r.features.contains("encrypt"))
        assertTrue(r.features.contains("sb_checksum"))
        assertEquals("f2fs_test", r.volumeName)
    }

    @Test
    fun validF2fs_engineReturnsReady() {
        val result = engine().analyze(file("f2fs.img", TestFilesystemImageBuilder.buildF2fs()))

        assertTrue(result is EngineResult.Success)
        val success = result as EngineResult.Success
        val metadata = success.metadata
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
        assertEquals(100, metadata.healthScore)
        assertEquals(HealthLevel.EXCELLENT, metadata.healthLevel)
        assertTrue(metadata.sections.any { it.title == "F2FS" })
    }

    // ---------------- error / tak dikenal ----------------

    @Test
    fun corruptedSuperblock_analyzerReturnsUnknownWithoutCrash() {
        val result = FilesystemAnalyzer.analyze(file("corrupt.img", TestFilesystemImageBuilder.buildCorrupted()))

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertEquals("Unknown", r.fsType)
        assertFalse(r.known)
    }

    @Test
    fun corruptedSuperblock_canParseIsFalse() {
        val f = file("corrupt.img", TestFilesystemImageBuilder.buildCorrupted())
        assertNull(FilesystemAnalyzer.detectType(f))
        assertFalse(FilesystemParser().canParse(f, FirmwareAnalysisContext.start(f)))
    }

    @Test
    fun corruptedSuperblock_parserDirectlyReturnsUnknownValidation() {
        val parser = FilesystemParser()
        val f = file("random.img", TestFilesystemImageBuilder.buildCorrupted())
        val metadata = parser.analyze(f, FirmwareAnalysisContext.start(f))

        assertEquals(ValidationState.UNKNOWN, metadata.validationPanel?.status)
        assertEquals(50, metadata.healthScore)
    }

    @Test
    fun corruptedSuperblock_engineReturnsUnsupportedFormat() {
        val result = engine().analyze(file("corrupt.img", TestFilesystemImageBuilder.buildCorrupted()))

        assertTrue(result is EngineResult.Failure)
        assertEquals(
            FirmwareAnalysisError.UNSUPPORTED_FORMAT,
            (result as EngineResult.Failure).error.error,
        )
    }

    @Test
    fun tooSmallFile_detectsNothing() {
        val f = file("tiny.img", TestFilesystemImageBuilder.buildTooSmall())
        assertNull(FilesystemAnalyzer.detectType(f))
        assertTrue(FilesystemAnalyzer.analyze(f) is ParserResult.Success<*>)
        val r = ((FilesystemAnalyzer.analyze(f)) as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertFalse(r.known)
    }

    // ---------------- image besar ----------------

    @Test
    fun largeImage_readsOnlySuperblock() {
        val size = 12L * 1024 * 1024 * 1024
        val f = TestFilesystemImageBuilder.writeSparse(
            tmp.root,
            "huge.img",
            size,
            TestFilesystemImageBuilder.buildErofs(),
        )
        val start = System.nanoTime()
        val result = FilesystemAnalyzer.analyze(f)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(result is ParserResult.Success<*>)
        val r = (result as ParserResult.Success<*>).data as FilesystemAnalysisResult
        assertEquals("EROFS", r.fsType)
        assertEquals(size, r.fileSize)
        assertTrue("analyzer must not read whole 12 GB image", elapsedMs < 5000)
    }
}
