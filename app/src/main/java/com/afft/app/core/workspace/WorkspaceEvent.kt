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

/** Jenis event workspace. */
enum class WorkspaceEventType {
    PROJECT_CREATED,
    PROJECT_OPENED,
    PROJECT_UPDATED,
    ANALYSIS_STARTED,
    ANALYSIS_COMPLETED,
    ANALYSIS_FAILED,
    EXTRACTION_STARTED,
    EXTRACTION_FINISHED,
    EXPORT_FINISHED,
    PROJECT_CLOSED,
}

/** Event yang dipancarkan WorkspaceEngine. */
data class WorkspaceEvent(
    val type: WorkspaceEventType,
    val timestamp: Long,
    val projectName: String? = null,
    val detail: String? = null,
)
