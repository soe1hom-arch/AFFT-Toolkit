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

import android.content.Context
import com.afft.app.core.parser.EngineResult
import com.afft.app.core.parser.FirmwareAnalysisContext
import com.afft.app.core.parser.FirmwareAnalysisEngine
import com.afft.app.core.parser.FirmwareAnalysisError
import com.afft.app.core.parser.FirmwareAnalysisException
import com.afft.app.core.parser.FirmwareParserFactory
import com.afft.app.core.parser.FirmwareParserRegistry
import com.afft.app.core.parser.ParserResult
import com.afft.app.core.parser.ParserStatus
import com.afft.app.core.parser.PayloadParser
import com.afft.app.core.workspace.WorkspaceEngine
import com.afft.app.core.workspace.WorkspaceManager
import com.afft.app.core.workspace.WorkspaceOperation
import com.afft.app.core.workspace.WorkspaceProject
import com.afft.app.core.workspace.WorkspaceState
import com.afft.app.ui.components.dashboard.FirmwareMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Snapshot kondisi workspace untuk konsumsi UI (Home, Payload, dll).
 *
 * Workspace menjadi single source of truth: UI membaca [WorkspaceUiState]
 * yang selalu disinkronkan dari [WorkspaceEngine] + hasil analisis terakhir.
 */
data class WorkspaceUiState(
    val project: WorkspaceProject? = null,
    val state: WorkspaceState = WorkspaceState.IDLE,
    val firmwareMetadata: FirmwareMetadata? = null,
    val currentFile: String? = null,
    val lastOperation: WorkspaceOperation? = null,
    val history: List<WorkspaceOperation> = emptyList(),
    val healthScore: Int? = null,
    val errorMessage: String? = null,
    val isAnalyzing: Boolean = false,
) {
    val isEmpty: Boolean get() = project == null && firmwareMetadata == null
}

/**
 * WorkspaceCoordinator — facade tunggal yang menghubungkan:
 *
 *   UI (Home/Payload) ──► [WorkspaceCoordinator]
 *                            │  analisis
 *                            ├──► [FirmwareAnalysisEngine] ──► [FirmwareParserRegistry]
 *                            │                                  └── PayloadFirmwareParser ─► PayloadParser
 *                            │  proyek / state / history / metadata
 *                            └──► [WorkspaceEngine] ──► [WorkspaceManager] ──► metadata.json
 *
 * Tanggung jawab:
 *   - membuka/membuat proyek saat file dipilih
 *   - menjalankan analisis firmware (tanpa duplikasi, memakai cache)
 *   - menyinkronkan metadata/history/health/event ke workspace
 *   - menyediakan [StateFlow] untuk UI tanpa state duplikat
 */
class WorkspaceCoordinator(
    val engine: WorkspaceEngine,
    private val analysisEngine: FirmwareAnalysisEngine,
) {

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private var analyzedFile: File? = null
    private var analyzedMetadata: FirmwareMetadata? = null

    /**
     * Menganalisis firmware. Idempotent: file yang sama (pada proyek yang
     * sama) hanya dianalisis sekali — selanjutnya memakai cache.
     */
    suspend fun analyze(file: File): FirmwareMetadata {
        return withContext(Dispatchers.IO) {
            analyzedMetadata?.let { cached ->
                if (analyzedFile == file && engine.currentProject?.name == projectNameFor(file)) {
                    refreshFromEngine()
                    return@withContext cached
                }
            }

            ensureProject(file)
            val handle = engine.beginOperation("Analysis")
                ?: error("No active workspace project")
            _state.update {
                it.copy(
                    isAnalyzing = true,
                    currentFile = file.name,
                    firmwareMetadata = null,
                    errorMessage = null,
                )
            }

            when (val result = analysisEngine.analyze(file)) {
                is EngineResult.Success -> {
                    val parserName = result.context.parserName ?: "unknown"
                    val opType =
                        when (parserName) {
                            "boot" -> "Boot Analysis"
                            "super" -> "Super Analysis"
                            "filesystem" -> "Filesystem Analysis"
                            else -> "Analysis"
                        }
                    syncMetadata(result.metadata, parserName)
                    engine.recordOperation(
                        type = opType,
                        durationMillis = (System.currentTimeMillis() - handle.startedAt).coerceAtLeast(0L),
                        success = true,
                        detail = "$opType completed for ${file.name}",
                    )
                    engine.updateResumePoint(toolIdFor(parserName), file.name, "analyzed")
                    analyzedFile = file
                    analyzedMetadata = result.metadata
                    _state.update {
                        it.copy(
                            project = engine.currentProject,
                            state = engine.state,
                            lastOperation = engine.lastOperation(),
                            history = engine.history(),
                            firmwareMetadata = result.metadata,
                            currentFile = file.name,
                            healthScore = result.metadata.healthScore,
                            errorMessage = null,
                            isAnalyzing = false,
                        )
                    }
                    result.metadata
                }

                is EngineResult.Failure -> {
                    val errorMetadata = failureMetadata(result.error)
                    engine.recordOperation(
                        type = "Analysis",
                        durationMillis = (System.currentTimeMillis() - handle.startedAt).coerceAtLeast(0L),
                        success = false,
                        detail = result.error.message ?: "Analysis failed",
                    )
                    analyzedFile = file
                    analyzedMetadata = errorMetadata
                    _state.update {
                        it.copy(
                            project = engine.currentProject,
                            state = engine.state,
                            lastOperation = engine.lastOperation(),
                            history = engine.history(),
                            firmwareMetadata = errorMetadata,
                            currentFile = file.name,
                            healthScore = 0,
                            errorMessage = result.error.message,
                            isAnalyzing = false,
                        )
                    }
                    errorMetadata
                }
            }
        }
    }

    /** Menghapus pemilihan file saat ini (state kembali, proyek tetap). */
    fun clearFileSelection() {
        analyzedFile = null
        analyzedMetadata = null
        _state.update {
            it.copy(
                firmwareMetadata = null,
                currentFile = null,
                errorMessage = null,
                isAnalyzing = false,
            )
        }
    }

    fun refresh() = refreshFromEngine()

    /**
     * Mencatat hasil operasi firmware (extract/repack) ke riwayat proyek aktif
     * dan memperbarui titik lanjut. Dipanggil dari layar tool setelah operasi.
     */
    fun recordOperation(
        title: String,
        ok: Boolean,
        durationMillis: Long = 0L,
        detail: String = "",
        resumeTool: String? = null,
        resumeStep: String? = null,
        resumeFile: String? = null,
    ) {
        engine.recordOperation(title, durationMillis, ok, detail)
        if (ok && resumeTool != null) {
            engine.updateResumePoint(resumeTool, resumeFile, resumeStep ?: "done")
        }
        refreshFromEngine()
    }

    /** Menghapus seluruh riwayat operasi proyek aktif (memori + disk). */
    fun clearHistory() {
        engine.clearHistory()
        refreshFromEngine()
    }

    /**
     * File yang bisa dilanjutkan untuk tool tertentu: memakai [WorkspaceMetadata.lastTool]
     * + [WorkspaceMetadata.lastFile] bila file masih ada di [inputDir].
     */
    suspend fun resumeInputFor(
        toolId: String,
        inputDir: File,
    ): File? =
        withContext(Dispatchers.IO) {
            val metadata = engine.currentProject?.metadata ?: return@withContext null
            if (metadata.lastTool != toolId || metadata.lastFile.isNullOrBlank()) {
                return@withContext null
            }
            val file = File(inputDir, metadata.lastFile)
            if (file.isFile) file else null
        }

    /**
     * Mencari file input/ terbaru yang benar-benar cocok dengan parser
     * tertentu (berdasarkan magic, bukan ekstensi). Dipakai auto-detect
     * per layar agar Payload/Boot/Super/Filesystem tidak saling membaca
     * file dari menu lain.
     */
    suspend fun latestInputFor(
        parserName: String,
        inputDir: File,
    ): File? =
        withContext(Dispatchers.IO) {
            val parser = analysisEngine.parser(parserName) ?: return@withContext null
            val files = inputDir.listFiles()?.filter { it.isFile } ?: emptyList()
            files
                .filter { parser.canParse(it, FirmwareAnalysisContext.start(it)) }
                .maxByOrNull { it.lastModified() }
        }

    // ---------- internal ----------

    /** Membuka proyek untuk file; membuatnya bila belum ada. */
    private fun ensureProject(file: File) {
        val name = projectNameFor(file)
        if (engine.currentProject?.name == name) return
        if (engine.openProject(name).isFailure) {
            engine.createProject(name)
        }
    }

    /** Menyinkronkan metadata hasil analisis ke proyek aktif + persis. */
    private fun syncMetadata(metadata: FirmwareMetadata, parserName: String) {
        val android = metadata.sections.firstOrNull { it.title == "Android" }
        fun androidRow(label: String): String? =
            android?.rows?.firstOrNull { it.label == label }
                ?.value?.takeIf { it.isNotEmpty() && it != "Unknown" }

        engine.updateProject { m ->
            m.copy(
                androidVersion = androidRow("Android Version") ?: m.androidVersion,
                device = androidRow("Device Name") ?: m.device,
                codename = androidRow("Device Codename") ?: m.codename,
                firmwareType = firmwareTypeFor(parserName),
                healthScore = metadata.healthScore,
                status = WorkspaceState.COMPLETED,
                lastOpenedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun firmwareTypeFor(parserName: String): String =
        when (parserName) {
            "boot" -> "Boot Image"
            "super" -> "Super"
            "payload" -> "Payload"
            "filesystem" -> "Filesystem"
            else -> "Firmware"
        }

    private fun toolIdFor(parserName: String): String =
        when (parserName) {
            "boot", "super", "payload", "filesystem" -> parserName
            else -> "firmware"
        }

    /** Memetakan kegagalan analisis ke FirmwareMetadata error (UI). */
    private fun failureMetadata(error: FirmwareAnalysisException): FirmwareMetadata {
        val status =
            when (error.error) {
                FirmwareAnalysisError.MISSING_METADATA -> ParserStatus.FILE_NOT_FOUND
                FirmwareAnalysisError.PERMISSION_DENIED -> ParserStatus.PERMISSION_DENIED
                FirmwareAnalysisError.UNSUPPORTED_FORMAT -> ParserStatus.INVALID_PAYLOAD
                FirmwareAnalysisError.CORRUPTED_IMAGE -> ParserStatus.CORRUPTED_METADATA
                else -> ParserStatus.READ_ERROR
            }
        return PayloadParser.failureMetadata(ParserResult.Failure(status, error.message ?: "Analysis failed"))
    }

    private fun projectNameFor(file: File): String {
        val base = file.nameWithoutExtension.trim().ifEmpty { file.name }
        return base.take(120)
    }

    private fun refreshFromEngine() {
        val snapshot = engine.snapshot()
        _state.update {
            it.copy(
                project = snapshot.project,
                state = snapshot.state,
                lastOperation = snapshot.lastOperation,
                history = engine.history(),
                healthScore = analyzedMetadata?.healthScore ?: snapshot.project?.metadata?.healthScore,
            )
        }
    }

    companion object {
        /** Membuat coordinator default (registy + engine + workspace di root default). */
        fun create(context: Context): WorkspaceCoordinator {
            val manager = WorkspaceManager(WorkspaceManager.defaultWorkspaceRoot(context))
            val engine = WorkspaceEngine(manager)
            val registry = FirmwareParserRegistry()
            FirmwareParserFactory.createDefault().forEach { registry.register(it) }
            return WorkspaceCoordinator(engine, FirmwareAnalysisEngine(registry))
        }
    }
}
