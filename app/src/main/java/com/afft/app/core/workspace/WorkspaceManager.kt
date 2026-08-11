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

import android.content.Context
import java.io.File

/**
 * Mengelola proyek di disk: create, open, rename, delete, list, recent.
 * Pemuatan metadata dilakukan lazy (satu proyek = satu metadata.json),
 * menghindari akses disk berlebihan.
 */
class WorkspaceManager(
    val workspaceRoot: File,
    private val repository: WorkspaceRepository = WorkspaceRepository(),
) {

    init {
        if (!workspaceRoot.exists()) {
            workspaceRoot.mkdirs()
        }
    }

    /** Membuat proyek baru + seluruh struktur direktori. */
    fun createProject(name: String): Result<WorkspaceProject> {
        val clean = sanitize(name) ?: return Result.failure(IllegalArgumentException("Invalid project name"))
        val projectDir = File(workspaceRoot, clean)
        if (repository.projectExists(projectDir)) {
            return Result.failure(IllegalArgumentException("Project '$clean' already exists"))
        }
        return runCatching {
            if (!projectDir.mkdirs()) error("Cannot create project directory")
            ensureStructure(projectDir)
            val now = System.currentTimeMillis()
            val metadata =
                WorkspaceMetadata(
                    name = clean,
                    createdAt = now,
                    lastOpenedAt = now,
                    workspaceVersion = WorkspaceMetadata.CURRENT_WORKSPACE_VERSION,
                    status = WorkspaceState.READY,
                )
            repository.saveProject(projectDir, metadata).getOrThrow()
            WorkspaceProject(projectDir, metadata)
        }
    }

    /** Membuka proyek + memperbarui lastOpenedAt. */
    fun openProject(name: String): Result<WorkspaceProject> {
        val projectDir = File(workspaceRoot, name)
        return repository.loadProject(projectDir).mapCatching { project ->
            val updated = project.metadata.copy(lastOpenedAt = System.currentTimeMillis(), status = WorkspaceState.READY)
            repository.saveProject(projectDir, updated).getOrThrow()
            ensureStructure(projectDir)
            WorkspaceProject(projectDir, updated)
        }
    }

    /** Menamai ulang proyek (rename folder + update metadata). */
    fun renameProject(project: WorkspaceProject, newName: String): Result<WorkspaceProject> {
        val clean = sanitize(newName) ?: return Result.failure(IllegalArgumentException("Invalid project name"))
        if (clean == project.name) return Result.success(project)
        val newDir = File(workspaceRoot, clean)
        if (repository.projectExists(newDir)) {
            return Result.failure(IllegalArgumentException("Project '$clean' already exists"))
        }
        return runCatching {
            if (!project.rootDir.renameTo(newDir)) error("Cannot rename project")
            val updated = project.metadata.copy(name = clean)
            repository.saveProject(newDir, updated).getOrThrow()
            WorkspaceProject(newDir, updated)
        }
    }

    /** Menghapus proyek beserta isinya. */
    fun deleteProject(name: String): Result<Unit> =
        repository.deleteProject(File(workspaceRoot, name))

    /** Semua proyek (metadata di-load lazy per proyek). */
    fun listProjects(): List<WorkspaceProject> {
        val dirs =
            workspaceRoot.listFiles { file -> file.isDirectory }
                ?: return emptyList()
        return dirs.mapNotNull { dir ->
            if (repository.projectExists(dir)) {
                repository.loadProject(dir).getOrNull()
            } else {
                null
            }
        }
    }

    /** Proyek terakhir dibuka (terbaru dulu). */
    fun recentProjects(limit: Int = 5): List<WorkspaceProject> =
        listProjects()
            .sortedByDescending { it.metadata.lastOpenedAt }
            .take(limit.coerceAtLeast(0))

    /** Membuat ulang folder standar jika hilang. */
    fun ensureStructure(root: File) {
        WorkspaceProject.REQUIRED_DIRS.forEach { dirName ->
            val dir = File(root, dirName)
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun sanitize(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 120) return null
        if (trimmed.startsWith(".")) return null
        if (trimmed.any { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' }) {
            return null
        }
        return trimmed
    }

    companion object {
        /** Root workspace default di penyimpanan aplikasi (tidak butuh izin publik). */
        fun defaultWorkspaceRoot(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "workspace")
    }
}
