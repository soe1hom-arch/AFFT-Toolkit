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

import com.afft.app.ui.components.dashboard.StatusType

/** Token operasi yang sedang berjalan. */
data class OperationHandle(
    val type: String,
    val startedAt: Long,
)

/** Snapshot kondisi workspace untuk konsumsi UI (mis. Home). */
data class WorkspaceSnapshot(
    val project: WorkspaceProject?,
    val state: WorkspaceState,
    val lastOperation: WorkspaceOperation?,
)

/**
 * WorkspaceEngine — facade utama seluruh operasi workspace.
 *
 * LIFECYCLE:
 *   create/open ──► READY ──begin──► BUSY ──finish(sukses)──► COMPLETED
 *                                                    └──finish(gagal)──► FAILED
 *   close ──► IDLE
 *
 * Modul masa depan (Payload, Boot, Super, Filesystem, Recovery, Kernel,
 * ROM Builder, APK, Plugin) berinteraksi cukup lewat:
 *   1. openProject / createProject
 *   2. beginOperation(type) / finishOperation(handle, success, detail)
 *   3. history() / snapshot() untuk UI
 * Engine tidak peduli jenis modul — hanya mencatat & menyebarkan event.
 */
class WorkspaceEngine(
    private val manager: WorkspaceManager,
    private val repository: WorkspaceRepository = WorkspaceRepository(),
) {

    var currentProject: WorkspaceProject? = null
        private set

    var state: WorkspaceState = WorkspaceState.IDLE
        private set

    private val history = WorkspaceHistory()
    private val eventLog = ArrayDeque<WorkspaceEvent>()
    private val listeners = mutableListOf<(WorkspaceEvent) -> Unit>()

    /**
     * Kunci tunggal untuk semua akses mutable (state, proyek, history, events).
     * WorkspaceCoordinator memanggil engine dari Dispatchers.IO sementara UI
     * membaca dari main thread — lock ini mencegah korupsi tulis bersamaan.
     */
    private val lock = Any()

    // ---------- lifecycle proyek ----------

    fun createProject(name: String): Result<WorkspaceProject> =
        synchronized(lock) {
            manager.createProject(name).onSuccess { project ->
                currentProject = project
                state = WorkspaceState.READY
                loadHistoryFromDisk()
                emit(WorkspaceEventType.PROJECT_CREATED, project.name)
            }
        }

    fun openProject(name: String): Result<WorkspaceProject> =
        synchronized(lock) {
            manager.openProject(name).onSuccess { project ->
                currentProject = project
                state = WorkspaceState.READY
                loadHistoryFromDisk()
                emit(WorkspaceEventType.PROJECT_OPENED, project.name)
            }
        }

    fun closeProject() {
        synchronized(lock) {
            currentProject?.let { emit(WorkspaceEventType.PROJECT_CLOSED, it.name) }
            currentProject = null
            state = WorkspaceState.IDLE
        }
    }

    fun deleteProject(name: String): Result<Unit> {
        synchronized(lock) {
            if (currentProject?.name == name) closeProject()
        }
        return manager.deleteProject(name)
    }

    fun renameProject(newName: String): Result<WorkspaceProject> =
        synchronized(lock) {
            val project = currentProject ?: return@synchronized Result.failure(IllegalStateException("No active project"))
            manager.renameProject(project, newName).onSuccess { renamed ->
                currentProject = renamed
                emit(WorkspaceEventType.PROJECT_OPENED, renamed.name, "renamed to ${renamed.name}")
            }
        }

    /**
     * Memperbarui metadata proyek aktif, lalu mempersist ke disk dan
     * memancarkan event PROJECT_UPDATED.
     */
    fun updateProject(update: (WorkspaceMetadata) -> WorkspaceMetadata): Result<WorkspaceProject> =
        synchronized(lock) {
            val project = currentProject ?: return@synchronized Result.failure(IllegalStateException("No active project"))
            val updated = update(project.metadata)
            repository.saveProject(project.rootDir, updated).mapCatching {
                val refreshed = WorkspaceProject(project.rootDir, updated)
                currentProject = refreshed
                emit(WorkspaceEventType.PROJECT_UPDATED, refreshed.name)
                refreshed
            }
        }

    // ---------- operasi ----------

    fun beginOperation(type: String): OperationHandle? =
        synchronized(lock) {
            if (currentProject == null) return@synchronized null
            state = WorkspaceState.BUSY
            if (type.contains("analysis", ignoreCase = true)) {
                emit(WorkspaceEventType.ANALYSIS_STARTED, projectName = currentProject?.name, detail = type)
            }
            if (type.contains("extract", ignoreCase = true)) {
                emit(WorkspaceEventType.EXTRACTION_STARTED, projectName = currentProject?.name, detail = type)
            }
            OperationHandle(type, System.currentTimeMillis())
        }

    fun finishOperation(handle: OperationHandle, success: Boolean, detail: String = ""): WorkspaceOperation? =
        synchronized(lock) {
            if (currentProject == null) return@synchronized null
            val operation =
                WorkspaceOperation(
                    type = handle.type,
                    timestamp = handle.startedAt,
                    durationMillis = (System.currentTimeMillis() - handle.startedAt).coerceAtLeast(0L),
                    result = if (success) OperationResult.SUCCESS else OperationResult.FAILED,
                    detail = detail,
                )
            history.add(operation)
            persistHistory()
            state = if (success) WorkspaceState.COMPLETED else WorkspaceState.FAILED
            emitFinishEvent(handle.type, success, operation)
            operation
        }

    /** Mencatat operasi selesai tanpa tracking waktu (single-shot). */
    fun recordOperation(type: String, durationMillis: Long, success: Boolean, detail: String = "") {
        synchronized(lock) {
            val operation =
                WorkspaceOperation(
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    durationMillis = durationMillis,
                    result = if (success) OperationResult.SUCCESS else OperationResult.FAILED,
                    detail = detail,
                )
            history.add(operation)
            persistHistory()
            state = if (success) WorkspaceState.COMPLETED else WorkspaceState.FAILED
            emitFinishEvent(type, success, operation)
        }
    }

    /**
     * Menghapus seluruh riwayat operasi proyek aktif (memori + disk).
     * Dipanggil dari UI saat user ingin mereset riwayat.
     */
    fun clearHistory(): Result<Unit> =
        synchronized(lock) {
            history.clear()
            val file = historyFile()
            runCatching {
                file?.delete()
            }.map { Unit }
        }

    /** Memperbarui titik lanjut (tool/file/langkah terakhir) di metadata proyek. */
    fun updateResumePoint(
        tool: String,
        file: String?,
        step: String,
    ): Result<WorkspaceProject> =
        updateProject { metadata ->
            metadata.copy(
                lastTool = tool,
                lastFile = file,
                lastStep = step,
                lastOpenedAt = System.currentTimeMillis(),
            )
        }

    // ---------- akses data ----------

    fun history(): List<WorkspaceOperation> = synchronized(lock) { history.all() }

    fun lastOperation(): WorkspaceOperation? = synchronized(lock) { history.latest() }

    fun events(): List<WorkspaceEvent> = synchronized(lock) { eventLog.toList() }

    fun snapshot(): WorkspaceSnapshot =
        synchronized(lock) { WorkspaceSnapshot(currentProject, state, history.latest()) }

    fun projects(): List<WorkspaceProject> = manager.listProjects()

    fun recentProjects(limit: Int = 5): List<WorkspaceProject> = manager.recentProjects(limit)

    fun addListener(listener: (WorkspaceEvent) -> Unit) {
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    // ---------- persistensi riwayat ----------

    private fun historyFile(): File? =
        currentProject?.rootDir?.let { File(it, WorkspaceHistoryStore.FILE_NAME) }

    private fun persistHistory() {
        val file = historyFile() ?: return
        WorkspaceHistoryStore.save(file.parentFile, history.all())
    }

    private fun loadHistoryFromDisk() {
        val root = currentProject?.rootDir ?: return
        history.clear()
        // Store menyimpan terbaru di depan; tambahkan dari paling lama agar
        // urutan (terbaru di depan) tetap benar setelah dimuat.
        WorkspaceHistoryStore.load(root).reversed().forEach { history.add(it) }
    }

    // ---------- internal ----------

    private fun emitFinishEvent(type: String, success: Boolean, operation: WorkspaceOperation) {
        val event =
            when {
                type.contains("export", ignoreCase = true) -> WorkspaceEventType.EXPORT_FINISHED
                type.contains("extract", ignoreCase = true) -> WorkspaceEventType.EXTRACTION_FINISHED
                type.contains("analysis", ignoreCase = true) && !success ->
                    WorkspaceEventType.ANALYSIS_FAILED
                type.contains("analysis", ignoreCase = true) -> WorkspaceEventType.ANALYSIS_COMPLETED
                else -> null
            } ?: return
        emit(event, projectName = currentProject?.name, detail = if (success) "OK" else "FAILED")
    }

    private fun emit(type: WorkspaceEventType, projectName: String? = null, detail: String? = null) {
        synchronized(lock) {
            val event = WorkspaceEvent(type, System.currentTimeMillis(), projectName, detail)
            eventLog.addFirst(event)
            while (eventLog.size > MAX_EVENTS) {
                eventLog.removeLast()
            }
            listeners.toList().forEach { listener ->
                runCatching { listener(event) }
            }
        }
    }

    companion object {
        private const val MAX_EVENTS = 100
    }
}

/** Memetakan state workspace ke badge status dashboard. */
fun WorkspaceState.toStatusType(): StatusType =
    when (this) {
        WorkspaceState.IDLE -> StatusType.INFO
        WorkspaceState.READY -> StatusType.READY
        WorkspaceState.BUSY -> StatusType.RUNNING
        WorkspaceState.COMPLETED -> StatusType.READY
        WorkspaceState.FAILED -> StatusType.ERROR
    }
