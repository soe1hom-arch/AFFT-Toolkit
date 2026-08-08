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
import com.afft.app.core.coordinator.WorkspaceCoordinator
import com.afft.app.model.BootImageType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootScreen(
    workspace: WorkspaceCoordinator,
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedBootType by rememberSaveable { mutableStateOf<BootImageType?>(null) }
    var selectedInputFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedInputFile: File? = selectedInputFilePath?.let { path -> File(path) }
    val workspaceState by workspace.state.collectAsState()
    val progressMessage by afftService.progressMessage.collectAsState()
    val isBootLoading = workspaceState.isAnalyzing && workspaceState.currentFile == selectedInputFile?.name
    val bootInspectorMetadata = workspaceState.firmwareMetadata
    val doneMessage = rememberDoneMessage(progressMessage, isRunning)
    val liveActivity =
        when {
            isRunning -> progressMessage.ifBlank { "Memproses boot image..." }
            doneMessage != null -> doneMessage
            isBootLoading -> "Menganalisis ${selectedInputFile?.name ?: "boot image"}..."
            progressMessage.isNotBlank() -> progressMessage
            else -> null
        }
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
        val latestFile = workspace.latestInputFor("boot", afftService.getInputDir())
        if (latestFile != null) {
            selectedInputFilePath = latestFile.absolutePath
            selectedFileName = latestFile.name
            selectedUri = null
        }
    }

    // Analisis boot image via WorkspaceCoordinator (membaca metadata header saja).
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
                            if (nameIdx >= 0) {
                                selectedFileName = c.getString(nameIdx)
                                selectedBootType =
                                    BootImageType.entries.find { type ->
                                        type.fileName.equals(c.getString(nameIdx), ignoreCase = true)
                                    }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("BootScreen", "Query failed: ${e.message}")
                    selectedFileName = it.lastPathSegment
                }
                scope.launch {
                    selectedInputFilePath = afftService.copyPickedFileToInput(it)?.absolutePath
                }
            }
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
            title = "Pilih folder sumber boot",
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
            title = "Pilih boot image",
            currentDir = browseDir,
            onNavigate = { dir -> browseDir = dir },
            onFileSelected = { file ->
                selectedInputFilePath = file.absolutePath
                selectedFileName = file.name
                selectedUri = null
                selectedBootType =
                    BootImageType.entries.find { type ->
                        type.fileName.equals(file.name, ignoreCase = true) ||
                            file.name.equals(type.fileName, ignoreCase = true)
                    }
                showWorkspaceBrowser = false
            },
            onDismiss = { showWorkspaceBrowser = false },
        )
    }

    // ── File Manager Picker Dialog ──
    if (showFileManagerPicker) {
        FileManagerPickerDialog(
            initialDir = afftService.getInputDir(),
            title = "Pilih boot image dari File Manager",
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
            "Boot Family Operations",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = LocalFontFamily.current,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Unpack & repack boot images (boot, vendor_boot, init_boot, dtbo, recovery, vbmeta)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Pilih tipe boot:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BootImageType.entries.forEach { type ->
                FilterChip(
                    selected = selectedBootType == type,
                    onClick = { selectedBootType = type },
                    label = { Text(type.displayName) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilePickerCard(
            title = "Pilih file boot image",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = { showSourceSelector = true },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LiveStatusCard(
            activity = liveActivity,
            idleText = "Idle — pilih boot image untuk memulai",
            busy = isRunning || isBootLoading || progressMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Firmware inspector — metadata boot dari Workspace (analisis header nyata)
        FirmwareInspector(
            metadata =
                when {
                    selectedInputFile == null -> emptyFirmwareMetadata()
                    isBootLoading -> FirmwareMetadata(isLoading = true)
                    else -> bootInspectorMetadata ?: emptyFirmwareMetadata()
                },
            emptyTitle = "No boot image selected",
            emptyDescription = "Select a boot.img or vendor_boot.img file to begin analysis",
            emptyIconRes = R.drawable.ic_boot_image,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    selectedInputFile?.let { file ->
                        selectedBootType?.let { type ->
                            scope.launch {
                                afftService.unpackBoot(file, type.fileName)
                            }
                        }
                    } ?: selectedUri?.let { uri ->
                        selectedBootType?.let { type ->
                            scope.launch {
                                afftService.unpackBoot(uri, type.fileName)
                            }
                        }
                    }
                },
                enabled = (selectedUri != null || selectedInputFile != null) && selectedBootType != null && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_unarchive), null, tint = LocalIconTint.current)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Unpack")
            }

            Button(
                onClick = {
                    selectedBootType?.let { type ->
                        scope.launch {
                            afftService.repackBoot(type.fileName, customSourceDir = repackSourcePath)
                        }
                    }
                },
                enabled = selectedBootType != null && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = LocalIconTint.current)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Repack")
            }
        }

        // Custom source card
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        repackSourcePath ?: "boot_out/<type>_out/ (default)",
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

        // Preview extracted files if available
        val bootOutDir =
            selectedBootType?.let {
                File(afftService.getTempDir(), "boot_out/${it.fileName}_out")
            }
        val bootFiles =
            remember(bootOutDir, selectedBootType) {
                if (bootOutDir != null && bootOutDir.exists()) {
                    bootOutDir
                        .listFiles()
                        ?.filter { it.isFile && it.name != selectedBootType?.fileName }
                        ?.sortedBy { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            }

        if (bootFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Extracted files — ${bootOutDir?.name}",
                        fontSize = 11.sp,
                        fontFamily = LocalFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    bootFiles.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(f.name, fontSize = 11.sp, fontFamily = LocalFontFamily.current)
                            Text(
                                formatFileSize(f.length()),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true, message = "Processing boot image...")
        }

        // Bottom spacing agar tombol/isi terakhir tidak menempel bottom nav
        Spacer(modifier = Modifier.height(24.dp))
    }
}