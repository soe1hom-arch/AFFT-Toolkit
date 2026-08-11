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

/**
 * Proyek workspace aktif. Merupakan view atas direktori proyek:
 *   workspace/ProjectName/
 *     metadata.json, logs/, output/, temp/, cache/, payload/,
 *     boot/, super/, filesystem/, apk/, reports/
 */
class WorkspaceProject internal constructor(
    val rootDir: File,
    val metadata: WorkspaceMetadata,
) {
    val name: String get() = metadata.name

    fun metadataFile(): File = File(rootDir, "metadata.json")
    fun logsDir(): File = File(rootDir, "logs")
    fun outputDir(): File = File(rootDir, "output")
    fun tempDir(): File = File(rootDir, "temp")
    fun cacheDir(): File = File(rootDir, "cache")
    fun payloadDir(): File = File(rootDir, "payload")
    fun bootDir(): File = File(rootDir, "boot")
    fun superDir(): File = File(rootDir, "super")
    fun filesystemDir(): File = File(rootDir, "filesystem")
    fun reportsDir(): File = File(rootDir, "reports")

    companion object {
        /** Struktur direktori standar semua proyek. */
        val REQUIRED_DIRS =
            listOf("logs", "output", "temp", "cache", "payload", "boot", "super", "filesystem", "apk", "reports")
    }
}
