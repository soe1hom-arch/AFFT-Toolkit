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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.QuickLocation
import com.afft.app.ui.components.RepackSourceCard
import com.afft.app.ui.components.ScreenHeader
import com.afft.app.ui.components.StepSectionTitle
import com.afft.app.ui.components.WorkspaceFileBrowserDialog
import com.afft.app.ui.components.dashboard.FirmwareInspector
import com.afft.app.ui.components.dashboard.FirmwareMetadata
import com.afft.app.ui.components.dashboard.StatusType
import com.afft.app.ui.components.dashboard.emptyFirmwareMetadata
import com.afft.app.ui.components.rememberDoneMessage
import com.afft.app.util.formatFileSize
import com.afft.app.util.safTreeToFile
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
        val latestFile =
            workspace.resumeInputFor("boot", afftService.getInputDir())
                ?: workspace.latestInputFor("boot", afftService.getInputDir())
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
            targetLabel = "boot image",
        )
    }

    // Sistem folder picker (SAF) untuk sumber repack dari folder bebas
    val folderPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            uri?.let {
                val folder = safTreeToFile(context, it)
                if (folder != null) {
                    repackSourcePath = folder.absolutePath
                } else {
                    Toast.makeText(
                        context,
                        "Folder tidak didukung — gunakan folder di penyimpanan internal atau Browser Folder",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
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
            selectFolderMode = true,
            onFolderSelected = { folder ->
                repackSourcePath = folder.absolutePath
                showRepackSourceBrowser = false
            },
            quickLocations =
                listOf(
                    QuickLocation("Folder Kerja", R.drawable.ic_folder, afftService.getTempDir()),
                ),
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
        ScreenHeader(
            iconRes = R.drawable.ic_boot_image,
            title = "Boot Family Operations",
            subtitle = "Unpack & repack 7 boot images (boot, vendor_boot, init_boot, dtbo, recovery, vbmeta, vendor_kernel_boot)",
            status =
                when {
                    isRunning -> StatusType.RUNNING to "PROCESSING"
                    doneMessage != null -> StatusType.READY to "DONE"
                    else -> null
                },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 1: Pilih Tipe & File ──
        StepSectionTitle(
            step = "01",
            title = "Pilih Tipe & File",
            description = "Pilih tipe boot lalu pilih image-nya",
        )
        Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 2: Unpack ──
        StepSectionTitle(
            step = "02",
            title = "Unpack",
            description = "Ekstrak boot image ke boot_out/<type>_out/",
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    selectedBootType?.let { type ->
                        scope.launch {
                            val t0 = System.currentTimeMillis()
                            val result = afftService.unpackBoot(file, type.fileName)
                            workspace.recordOperation(
                                title = "Unpack Boot",
                                ok = result.ok,
                                durationMillis = System.currentTimeMillis() - t0,
                                detail = result.message,
                                resumeTool = "boot",
                                resumeStep = if (result.ok) "unpacked" else null,
                                resumeFile = selectedFileName,
                            )
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    selectedBootType?.let { type ->
                        scope.launch {
                            val t0 = System.currentTimeMillis()
                            val result = afftService.unpackBoot(uri, type.fileName)
                            workspace.recordOperation(
                                title = "Unpack Boot",
                                ok = result.ok,
                                durationMillis = System.currentTimeMillis() - t0,
                                detail = result.message,
                                resumeTool = "boot",
                                resumeStep = if (result.ok) "unpacked" else null,
                                resumeFile = selectedFileName,
                            )
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && selectedBootType != null && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_unarchive), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unpack ${selectedBootType?.displayName ?: "Boot"}")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 3: Repack ──
        StepSectionTitle(
            step = "03",
            title = "Repack",
            description = "Pilih folder sumber dari mana saja, lalu repack",
        )
        Spacer(modifier = Modifier.height(10.dp))

        RepackSourceCard(
            selectedPath = repackSourcePath,
            defaultHint = "boot_out/<type>_out/ (default)",
            onBrowse = {
                repackBrowseDir = repackSourcePath?.let { File(it) } ?: afftService.getTempDir()
                showRepackSourceBrowser = true
            },
            onPickSystemFolder = { folderPicker.launch(null) },
            onClear = { repackSourcePath = null },
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                selectedBootType?.let { type ->
                    scope.launch {
                        val t0 = System.currentTimeMillis()
                        val result =
                            afftService.repackBoot(type.fileName, customSourceDir = repackSourcePath)
                        workspace.recordOperation(
                            title = "Repack Boot",
                            ok = result.ok,
                            durationMillis = System.currentTimeMillis() - t0,
                            detail = result.message,
                            resumeTool = "boot",
                            resumeStep = if (result.ok) "repacked" else null,
                            resumeFile = selectedFileName,
                        )
                    }
                }
            },
            enabled = selectedBootType != null && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Repack ${selectedBootType?.displayName ?: "Boot"}")
        }

        // Preview extracted files if available
        val bootOutDir =
            repackSourcePath?.let { File(it) }
                ?: selectedBootType?.let {
                    File(afftService.getTempDir(), "boot_out/${it.fileName}_out")
                }
        val bootFiles =
            remember(bootOutDir, selectedBootType) {
                if (bootOutDir != null && bootOutDir.exists()) {
                    bootOutDir
                        .listFiles()
                        ?.filter {
                            it.isFile && it.name != selectedBootType?.fileName && it.name != "new-boot.img"
                        }
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
                        "Isi folder — ${bootOutDir?.name ?: ""}",
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
