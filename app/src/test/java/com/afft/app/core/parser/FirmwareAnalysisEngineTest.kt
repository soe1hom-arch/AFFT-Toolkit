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

import com.afft.app.ui.components.dashboard.FirmwareMetadata
import com.afft.app.ui.components.dashboard.HealthLevel
import com.afft.app.ui.components.dashboard.ValidationState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirmwareAnalysisEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeParser(
        override val name: String = "fake",
        override val version: String = "1.0.0",
        private val extensions: Set<String> = setOf("fake"),
        private val canParseResult: Boolean = true,
        private val analysisError: Throwable? = null,
        private val validation: FirmwareValidation =
            FirmwareValidation.ready("Supported format", "Valid firmware", "Proceed"),
    ) : FirmwareParser {
        override fun supportedExtensions(): Set<String> = extensions
        override fun supportedMimeTypes(): Set<String> = setOf("application/x-fake")
        override fun canParse(file: File, context: FirmwareAnalysisContext): Boolean = canParseResult
        override fun analyze(file: File, context: FirmwareAnalysisContext): FirmwareMetadata {
            analysisError?.let { throw it }
            return FirmwareMetadata(headline = "Fake Inspector")
        }
        override fun validate(metadata: FirmwareMetadata): FirmwareValidation = validation
    }

    private fun file(name: String): File {
        val f = File(tmp.root, name)
        f.writeBytes(byteArrayOf(1, 2, 3, 4))
        return f
    }

    // ---------------- registry ----------------

    @Test
    fun registry_registerFindRemove() {
        val registry = FirmwareParserRegistry()
        assertTrue(registry.register(FakeParser()))
        assertNotNull(registry.find("fake"))
        assertEquals(1, registry.size())
        assertTrue(!registry.register(FakeParser(name = "fake"))) // duplikat ditolak
        assertTrue(registry.remove("fake"))
        assertNull(registry.find("fake"))
    }

    @Test
    fun registry_detectByExtension() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser())
        val parser = registry.detect(file("firmware.fake"), FirmwareAnalysisContext.start(file("firmware.fake")))
        assertEquals("fake", parser?.name)
    }

    @Test
    fun registry_detectFallsBackToCanParse() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser(extensions = setOf("fake"), canParseResult = true))
        val parser = registry.detect(file("unknown.bin"), FirmwareAnalysisContext.start(file("unknown.bin")))
        assertEquals("fake", parser?.name)
    }

    // ---------------- factory ----------------

    @Test
    fun factory_createsRegisteredParser() {
        FirmwareParserFactory.register("fake") { FakeParser() }
        try {
            assertTrue("fake" in FirmwareParserFactory.names())
            assertNotNull(FirmwareParserFactory.create("fake"))
            assertNull(FirmwareParserFactory.create("nope"))
        } finally {
            FirmwareParserFactory.clear()
        }
    }

    // ---------------- validation ----------------

    @Test
    fun validation_factoriesProduceAllStatuses() {
        assertEquals(FirmwareValidationStatus.READY, FirmwareValidation.ready("r", "d", "x").status)
        assertEquals(FirmwareValidationStatus.WARNING, FirmwareValidation.warning("r", "d", "x").status)
        assertEquals(FirmwareValidationStatus.ERROR, FirmwareValidation.error("r", "d", "x").status)
        assertEquals(FirmwareValidationStatus.UNKNOWN, FirmwareValidation.unknown("r", "d", "x").status)
    }

    // ---------------- health calculator ----------------

    @Test
    fun healthCalculator_readyScores100() {
        val result = FirmwareHealthCalculator.calculate(FirmwareValidation.ready("ok", "d", "x"))
        assertEquals(100, result.score)
        assertEquals(FirmwareHealth.EXCELLENT, result.health)
    }

    @Test
    fun healthCalculator_issuesReduceScore() {
        val validation =
            FirmwareValidation.warning(
                reason = "minor",
                description = "d",
                recommendation = "x",
                issues = listOf(FirmwareIssue("Missing metadata", IssueSeverity.CRITICAL)),
            )
        val result = FirmwareHealthCalculator.calculate(validation)
        assertEquals(75, result.score)
        assertEquals(FirmwareHealth.GOOD, result.health)
    }

    @Test
    fun healthCalculator_errorCappedAt30() {
        val result = FirmwareHealthCalculator.calculate(FirmwareValidation.error("bad", "d", "x"))
        assertEquals(30, result.score)
        assertEquals(FirmwareHealth.CRITICAL, result.health)
    }

    // ---------------- engine ----------------

    @Test
    fun engine_successMergesValidationAndHealth() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser())
        val engine = FirmwareAnalysisEngine(registry)

        val result = engine.analyze(file("fw.fake"))

        assertTrue(result is EngineResult.Success)
        val success = result as EngineResult.Success
        assertEquals(100, success.metadata.healthScore)
        assertEquals(HealthLevel.EXCELLENT, success.metadata.healthLevel)
        assertEquals(ValidationState.READY, success.metadata.validationPanel?.status)
        assertEquals("fake", success.context.parserName)
        assertTrue(success.context.elapsedMillis >= 0L)
    }

    @Test
    fun engine_unsupportedFormatReturnsFailure() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser(extensions = setOf("fake"), canParseResult = false))
        val engine = FirmwareAnalysisEngine(registry)

        val result = engine.analyze(file("fw.bin"))

        assertTrue(result is EngineResult.Failure)
        val failure = result as EngineResult.Failure
        assertEquals(FirmwareAnalysisError.UNSUPPORTED_FORMAT, failure.error.error)
    }

    @Test
    fun engine_mapsParserExceptionToParserFailure() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser(analysisError = IllegalStateException("boom")))
        val engine = FirmwareAnalysisEngine(registry)

        val result = engine.analyze(file("fw.fake"))

        assertTrue(result is EngineResult.Failure)
        val failure = result as EngineResult.Failure
        assertEquals(FirmwareAnalysisError.PARSER_FAILURE, failure.error.error)
        assertTrue(failure.error.message.orEmpty().contains("boom"))
    }

    @Test
    fun engine_missingFileReturnsMissingMetadata() {
        val registry = FirmwareParserRegistry()
        registry.register(FakeParser())
        val engine = FirmwareAnalysisEngine(registry)

        val result = engine.analyze(File(tmp.root, "absent.fake"))

        assertTrue(result is EngineResult.Failure)
        assertEquals(FirmwareAnalysisError.MISSING_METADATA, (result as EngineResult.Failure).error.error)
    }
}
