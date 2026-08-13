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

package com.afft.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.afft.app.R
import com.afft.app.service.AFFTService
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.LocalIconTint
import com.afft.app.util.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LogsPanel(
    afftService: AFFTService,
    modifier: Modifier = Modifier,
) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var logFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedLog by remember { mutableStateOf<File?>(null) }
    var logContent by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(true) }

    // Load log files
    LaunchedEffect(Unit) {
        logFiles = afftService.getLogFiles()
        isLoading = false
    }

    if (selectedLog != null) {
        // View selected log file
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    selectedLog = null
                    logContent = ""
                }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), null, tint = LocalIconTint.current)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kembali")
                }
                Text(
                    selectedLog?.name ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = LocalFontFamily.current,
                )
                Text(
                    "${selectedLog?.let { file -> formatFileSize(file.length()) } ?: ""}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Load content
            LaunchedEffect(selectedLog) {
                selectedLog?.let { file ->
                    logContent = afftService.getLogContent(file)
                }
            }

            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                ) {
                    val lines = logContent.lines()
                    items(lines.size) { index ->
                        if (index < lines.size) {
                            ColoredLogLine(
                                text = lines[index],
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = {
                    scope.launch {
                        afftService.saveCurrentLogToDownloads()
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_save_alt), null, tint = LocalIconTint.current)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan ke Downloads")
                }
            }
        }
    } else {
        // List log files
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Log Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = LocalFontFamily.current,
                )
                Row {
                    // Refresh button
                    IconButton(onClick = {
                        isLoading = true
                        scope.launch {
                            logFiles = afftService.getLogFiles()
                            isLoading = false
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_refresh), "Refresh", tint = LocalIconTint.current)
                    }
                    // Clear old logs
                    IconButton(onClick = {
                        scope.launch {
                            afftService.clearOldLogs(20)
                            logFiles = afftService.getLogFiles()
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_cleaning_services),
                            "Clean Old",
                            tint = LocalIconTint.current,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Folder: ${afftService.getLogsDir().absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalFontFamily.current,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (logFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.ic_text_snippet),
                            null,
                            tint = LocalIconTint.current,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Belum ada log file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Jalankan operasi terlebih dahulu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logFiles.size) { index ->
                        val file = logFiles[index]
                        Card(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedLog = file
                                    },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        fontFamily = LocalFontFamily.current,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        formatFileSize(file.length()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = LocalFontFamily.current,
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ic_chevron_right),
                                    null,
                                    tint = LocalIconTint.current,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
