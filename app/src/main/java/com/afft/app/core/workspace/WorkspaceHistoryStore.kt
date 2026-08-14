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
 * Persistensi riwayat operasi workspace (history.json).
 *
 * Format: satu baris per operasi, field dipisah tab:
 * `type<TAB>timestamp<TAB>durationMillis<TAB>result<TAB>detail`
 * (terbaru di depan). Ditulis atomik (temp + rename) agar aman saat
 * proses terhenti.
 */
object WorkspaceHistoryStore {

    const val FILE_NAME = "history.json"

    /** Baca riwayat dari disk; kosong bila file belum ada/rusak. */
    fun load(root: File): List<WorkspaceOperation> =
        runCatching {
            val file = File(root, FILE_NAME)
            if (!file.exists()) return emptyList()
            file.readLines().mapNotNull { line -> decode(line) }
        }.getOrDefault(emptyList())

    /** Tulis riwayat (terbaru di depan) ke disk secara atomik. */
    fun save(root: File, operations: List<WorkspaceOperation>) {
        runCatching {
            if (!root.exists() && !root.mkdirs()) return
            val target = File(root, FILE_NAME)
            val temp = File(root, "$FILE_NAME.tmp")
            val body = operations.joinToString("\n") { encode(it) }
            temp.writeText(if (body.isEmpty()) "" else "$body\n")
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    /** Hapus file riwayat di disk. */
    fun clear(root: File) {
        runCatching { File(root, FILE_NAME).delete() }
    }

    private fun encode(op: WorkspaceOperation): String =
        "${escape(op.type)}\t${op.timestamp}\t${op.durationMillis}\t${op.result.name}\t${escape(op.detail)}"

    private fun decode(raw: String): WorkspaceOperation? {
        if (raw.isBlank()) return null
        val parts = raw.split('\t', limit = 5)
        if (parts.size < 4) return null
        val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val duration = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val result =
            runCatching { OperationResult.valueOf(parts[3]) }.getOrNull() ?: OperationResult.FAILED
        val detail = parts.getOrNull(4)?.let { unescape(it) } ?: ""
        return WorkspaceOperation(
            type = unescape(parts[0]),
            timestamp = timestamp,
            durationMillis = duration,
            result = result,
            detail = detail,
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun unescape(s: String): String =
        s.replace("\\\\", "\\")
            .replace("\\t", "\t")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
}
