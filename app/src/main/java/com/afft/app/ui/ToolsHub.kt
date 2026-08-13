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

package com.afft.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.afft.app.core.coordinator.WorkspaceCoordinator
import com.afft.app.service.AFFTService
import com.afft.app.ui.navigation.FirmwareTool
import com.afft.app.ui.theme.LocalFontFamily

/**
 * Hub untuk 4 tool firmware: Payload, Super, Filesystem, Boot.
 *
 * Tab aktif dipegang oleh route [AppScreen.Tools], sehingga state tiap
 * tool bertahan lewat SaveableStateProvider di MainScreen (switch tab =
 * ganti route, kembali = state direstore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHub(
    workspace: WorkspaceCoordinator,
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean,
    selectedTool: FirmwareTool,
    onToolSelect: (FirmwareTool) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = FirmwareTool.entries.indexOf(selectedTool),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FirmwareTool.entries.forEach { tool ->
                Tab(
                    selected = tool == selectedTool,
                    onClick = { onToolSelect(tool) },
                    text = {
                        Text(
                            tool.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = LocalFontFamily.current,
                        )
                    },
                )
            }
        }
        when (selectedTool) {
            FirmwareTool.PAYLOAD -> PayloadScreen(workspace, afftService, logs, isRunning)
            FirmwareTool.SUPER -> SuperScreen(workspace, afftService, logs, isRunning)
            FirmwareTool.FILESYSTEM -> FilesystemScreen(workspace, afftService, logs, isRunning)
            FirmwareTool.BOOT -> BootScreen(workspace, afftService, logs, isRunning)
        }
    }
}
