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
 * Metadata proyek yang dipersist ke metadata.json.
 */
data class WorkspaceMetadata(
    val name: String,
    val createdAt: Long,
    val lastOpenedAt: Long,
    val androidVersion: String? = null,
    val device: String? = null,
    val codename: String? = null,
    val firmwareType: String? = null,
    val workspaceVersion: Int = CURRENT_WORKSPACE_VERSION,
    val status: WorkspaceState = WorkspaceState.IDLE,
    val healthScore: Int? = null,
) {
    companion object {
        const val CURRENT_WORKSPACE_VERSION = 1
    }
}
