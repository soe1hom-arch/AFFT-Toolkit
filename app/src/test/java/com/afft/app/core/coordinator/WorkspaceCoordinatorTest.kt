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

package com.afft.app.core.coordinator

import com.afft.app.core.parser.FirmwareAnalysisEngine
import com.afft.app.core.parser.FirmwareParserFactory
import com.afft.app.core.parser.FirmwareParserRegistry
import com.afft.app.core.parser.TestBootImageBuilder
import com.afft.app.core.parser.TestPayloadBuilder
import com.afft.app.core.parser.TestFilesystemImageBuilder
import com.afft.app.core.parser.TestSuperImageBuilder
import com.afft.app.core.workspace.OperationResult
import com.afft.app.core.workspace.WorkspaceEngine
import com.afft.app.core.workspace.WorkspaceEventType
import com.afft.app.core.workspace.WorkspaceManager
import com.afft.app.core.workspace.WorkspaceState
import com.afft.app.ui.components.dashboard.ValidationState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceCoordinatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newCoordinator(root: File = File(tmp.root, "workspace")): WorkspaceCoordinator {
        val manager = WorkspaceManager(root)
        val engine = WorkspaceEngine(manager)
        val registry = FirmwareParserRegistry()
        FirmwareParserFactory.createDefault().forEach(registry::register)
        return WorkspaceCoordinator(engine, FirmwareAnalysisEngine(registry))
    }

    private fun validPayload(name: String): File =
        TestPayloadBuilder.write(tmp.root, name, TestPayloadBuilder.buildPayload())

    private fun invalidPayload(name: String): File =
        TestPayloadBuilder.write(tmp.root, name, TestPayloadBuilder.buildPayload(magic = "XXXX"))

    // ---------------- sync flow ----------------

    @Test
    fun analyze_createsProjectAndSyncsMetadataAndHealth() = runBlocking {
        val coordinator = newCoordinator()
        val file = validPayload("payload.bin")

        val metadata = coordinator.analyze(file)

        assertNotNull(coordinator.state.value.project)
        val project = coordinator.state.value.project!!
        assertEquals("payload", project.name)
        assertEquals(WorkspaceState.COMPLETED, coordinator.state.value.state)
        assertEquals("Payload", project.metadata.firmwareType)
        assertEquals(metadata.healthScore, project.metadata.healthScore)
        assertNull(project.metadata.androidVersion) // payload tanpa metadata android
        assertTrue(coordinator.state.value.history.isNotEmpty())
        assertEquals("Analysis", coordinator.state.value.lastOperation?.type)
        assertEquals("Analysis", coordinator.engine.lastOperation()?.type)
    }

    @Test
    fun repeatedSelection_analyzesOnlyOnce() = runBlocking {
        val coordinator = newCoordinator()
        val file = validPayload("once.bin")

        val first = coordinator.analyze(file)
        val second = coordinator.analyze(file)

        assertSame(first, second) // cache: file sama -> metadata sama (tanpa analisis ulang)
        assertEquals(1, coordinator.engine.history().count { it.type == "Analysis" })
    }

    @Test
    fun differentFile_opensDifferentProject() = runBlocking {
        val coordinator = newCoordinator()
        val fileA = validPayload("ota_a.bin")
        val fileB = validPayload("ota_b.bin")

        coordinator.analyze(fileA)
        coordinator.analyze(fileB)

        val projects = coordinator.engine.projects().map { it.name }.toSet()
        assertEquals(setOf("ota_a", "ota_b"), projects)
        assertEquals("ota_b", coordinator.state.value.project?.name)
    }

    @Test
    fun failureFlow_setsFailedStateAndErrorMetadata() = runBlocking {
        val coordinator = newCoordinator()
        val file = invalidPayload("broken.bin")

        val metadata = coordinator.analyze(file)

        assertEquals(WorkspaceState.FAILED, coordinator.state.value.state)
        assertEquals(WorkspaceState.FAILED, coordinator.engine.state)
        assertEquals(ValidationState.ERROR, metadata.validationPanel?.status)
        assertNotNull(metadata.validationPanel?.reason)
        assertEquals(0, coordinator.state.value.healthScore)
        assertEquals(OperationResult.FAILED, coordinator.state.value.lastOperation?.result)
        assertTrue(coordinator.engine.events().any { it.type == WorkspaceEventType.ANALYSIS_FAILED })
    }

    @Test
    fun successFlow_emitsAnalysisEventsAndProjectUpdated() = runBlocking {
        val coordinator = newCoordinator()
        val file = validPayload("events.bin")

        coordinator.analyze(file)

        val types = coordinator.engine.events().map { it.type }
        assertTrue(types.contains(WorkspaceEventType.PROJECT_CREATED))
        assertTrue(types.contains(WorkspaceEventType.ANALYSIS_STARTED))
        assertTrue(types.contains(WorkspaceEventType.ANALYSIS_COMPLETED))
        assertTrue(types.contains(WorkspaceEventType.PROJECT_UPDATED))
    }

    @Test
    fun openExistingProject_resumesWorkspace() = runBlocking {
        val root = File(tmp.root, "workspace")
        val first = newCoordinator(root)
        val file = validPayload("resume.bin")
        first.analyze(file)

        // simulasi restart app: coordinator baru di root workspace yang sama
        val second = newCoordinator(root)
        val resumed = second.analyze(file)

        assertNotNull(resumed)
        assertEquals("resume", second.state.value.project?.name)
        assertEquals(1, second.engine.history().count { it.type == "Analysis" })
        assertEquals(1, second.engine.projects().size) // tidak membuat proyek ganda
    }

    @Test
    fun bootImage_analyzesThroughWorkspace() = runBlocking {
        val coordinator = newCoordinator()
        val file = TestBootImageBuilder.write(tmp.root, "boot.img", TestBootImageBuilder.buildV0())

        val metadata = coordinator.analyze(file)

        assertEquals("boot", coordinator.state.value.project?.name)
        assertEquals("Boot Image", coordinator.state.value.project?.metadata?.firmwareType)
        assertEquals(WorkspaceState.COMPLETED, coordinator.state.value.state)
        assertEquals("Boot Analysis", coordinator.state.value.lastOperation?.type)
        assertEquals("Boot Analysis", coordinator.engine.history().first().type)
        assertEquals(metadata.healthScore, coordinator.state.value.project?.metadata?.healthScore)
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
    }

    @Test
    fun filesystemImage_analyzesThroughWorkspace() = runBlocking {
        val coordinator = newCoordinator()
        val file = TestFilesystemImageBuilder.write(tmp.root, "system.img", TestFilesystemImageBuilder.buildErofs())

        val metadata = coordinator.analyze(file)

        assertEquals("system", coordinator.state.value.project?.name)
        assertEquals("Filesystem", coordinator.state.value.project?.metadata?.firmwareType)
        assertEquals(WorkspaceState.COMPLETED, coordinator.state.value.state)
        assertEquals("Filesystem Analysis", coordinator.state.value.lastOperation?.type)
        assertEquals("Filesystem Analysis", coordinator.engine.history().first().type)
        assertEquals(metadata.healthScore, coordinator.state.value.project?.metadata?.healthScore)
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
    }

    @Test
    fun superImage_analyzesThroughWorkspace() = runBlocking {
        val coordinator = newCoordinator()
        val file = TestSuperImageBuilder.write(tmp.root, "super.img", TestSuperImageBuilder.build())

        val metadata = coordinator.analyze(file)

        assertEquals("super", coordinator.state.value.project?.name)
        assertEquals("Super", coordinator.state.value.project?.metadata?.firmwareType)
        assertEquals(WorkspaceState.COMPLETED, coordinator.state.value.state)
        assertEquals("Super Analysis", coordinator.state.value.lastOperation?.type)
        assertEquals("Super Analysis", coordinator.engine.history().first().type)
        assertEquals(metadata.healthScore, coordinator.state.value.project?.metadata?.healthScore)
        assertEquals(ValidationState.READY, metadata.validationPanel?.status)
    }
}
