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

/**
 * Riwayat operasi workspace.
 *
 * Urutan: terbaru di depan (newest first).
 * Kapasitas dibatasi agar tidak membengkak tanpa batas;
 * pemfilteran (per tipe/tanggal) dapat ditambahkan belakangan.
 */
class WorkspaceHistory(
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val operations = ArrayDeque<WorkspaceOperation>()

    fun add(operation: WorkspaceOperation) {
        operations.addFirst(operation)
        while (operations.size > capacity) {
            operations.removeLast()
        }
    }

    /** Semua operasi, terbaru di depan. */
    fun all(): List<WorkspaceOperation> = operations.toList()

    fun latest(): WorkspaceOperation? = operations.firstOrNull()

    fun clear() {
        operations.clear()
    }

    fun size(): Int = operations.size

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
