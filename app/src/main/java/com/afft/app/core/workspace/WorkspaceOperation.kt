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

/** Hasil akhir sebuah operasi. */
enum class OperationResult { SUCCESS, FAILED }

/**
 * Satu operasi yang tercatat dalam proyek workspace.
 * Selalu membawa Timestamp, Duration, dan Result.
 */
data class WorkspaceOperation(
    val type: String,
    val timestamp: Long,
    val durationMillis: Long,
    val result: OperationResult,
    val detail: String = "",
)
