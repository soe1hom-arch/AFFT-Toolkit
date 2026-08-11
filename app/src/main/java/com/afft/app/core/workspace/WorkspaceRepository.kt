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
 * Persistensi proyek workspace (metadata.json).
 *
 * Strategi: satu file JSON per proyek, ditulis atomik (file temp + rename)
 * agar aman saat proses terhenti. Load dilakukan lazy per proyek.
 */
class WorkspaceRepository {

    fun saveProject(root: File, metadata: WorkspaceMetadata): Result<Unit> =
        runCatching {
            if (!root.exists() && !root.mkdirs()) error("Cannot create project directory")
            val target = File(root, METADATA_FILE_NAME)
            val temp = File(root, "$METADATA_FILE_NAME.tmp")
            temp.writeText(MiniJson.encode(metadata))
            if (!temp.renameTo(target)) {
                target.writeText(MiniJson.encode(metadata))
                temp.delete()
            }
        }

    fun loadProject(root: File): Result<WorkspaceProject> =
        runCatching {
            val file = File(root, METADATA_FILE_NAME)
            if (!file.exists()) error("Metadata missing: ${root.name}")
            val metadata = MiniJson.decode(file.readText()) ?: error("Invalid metadata: ${root.name}")
            WorkspaceProject(root, metadata)
        }

    fun projectExists(root: File): Boolean = File(root, METADATA_FILE_NAME).exists()

    fun deleteProject(root: File): Result<Unit> =
        runCatching {
            if (root.exists() && !root.deleteRecursively()) error("Cannot delete ${root.name}")
        }

    /** Menulis JSON mini untuk metadata proyek (flat object). */
    internal object MiniJson {

        fun encode(m: WorkspaceMetadata): String {
            val sb = StringBuilder()
            sb.append('{')
            field(sb, "name", m.name)
            field(sb, "createdAt", m.createdAt)
            field(sb, "lastOpenedAt", m.lastOpenedAt)
            field(sb, "androidVersion", m.androidVersion)
            field(sb, "device", m.device)
            field(sb, "codename", m.codename)
            field(sb, "firmwareType", m.firmwareType)
            field(sb, "workspaceVersion", m.workspaceVersion.toLong())
            field(sb, "status", m.status.name)
            field(sb, "healthScore", m.healthScore?.toLong())
            sb.append('}')
            return sb.toString()
        }

        fun decode(raw: String): WorkspaceMetadata? {
            val values = mutableMapOf<String, String>()
            val regex =
                Regex("\"([^\"]+)\"\\s*:\\s*(?:\"((?:\\\\.|[^\"])*)\"|(-?\\d+)|(true|false)|null)")
            regex.findAll(raw).forEach { match ->
                val key = match.groupValues[1]
                val str = match.groupValues[2]
                val num = match.groupValues[3]
                val bool = match.groupValues[4]
                values[key] =
                    when {
                        str.isNotEmpty() -> str
                        num.isNotEmpty() -> num
                        bool.isNotEmpty() -> bool
                        else -> ""
                    }
            }
            val name = values["name"] ?: return null
            val createdAt = values["createdAt"]?.toLongOrNull() ?: 0L
            val lastOpenedAt = values["lastOpenedAt"]?.toLongOrNull() ?: createdAt
            val workspaceVersion = values["workspaceVersion"]?.toIntOrNull() ?: WorkspaceMetadata.CURRENT_WORKSPACE_VERSION
            val status = values["status"]?.let { runCatching { WorkspaceState.valueOf(it) }.getOrNull() } ?: WorkspaceState.IDLE
            val healthScore = values["healthScore"]?.toIntOrNull()
            return WorkspaceMetadata(
                name = name,
                createdAt = createdAt,
                lastOpenedAt = lastOpenedAt,
                androidVersion = values["androidVersion"]?.takeIf { it.isNotEmpty() },
                device = values["device"]?.takeIf { it.isNotEmpty() },
                codename = values["codename"]?.takeIf { it.isNotEmpty() },
                firmwareType = values["firmwareType"]?.takeIf { it.isNotEmpty() },
                workspaceVersion = workspaceVersion,
                status = status,
                healthScore = healthScore,
            )
        }

        private fun field(sb: StringBuilder, key: String, value: String?) {
            if (sb.length > 1) sb.append(',')
            sb.append('"').append(key).append("\":")
            if (value == null) sb.append("null") else sb.append('"').append(escape(value)).append('"')
        }

        private fun field(sb: StringBuilder, key: String, value: Long?) {
            if (sb.length > 1) sb.append(',')
            sb.append('"').append(key).append("\":").append(value ?: "null")
        }

        private fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    companion object {
        const val METADATA_FILE_NAME = "metadata.json"
    }
}
