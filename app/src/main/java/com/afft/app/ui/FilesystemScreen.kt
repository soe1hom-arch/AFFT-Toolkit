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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.R
import com.afft.app.core.coordinator.WorkspaceCoordinator
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FileManagerPickerDialog
import com.afft.app.ui.components.FilePickerCard
import com.afft.app.ui.components.FileSourceSelectorDialog
import com.afft.app.ui.components.LiveStatusCard
import com.afft.app.ui.components.rememberDoneMessage
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.WorkspaceFileBrowserDialog
import com.afft.app.ui.components.dashboard.FirmwareInspector
import com.afft.app.ui.components.dashboard.FirmwareMetadata
import com.afft.app.ui.components.dashboard.emptyFirmwareMetadata
import com.afft.app.util.formatFileSize
import com.afft.app.util.stringListSaver
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesystemScreen(
    workspace: WorkspaceCoordinator,
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedInputFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedInputFile: File? = selectedInputFilePath?.let { path -> File(path) }
    val workspaceState by workspace.state.collectAsState()
    val progressMessage by afftService.progressMessage.collectAsState()
    val isFsLoading = workspaceState.isAnalyzing && workspaceState.currentFile == selectedInputFile?.name
    val fsInspectorMetadata = workspaceState.firmwareMetadata
    val doneMessage = rememberDoneMessage(progressMessage, isRunning)
    val liveActivity =
        when {
            isRunning -> progressMessage.ifBlank { "Memproses filesystem image..." }
            doneMessage != null -> doneMessage
            isFsLoading -> "Menganalisis ${selectedInputFile?.name ?: "filesystem image"}..."
            progressMessage.isNotBlank() -> progressMessage
            else -> null
        }
    var availableDirs by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf<List<String>>(emptyList()) }
    var selectedDir by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var repackSourcePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showRepackSourceBrowser by rememberSaveable { mutableStateOf(false) }
    var repackBrowseDir by remember { mutableStateOf(afftService.getTempDir()) }

    // Dialogs state
    var showSourceSelector by rememberSaveable { mutableStateOf(false) }
    var showWorkspaceBrowser by rememberSaveable { mutableStateOf(false) }
    var showFileManagerPicker by rememberSaveable { mutableStateOf(false) }
    var browseDir by remember { mutableStateOf(afftService.getInputDir()) }

    // Auto-detect file dari input/ saat screen dimuat (untuk menghindari copy ulang)
    LaunchedEffect(Unit) {
        val latestFile = workspace.latestInputFor("filesystem", afftService.getInputDir())
        if (latestFile != null) {
            selectedInputFilePath = latestFile.absolutePath
            selectedFileName = latestFile.name
            selectedUri = null
        }
    }

    // Analisis filesystem image via WorkspaceCoordinator (metadata superblock saja).
    LaunchedEffect(selectedInputFile) {
        if (selectedInputFile == null) {
            workspace.clearFileSelection()
            return@LaunchedEffect
        }
        workspace.analyze(selectedInputFile)
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let {
                selectedUri = it
                selectedInputFilePath = null
                try {
                    context.contentResolver.query(it, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) selectedFileName = c.getString(nameIdx)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FilesystemScreen", "Query failed: ${e.message}")
                    selectedFileName = it.lastPathSegment
                }
                scope.launch {
                    selectedInputFilePath = afftService.copyPickedFileToInput(it)?.absolutePath
                }
            }
        }

    LaunchedEffect(Unit) {
        availableDirs = afftService.listContentsDirs()
    }

    // ── Source Selector Dialog ──
    if (showSourceSelector) {
        FileSourceSelectorDialog(
            onPickFromStorage = {
                filePicker.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onPickFromWorkspace = {
                browseDir = afftService.getInputDir()
                showWorkspaceBrowser = true
            },
            onPickFromFileManager = {
                showFileManagerPicker = true
            },
            onDismiss = { showSourceSelector = false },
        )
    }

    // ── Repack Source Browser Dialog ──
    if (showRepackSourceBrowser) {
        WorkspaceFileBrowserDialog(
            title = "Pilih folder sumber filesystem",
            currentDir = repackBrowseDir,
            onNavigate = { dir -> repackBrowseDir = dir },
            onFileSelected = { file ->
                repackSourcePath = file.parentFile?.absolutePath ?: file.absolutePath
                showRepackSourceBrowser = false
            },
            onDismiss = { showRepackSourceBrowser = false },
        )
    }

    // ── Workspace Browser Dialog ──
    if (showWorkspaceBrowser) {
        WorkspaceFileBrowserDialog(
            title = "Pilih filesystem .img",
            currentDir = browseDir,
            onNavigate = { dir -> browseDir = dir },
            onFileSelected = { file ->
                selectedInputFilePath = file.absolutePath
                selectedFileName = file.name
                selectedUri = null
                showWorkspaceBrowser = false
            },
            onDismiss = { showWorkspaceBrowser = false },
        )
    }

    // ── File Manager Picker Dialog ──
    if (showFileManagerPicker) {
        FileManagerPickerDialog(
            initialDir = afftService.getInputDir(),
            title = "Pilih file filesystem dari File Manager",
            onFileSelected = { file ->
                selectedInputFilePath = file.absolutePath
                selectedFileName = file.name
                selectedUri = null
                showFileManagerPicker = false
            },
            onDismiss = { showFileManagerPicker = false },
        )
    }

    Column(
        modifier =
                        Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            "Filesystem Operations",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = LocalFontFamily.current,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Extract & repack EROFS/ext4 filesystem images",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Extract section
        Text("Extract Filesystem", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        FilePickerCard(
            title = "Pilih filesystem .img",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = { showSourceSelector = true },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LiveStatusCard(
            activity = liveActivity,
            idleText = "Idle — pilih image filesystem untuk memulai",
            busy = isRunning || isFsLoading || progressMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Firmware inspector — metadata filesystem dari Workspace (analisis superblock)
        FirmwareInspector(
            metadata =
                when {
                    selectedInputFile == null -> emptyFirmwareMetadata()
                    isFsLoading -> FirmwareMetadata(isLoading = true)
                    else -> fsInspectorMetadata ?: emptyFirmwareMetadata()
                },
            emptyTitle = "No filesystem image selected",
            emptyDescription = "Select a raw EROFS, EXT4, or F2FS image to begin analysis",
            emptyIconRes = R.drawable.ic_filesystem,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        val result = afftService.extractFilesystem(file)
                        if (result.ok) {
                            availableDirs = afftService.listContentsDirs()
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        val result = afftService.extractFilesystem(uri)
                        if (result.ok) {
                            availableDirs = afftService.listContentsDirs()
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_unarchive), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Filesystem")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Repack section
        Text("Repack Filesystem", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (availableDirs.isEmpty()) {
            Text(
                "Belum ada konten untuk direpack. Extract filesystem terlebih dahulu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = selectedDir ?: "Pilih direktori",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    availableDirs.forEach { dir ->
                        DropdownMenuItem(
                            text = { Text(dir) },
                            onClick = {
                                selectedDir = dir
                                expanded = false
                            },
                        )
                    }
                }
            }

            // Custom source path
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_folder_open),
                        null,
                        tint = LocalIconTint.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Source folder",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            repackSourcePath ?: "temp/contents/ (default)",
                            fontFamily = LocalFontFamily.current,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = {
                        repackBrowseDir = repackSourcePath?.let { File(it) }
                            ?: afftService.getTempDir()
                        showRepackSourceBrowser = true
                    }) { Text("Browse", fontSize = 11.sp) }
                    if (repackSourcePath != null) {
                        IconButton(
                            onClick = { repackSourcePath = null },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_clear), null, tint = LocalIconTint.current)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Show folder contents preview
            val contentsDir = File(afftService.getTempDir(), "contents")
            val previewFiles =
                remember(selectedDir) {
                    if (selectedDir != null) {
                        val dir = File(contentsDir, selectedDir!!)
                        if (dir.exists()) {
                            val all = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
                            all.take(10) // preview first 10
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
            val totalItems =
                remember(selectedDir) {
                    if (selectedDir != null) {
                        val dir = File(contentsDir, selectedDir!!)
                        if (dir.exists()) dir.listFiles()?.size ?: 0 else 0
                    } else {
                        0
                    }
                }

            if (selectedDir != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Isi folder $selectedDir/ ($totalItems item)",
                            fontSize = 11.sp,
                            fontFamily = LocalFontFamily.current,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        previewFiles.forEach { f ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(f.name, fontSize = 11.sp, fontFamily = LocalFontFamily.current)
                                if (f.isFile) {
                                    Text(
                                        formatFileSize(f.length()),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (totalItems > 10) {
                            Text(
                                "...dan ${totalItems - 10} lainnya",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    selectedDir?.let { dir ->
                        scope.launch {
                            val result =
                                afftService.repackFilesystem(
                                    dir,
                                    customSourceDir = repackSourcePath,
                                )
                        }
                    }
                },
                enabled = selectedDir != null && !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = LocalIconTint.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Repack $selectedDir")
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true)
        }

        // Bottom spacing agar tombol/isi terakhir tidak menempel bottom nav
        Spacer(modifier = Modifier.height(24.dp))
    }
}