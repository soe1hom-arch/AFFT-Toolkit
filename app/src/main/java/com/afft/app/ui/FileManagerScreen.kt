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
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.LocalIconTint

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.R
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.AppDialog
import com.afft.app.ui.components.DialogOptionCard
import com.afft.app.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var pathHistory by remember { mutableStateOf<List<File>>(emptyList()) }
    var showSize by rememberSaveable { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showCopyDestDialog by rememberSaveable { mutableStateOf(false) }
    var showMoveDestDialog by rememberSaveable { mutableStateOf(false) }
    var operationInProgress by rememberSaveable { mutableStateOf(false) }
    var toastMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by rememberSaveable { mutableStateOf("name") }
    var sortAsc by rememberSaveable { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showPropertiesFor by remember { mutableStateOf<File?>(null) }
    var showPropertiesDialog by rememberSaveable { mutableStateOf(false) }

    val workDir = afftService.getWorkDir()
    val tempDir = afftService.getTempDir()
    val inputDir = afftService.getInputDir()
    val downloadDir = afftService.getExportDir()
    // Show toast
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    suspend fun refreshFiles(dir: File) =
        withContext(Dispatchers.IO) {
            val rawList = dir.listFiles()?.toList() ?: emptyList()
            val filtered =
                if (searchQuery.isBlank()) {
                    rawList
                } else {
                    rawList.filter { it.name.lowercase().contains(searchQuery.lowercase()) }
                }

            val sorted =
                filtered.sortedWith(
                    when (sortMode) {
                        "date" ->
                            compareByDescending<File> { !it.isDirectory }
                                .thenByDescending { it.lastModified() }
                        "size" ->
                            compareBy<File> { !it.isDirectory }
                                .thenBy { if (it.isDirectory) 0L else it.length() }
                        "type" ->
                            compareBy<File> { !it.isDirectory }
                                .thenBy { it.extension.lowercase() }
                        else ->
                            compareBy<File> { !it.isDirectory }
                                .thenBy { it.name.lowercase() }
                    },
                )
            val list = if (sortAsc) sorted else sorted.reversed()

            withContext(Dispatchers.Main) {
                currentDir = dir
                files = list
                selectedFiles = emptySet()
                selectMode = false
            }
        }

    // File picker for importing from external storage
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    operationInProgress = true
                    val result = afftService.pickAndCopyToInput(uri)
                    operationInProgress = false
                    if (result != null) {
                        toastMessage = "File diimpor: ${result.name}"
                        refreshFiles(currentDir ?: inputDir)
                    } else {
                        toastMessage = "Gagal mengimpor file"
                    }
                }
            }
        }

    fun toggleSelectFile(file: File) {
        selectedFiles =
            if (selectedFiles.contains(file)) {
                selectedFiles - file
            } else {
                selectedFiles + file
            }
        selectMode = selectedFiles.isNotEmpty()
    }

    LaunchedEffect(Unit) {
        refreshFiles(inputDir)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "AFFT Manager",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = LocalFontFamily.current,
            )
            if (selectMode) {
                TextButton(onClick = {
                    selectedFiles = emptySet()
                    selectMode = false
                }) {
                    Text("Batal", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Path bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pathHistory.isNotEmpty()) {
                IconButton(onClick = {
                    scope.launch {
                        val prev = pathHistory.last()
                        pathHistory = pathHistory.dropLast(1)
                        refreshFiles(prev)
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = LocalIconTint.current)
                }
            }
            // Parent directory button
            IconButton(
                onClick = {
                    scope.launch {
                        currentDir?.parentFile?.let { parent ->
                            pathHistory = pathHistory + (currentDir ?: inputDir)
                            refreshFiles(parent)
                        }
                    }
                },
                enabled = currentDir != null && currentDir?.parentFile != null,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(painterResource(R.drawable.ic_subdirectory_arrow_left), "↑", tint = LocalIconTint.current)
            }
            Text(
                text = currentDir?.absolutePath ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = LocalFontFamily.current,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Refresh button
            IconButton(
                onClick = {
                    scope.launch {
                        currentDir?.let { refreshFiles(it) }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(painterResource(R.drawable.ic_refresh), "Refresh", tint = LocalIconTint.current)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Quick location + actions row 1 (chips scrollable agar tidak terpotong di layar sempit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {
                        scope.launch {
                            pathHistory = emptyList()
                            refreshFiles(File("/storage/emulated/0"))
                        }
                    },
                    label = { Text("Device", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_phone_android),
                            null,
                            tint = LocalIconTint.current,
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        scope.launch {
                            pathHistory = emptyList()
                            refreshFiles(tempDir)
                        }
                    },
                    label = { Text("Temp", fontSize = 12.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_folder), null, tint = LocalIconTint.current) },
                )
                AssistChip(
                    onClick = {
                        scope.launch {
                            pathHistory = emptyList()
                            refreshFiles(workDir)
                        }
                    },
                    label = { Text("Work", fontSize = 12.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_folder), null, tint = LocalIconTint.current) },
                )
                AssistChip(
                    onClick = {
                        scope.launch {
                            pathHistory = emptyList()
                            refreshFiles(inputDir)
                        }
                    },
                    label = { Text("Input", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_drive_file_move),
                            null,
                            tint = LocalIconTint.current,
                        )
                    },
                )
                if (downloadDir.exists()) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                pathHistory = emptyList()
                                refreshFiles(downloadDir)
                            }
                        },
                        label = { Text("DL", fontSize = 12.sp) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_download), null, tint = LocalIconTint.current) },
                    )
                }
            }
            // Import button (selalu terlihat di kanan)
            IconButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !operationInProgress,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(painterResource(R.drawable.ic_file_open), "Import", tint = LocalIconTint.current)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Sort + Search row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sort chips
            FilterChip(
                selected = sortMode == "name",
                onClick = {
                    if (sortMode == "name") {
                        sortAsc = !sortAsc
                    } else {
                        sortMode = "name"
                        sortAsc = true
                    }
                    scope.launch { currentDir?.let { refreshFiles(it) } }
                },
                label = { Text("Nama", fontSize = 10.sp) },
                leadingIcon = {
                    Icon(
                        if (sortAsc) {
                            painterResource(
                                R.drawable.ic_arrow_up,
                            )
                        } else {
                            painterResource(R.drawable.ic_arrow_down)
                        },
                        null,
                        tint = LocalIconTint.current,
                    )
                },
                modifier = Modifier.height(28.dp),
            )
            FilterChip(
                selected = sortMode == "date",
                onClick = {
                    if (sortMode == "date") {
                        sortAsc = !sortAsc
                    } else {
                        sortMode = "date"
                        sortAsc = false
                    }
                    scope.launch { currentDir?.let { refreshFiles(it) } }
                },
                label = { Text("Tgl", fontSize = 10.sp) },
                leadingIcon = {
                    Icon(
                        if (sortAsc) {
                            painterResource(
                                R.drawable.ic_arrow_up,
                            )
                        } else {
                            painterResource(R.drawable.ic_arrow_down)
                        },
                        null,
                        tint = LocalIconTint.current,
                    )
                },
                modifier = Modifier.height(28.dp),
            )
            FilterChip(
                selected = sortMode == "size",
                onClick = {
                    if (sortMode == "size") {
                        sortAsc = !sortAsc
                    } else {
                        sortMode = "size"
                        sortAsc = false
                    }
                    scope.launch { currentDir?.let { refreshFiles(it) } }
                },
                label = { Text("Ukuran", fontSize = 10.sp) },
                leadingIcon = {
                    Icon(
                        if (sortAsc) {
                            painterResource(
                                R.drawable.ic_arrow_up,
                            )
                        } else {
                            painterResource(R.drawable.ic_arrow_down)
                        },
                        null,
                        tint = LocalIconTint.current,
                    )
                },
                modifier = Modifier.height(28.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Search toggle
            IconButton(
                onClick = { showSearch = !showSearch },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(painterResource(R.drawable.ic_search), "Search", tint = LocalIconTint.current)
            }
            // Create folder
            IconButton(
                onClick = {
                    newFolderName = ""
                    showCreateFolderDialog = true
                },
                enabled = !operationInProgress,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(painterResource(R.drawable.ic_create_new_folder), "Folder Baru", tint = LocalIconTint.current)
            }
        }

        // Search bar
        if (showSearch) {
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { q ->
                    searchQuery = q
                    scope.launch { currentDir?.let { refreshFiles(it) } }
                },
                placeholder = { Text("Cari file...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = LocalIconTint.current) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            scope.launch { currentDir?.let { refreshFiles(it) } }
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(painterResource(R.drawable.ic_clear), null, tint = LocalIconTint.current)
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // File list
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            LazyColumn(modifier = Modifier.padding(2.dp)) {
                if (files.isNotEmpty()) {
                    items(files, key = { it.absolutePath }) { file ->
                        FileRow(
                            file = file,
                            isSelected = selectedFiles.contains(file),
                            selectMode = selectMode,
                            onClick = {
                                if (selectMode) {
                                    toggleSelectFile(file)
                                } else if (file.isDirectory) {
                                    scope.launch {
                                        pathHistory = pathHistory + (currentDir ?: tempDir)
                                        refreshFiles(file)
                                    }
                                }
                            },
                            onLongClick = {
                                toggleSelectFile(file)
                            },
                            showSize = showSize,
                        )
                    }
                }
                val minRows = 3
                val currentRows = files.size
                if (currentRows < minRows) {
                    items(minRows - currentRows) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(vertical = 1.dp),
                        )
                    }
                }
            }
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Folder kosong atau tidak ada file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Action bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: info
                Text(
                    if (selectMode) {
                        "${selectedFiles.size} selected"
                    } else {
                        "${files.size} item(s)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = LocalFontFamily.current,
                    fontSize = 11.sp,
                )

                // Right: actions
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (selectMode) {
                        // Select All
                        IconButton(onClick = {
                            selectedFiles = files.toSet()
                            selectMode = true
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_select_all), "Select All", tint = LocalIconTint.current)
                        }
                        // Rename & Properties (hanya saat 1 item dipilih)
                        if (selectedFiles.size == 1) {
                            IconButton(
                                onClick = {
                                    renameTarget = selectedFiles.first()
                                    renameText = renameTarget?.name.orEmpty()
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_edit), "Rename", tint = LocalIconTint.current)
                            }
                            IconButton(
                                onClick = {
                                    showPropertiesFor = selectedFiles.first()
                                    showPropertiesDialog = true
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_info), "Properties", tint = LocalIconTint.current)
                            }
                        }
                        // Copy
                        IconButton(
                            onClick = { showCopyDestDialog = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress,
                        ) {
                            Icon(painterResource(R.drawable.ic_content_copy), "Copy", tint = LocalIconTint.current)
                        }
                        // Move
                        IconButton(
                            onClick = { showMoveDestDialog = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress,
                        ) {
                            Icon(painterResource(R.drawable.ic_drive_file_move), "Move", tint = LocalIconTint.current)
                        }
                        // Delete
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(36.dp),
                            enabled = !operationInProgress,
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), "Delete", tint = LocalIconTint.current)
                        }
                    } else {
                        // Default actions when no selection
                        IconButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_file_open), "Import", tint = LocalIconTint.current)
                        }
                        TextButton(onClick = { showSize = !showSize }) {
                            Text(if (showSize) "Hide" else "Size", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && selectedFiles.isNotEmpty()) {
        AppDialog(
            iconRes = R.drawable.ic_warning,
            title = "Hapus Permanen?",
            subtitle = "File/folder berikut akan dihapus permanen dan tidak bisa dikembalikan.",
            onDismiss = { showDeleteConfirm = false },
            accent = MaterialTheme.colorScheme.error,
            content = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    selectedFiles.take(10).forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_insert_drive_file),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LocalIconTint.current,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                f.name,
                                fontFamily = LocalFontFamily.current,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (selectedFiles.size > 10) {
                        Text(
                            "...dan ${selectedFiles.size - 10} lainnya",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            footer = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            var fail = 0
                            for (f in selectedFiles) {
                                if (afftService.deleteFileWithSafety(f)) {
                                    ok++
                                } else {
                                    fail++
                                }
                            }
                            operationInProgress = false
                            toastMessage = "Dihapus: $ok file, gagal: $fail"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) { Text("Hapus") }
            },
        )
    }

    // Copy destination dialog
    if (showCopyDestDialog && selectedFiles.isNotEmpty()) {
        AppDialog(
            iconRes = R.drawable.ic_content_copy,
            title = "Salin ke...",
            subtitle = "Pilih tujuan penyalinan untuk ${selectedFiles.size} file terpilih.",
            onDismiss = { showCopyDestDialog = false },
            content = {
                DialogOptionCard(
                    iconRes = R.drawable.ic_download,
                    title = "Downloads/AFFT",
                    description = "Folder hasil ekspor di penyimpanan publik",
                    emphasized = true,
                    onClick = {
                        showCopyDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                if (afftService.copyFileTo(f, downloadDir)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Disalin ke Downloads: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_folder_open,
                    title = "Input/ (workspace)",
                    description = "Folder input di workspace AFFT",
                    onClick = {
                        showCopyDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                if (afftService.copyFileTo(f, inputDir)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Disalin ke input/: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_folder,
                    title = "Folder saat ini",
                    description = currentDir?.absolutePath ?: inputDir.absolutePath,
                    onClick = {
                        showCopyDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                val dest = currentDir ?: inputDir
                                if (afftService.copyFileTo(f, dest)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Disalin ke folder saat ini: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
            },
            footer = {
                TextButton(onClick = { showCopyDestDialog = false }) {
                    Text("Batal")
                }
            },
        )
    }

    // Move destination dialog
    if (showMoveDestDialog && selectedFiles.isNotEmpty()) {
        AppDialog(
            iconRes = R.drawable.ic_drive_file_move,
            title = "Pindah ke...",
            subtitle = "Pilih tujuan pemindahan untuk ${selectedFiles.size} file terpilih.",
            onDismiss = { showMoveDestDialog = false },
            content = {
                DialogOptionCard(
                    iconRes = R.drawable.ic_download,
                    title = "Downloads/AFFT",
                    description = "Folder hasil ekspor di penyimpanan publik",
                    emphasized = true,
                    onClick = {
                        showMoveDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                if (afftService.moveFileTo(f, downloadDir)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Dipindah ke Downloads: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_folder_open,
                    title = "Input/ (workspace)",
                    description = "Folder input di workspace AFFT",
                    onClick = {
                        showMoveDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                if (afftService.moveFileTo(f, inputDir)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Dipindah ke input/: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_folder,
                    title = "Folder saat ini",
                    description = currentDir?.absolutePath ?: inputDir.absolutePath,
                    onClick = {
                        showMoveDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            var ok = 0
                            for (f in selectedFiles) {
                                val dest = currentDir ?: inputDir
                                if (afftService.moveFileTo(f, dest)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Dipindah ke folder saat ini: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_download,
                    title = "Downloads/ (root)",
                    description = "Folder Download utama di penyimpanan publik",
                    onClick = {
                        showMoveDestDialog = false
                        scope.launch {
                            operationInProgress = true
                            val dest = File("/storage/emulated/0/Download")
                            var ok = 0
                            for (f in selectedFiles) {
                                if (afftService.moveFileTo(f, dest)) ok++
                            }
                            operationInProgress = false
                            toastMessage = "Dipindah ke Downloads: $ok file"
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                )
            },
            footer = {
                TextButton(onClick = { showMoveDestDialog = false }) {
                    Text("Batal")
                }
            },
        )
    }

    // Create folder dialog
    if (showCreateFolderDialog) {
        AppDialog(
            iconRes = R.drawable.ic_create_new_folder,
            title = "Buat Folder Baru",
            subtitle = currentDir?.absolutePath ?: inputDir.absolutePath,
            onDismiss = { showCreateFolderDialog = false },
            content = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Nama folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isEmpty()) return@Button
                        showCreateFolderDialog = false
                        scope.launch {
                            operationInProgress = true
                            val ok = afftService.createFolder(currentDir ?: inputDir, name)
                            operationInProgress = false
                            toastMessage =
                                if (ok) {
                                    "Folder dibuat: $name"
                                } else {
                                    "Gagal membuat folder: $name"
                                }
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                ) { Text("Buat") }
            },
        )
    }

    // Rename dialog
    if (showRenameDialog && renameTarget != null) {
        AppDialog(
            iconRes = R.drawable.ic_edit,
            title = "Ubah Nama",
            subtitle = "Ubah nama: ${renameTarget?.name.orEmpty()}",
            onDismiss = { showRenameDialog = false },
            content = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            footer = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        val target = renameTarget ?: return@Button
                        val name = renameText.trim()
                        if (name.isEmpty()) return@Button
                        showRenameDialog = false
                        scope.launch {
                            operationInProgress = true
                            val ok = afftService.renameFile(target, name)
                            operationInProgress = false
                            toastMessage =
                                if (ok) {
                                    "Nama diubah menjadi: $name"
                                } else {
                                    "Gagal mengubah nama"
                                }
                            refreshFiles(currentDir ?: inputDir)
                        }
                    },
                ) { Text("Simpan") }
            },
        )
    }

    // Properties dialog
    if (showPropertiesDialog && showPropertiesFor != null) {
        val target = showPropertiesFor ?: File("")
        val totalSize =
            remember(target) {
                if (target.isDirectory) {
                    try {
                        target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    target.length()
                }
            }
        AppDialog(
            iconRes = R.drawable.ic_info,
            title = "Properti",
            subtitle = target.name,
            onDismiss = { showPropertiesDialog = false },
            content = {
                Column {
                    DetailRow("Nama", target.name)
                    DetailRow("Jenis", if (target.isDirectory) "Folder" else "File")
                    DetailRow("Ukuran", formatFileSize(totalSize))
                    DetailRow("Lokasi", target.parent ?: target.absolutePath)
                    DetailRow(
                        "Diubah",
                        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(target.lastModified())),
                    )
                }
            },
            footer = {
                TextButton(onClick = { showPropertiesDialog = false }) {
                    Text("Tutup")
                }
            },
        )
    }

    // Processing overlay - fixed card di bawah action bar
    if (operationInProgress) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memproses...", fontFamily = LocalFontFamily.current, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = LocalFontFamily.current,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    file: File,
    isSelected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showSize: Boolean,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(vertical = 1.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        file.isDirectory -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Checkbox in select mode
            if (selectMode || isSelected) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                when {
                    file.isDirectory -> painterResource(R.drawable.ic_folder)
                    file.name.endsWith(".img") -> painterResource(R.drawable.ic_sd_storage)
                    file.name.endsWith(".bin") -> painterResource(R.drawable.ic_archive)
                    else -> painterResource(R.drawable.ic_description)
                },
                contentDescription = null,
                tint = LocalIconTint.current,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontFamily = LocalFontFamily.current,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showSize && !file.isDirectory) {
                    Text(
                        text = formatFileSize(file.length()),
                        fontFamily = LocalFontFamily.current,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
