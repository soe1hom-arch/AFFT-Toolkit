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

package com.afft.app.core.workspace

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newEngine(): WorkspaceEngine {
        val root = File(tmp.root, "workspace")
        return WorkspaceEngine(WorkspaceManager(root))
    }

    @Test
    fun createProject_createsFullStructure() {
        val engine = newEngine()
        val result = engine.createProject("Pixel 8 OTA")

        assertTrue(result.isSuccess)
        val project = result.getOrThrow()
        assertTrue(project.metadataFile().exists())
        WorkspaceProject.REQUIRED_DIRS.forEach { dir ->
            assertTrue("missing dir: $dir", File(project.rootDir, dir).isDirectory)
        }
        assertEquals(WorkspaceState.READY, engine.state)
        assertNotNull(engine.currentProject)
    }

    @Test
    fun metadata_persistsAcrossReload() {
        val root = File(tmp.root, "workspace")
        val first = WorkspaceEngine(WorkspaceManager(root))
        val created = first.createProject("Persist Test").getOrThrow()

        // reload dari disk dengan engine baru
        val second = WorkspaceEngine(WorkspaceManager(root))
        val loaded = second.openProject("Persist Test").getOrThrow()

        assertEquals(created.metadata.name, loaded.metadata.name)
        assertEquals(created.metadata.createdAt, loaded.metadata.createdAt)
        assertEquals(created.metadata.workspaceVersion, loaded.metadata.workspaceVersion)
        assertTrue(loaded.metadata.lastOpenedAt >= created.metadata.lastOpenedAt)
    }

    @Test
    fun deleteProject_removesFromDisk() {
        val engine = newEngine()
        engine.createProject("To Delete").getOrThrow()

        val result = engine.deleteProject("To Delete")

        assertTrue(result.isSuccess)
        assertFalse(File(tmp.root, "workspace/To Delete").exists())
        assertTrue(engine.projects().isEmpty())
    }

    @Test
    fun renameProject_updatesNameAndDir() {
        val engine = newEngine()
        engine.createProject("Old Name").getOrThrow()

        val renamed = engine.renameProject("New Name").getOrThrow()

        assertEquals("New Name", renamed.name)
        assertTrue(File(tmp.root, "workspace/New Name").isDirectory)
        assertFalse(File(tmp.root, "workspace/Old Name").exists())
        assertEquals("New Name", engine.currentProject?.name)
    }

    @Test
    fun history_isNewestFirst() {
        val engine = newEngine()
        engine.createProject("Hist").getOrThrow()
        engine.recordOperation("Payload Imported", durationMillis = 10, success = true)
        engine.recordOperation("Payload Extracted", durationMillis = 20, success = true)
        engine.recordOperation("Export Completed", durationMillis = 30, success = true)

        val history = engine.history()
        assertEquals(3, history.size)
        assertEquals("Export Completed", history[0].type)
        assertEquals("Payload Extracted", history[1].type)
        assertEquals("Payload Imported", history[2].type)
        assertEquals("Export Completed", engine.lastOperation()?.type)
    }

    @Test
    fun beginFinishOperation_updatesStateAndRecords() {
        val engine = newEngine()
        engine.createProject("Ops").getOrThrow()

        val handle = engine.beginOperation("Extract Payload")
        assertNotNull(handle)
        assertEquals(WorkspaceState.BUSY, engine.state)

        val op = engine.finishOperation(handle!!, success = true)
        assertNotNull(op)
        assertEquals(OperationResult.SUCCESS, op?.result)
        assertEquals(WorkspaceState.COMPLETED, engine.state)

        val failed = engine.beginOperation("Repack Super")
        engine.finishOperation(failed!!, success = false)
        assertEquals(WorkspaceState.FAILED, engine.state)
        assertEquals(OperationResult.FAILED, engine.lastOperation()?.result)
    }

    @Test
    fun events_areEmittedForLifecycle() {
        val engine = newEngine()
        engine.createProject("Events")
        engine.closeProject()
        engine.openProject("Events")

        val types = engine.events().map { it.type }
        assertTrue(types.contains(WorkspaceEventType.PROJECT_CREATED))
        assertTrue(types.contains(WorkspaceEventType.PROJECT_CLOSED))
        assertTrue(types.contains(WorkspaceEventType.PROJECT_OPENED))
    }

    @Test
    fun recentProjects_sortedByLastOpened() {
        val root = File(tmp.root, "workspace")
        val engine = WorkspaceEngine(WorkspaceManager(root))
        engine.createProject("First").getOrThrow()
        // Jeda cukup besar agar lastOpenedAt berbeda meski granularitas
        // clock coarse (CI runner bisa menyamakan milidetik).
        Thread.sleep(25)
        engine.createProject("Second").getOrThrow()
        engine.closeProject()
        Thread.sleep(25)
        engine.openProject("First").getOrThrow() // First paling baru dibuka

        val recent = engine.recentProjects()
        assertEquals(2, recent.size)
        assertEquals("First", recent[0].name)
        assertEquals("Second", recent[1].name)
    }

    @Test
    fun emptyWorkspace_returnsNoProjects() {
        val engine = newEngine()
        assertTrue(engine.projects().isEmpty())
        assertTrue(engine.snapshot().project == null)
        assertNull(engine.lastOperation())
    }

    @Test
    fun duplicateProjectName_isRejected() {
        val engine = newEngine()
        assertTrue(engine.createProject("Same").isSuccess)
        assertTrue(engine.createProject("Same").isFailure)
        assertTrue(engine.createProject("bad/name").isFailure)
    }

    @Test
    fun updateProject_persistsAndEmitsProjectUpdated() {
        val engine = newEngine()
        engine.createProject("Update Me").getOrThrow()

        val updated =
            engine.updateProject { m -> m.copy(healthScore = 87, androidVersion = "15") }.getOrThrow()

        assertEquals(87, updated.metadata.healthScore)
        assertEquals("15", updated.metadata.androidVersion)
        assertEquals("Update Me", engine.currentProject?.name)
        assertTrue(engine.events().any { it.type == WorkspaceEventType.PROJECT_UPDATED })
    }

    @Test
    fun updateProject_withoutProject_returnsFailure() {
        val engine = newEngine()
        assertTrue(engine.updateProject { it }.isFailure)
    }

    @Test
    fun analysisFailure_emitsAnalysisFailedEvent() {
        val engine = newEngine()
        engine.createProject("Fail Analysis").getOrThrow()

        val handle = engine.beginOperation("Analysis payload")
        engine.finishOperation(handle!!, success = false, detail = "boom")

        val types = engine.events().map { it.type }
        assertTrue(types.contains(WorkspaceEventType.ANALYSIS_STARTED))
        assertTrue(types.contains(WorkspaceEventType.ANALYSIS_FAILED))
    }

    // ---------------- thread safety ----------------

    @Test
    fun concurrentRecordOperations_doNotCorruptHistory() = runBlocking {
        val engine = newEngine()
        engine.createProject("concurrent")

        val jobs =
            (1..16).map { i ->
                launch(Dispatchers.Default) {
                    repeat(10) { j ->
                        engine.recordOperation(
                            type = "op-$i-$j",
                            durationMillis = 1,
                            success = true,
                            detail = "detail $i-$j",
                        )
                    }
                }
            }
        jobs.forEach { it.join() }

        assertEquals(160, engine.history().size)
        // Urutan terbaru-di-depan tetap konsisten tanpa duplikasi/corruption
        val types = engine.history().map { it.type }
        assertEquals(160, types.toSet().size)
    }

    @Test
    fun concurrentReadsDuringWrites_doNotCrash() = runBlocking {
        val engine = newEngine()
        engine.createProject("readwrite")

        val writer =
            launch(Dispatchers.Default) {
                repeat(100) { i ->
                    engine.recordOperation("write-$i", 1, true, "d")
                }
            }
        val readers =
            (1..4).map {
                launch(Dispatchers.Default) {
                    repeat(100) {
                        engine.history()
                        engine.snapshot()
                        engine.events()
                    }
                }
            }
        writer.join()
        readers.forEach { it.join() }
        assertEquals(100, engine.history().size)
    }
}
