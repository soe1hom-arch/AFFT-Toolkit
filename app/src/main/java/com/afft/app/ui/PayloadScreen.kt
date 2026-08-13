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
import android.provider.OpenableColumns
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
import com.afft.app.R
import com.afft.app.service.AFFTService
import com.afft.app.ui.components.FileManagerPickerDialog
import com.afft.app.ui.components.FilePickerCard
import com.afft.app.ui.components.FileSourceSelectorDialog
import com.afft.app.ui.components.LiveStatusCard
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.ScreenHeader
import com.afft.app.ui.components.StepSectionTitle
import com.afft.app.ui.components.WorkspaceFileBrowserDialog
import com.afft.app.ui.components.dashboard.FirmwareInspector
import com.afft.app.ui.components.dashboard.FirmwareMetadata
import com.afft.app.ui.components.dashboard.StatusType
import com.afft.app.ui.components.dashboard.emptyFirmwareMetadata
import com.afft.app.core.coordinator.WorkspaceCoordinator
import com.afft.app.ui.components.rememberDoneMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun PayloadScreen(
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
    val workspaceState by workspace.state.collectAsState()
    val inspectorMetadata = workspaceState.firmwareMetadata
    val selectedInputFile: File? = selectedInputFilePath?.let { path -> File(path) }
    val isInspectorLoading =
        workspaceState.isAnalyzing && workspaceState.currentFile == selectedInputFile?.name
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val progressMessage by afftService.progressMessage.collectAsState()
    val progressPercent by afftService.progressPercent.collectAsState()
    val currentPartition by afftService.currentPartition.collectAsState()
    val doneMessage = rememberDoneMessage(progressMessage, isRunning)
    val liveActivity =
        when {
            isRunning -> progressMessage.ifBlank { "Memproses payload.bin..." }
            doneMessage != null -> doneMessage
            isInspectorLoading -> "Menganalisis ${selectedInputFile?.name ?: "payload.bin"}..."
            progressMessage.isNotBlank() -> progressMessage
            else -> null
        }

    // Dialogs state
    var showSourceSelector by rememberSaveable { mutableStateOf(false) }
    var showWorkspaceBrowser by rememberSaveable { mutableStateOf(false) }
    var showFileManagerPicker by rememberSaveable { mutableStateOf(false) }
    var browseDir by remember { mutableStateOf(afftService.getInputDir()) }

    // Auto-detect file dari input/ saat screen dimuat (untuk menghindari copy ulang)
    LaunchedEffect(Unit) {
        val latestFile = workspace.latestInputFor("payload", afftService.getInputDir())
        if (latestFile != null) {
            selectedInputFilePath = latestFile.absolutePath
            selectedFileName = latestFile.name
            selectedUri = null
        }
    }

    // Analisis payload via WorkspaceCoordinator (single source of truth):
    // membuka/membuat proyek, menjalankan FirmwareAnalysisEngine, dan
    // menyinkronkan metadata/history/health ke Workspace.
    LaunchedEffect(selectedInputFile) {
        if (selectedInputFile == null) {
            workspace.clearFileSelection()
            return@LaunchedEffect
        }
        workspace.analyze(selectedInputFile)
    }

    // System file picker (dari penyimpanan perangkat)
    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            uri?.let {
                selectedUri = it
                selectedInputFilePath = null
                errorMessage = null
                try {
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                selectedFileName = c.getString(nameIdx)
                            }
                        }
                    } ?: run {
                        selectedFileName = it.lastPathSegment ?: "Unknown file"
                    }
                } catch (e: Exception) {
                    selectedFileName = it.lastPathSegment ?: "Unknown file"
                    android.util.Log.w("PayloadScreen", "File query error: ${e.message}")
                }
                // Auto-copy picked file to input/ directory dan simpan referensi lokal
                scope.launch {
                    try {
                        selectedInputFilePath = afftService.copyPickedFileToInput(it)?.absolutePath
                    } catch (_: CancellationException) {
                    }
                }
            } ?: run {
                errorMessage = "Tidak ada file dipilih"
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
            targetLabel = "payload.bin",
        )
    }

    // ── Workspace Browser Dialog ──
    if (showWorkspaceBrowser) {
        WorkspaceFileBrowserDialog(
            title = "Pilih payload.bin",
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
            title = "Pilih payload.bin dari File Manager",
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
            iconRes = R.drawable.ic_payload,
            title = "Extract payload.bin",
            subtitle = "Extract OTA firmware payload.bin files",
            status =
                when {
                    isRunning -> StatusType.RUNNING to "PROCESSING"
                    doneMessage != null -> StatusType.READY to "DONE"
                    else -> null
                },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 1: Pilih & Analisis ──
        StepSectionTitle(
            step = "01",
            title = "Pilih & Analisis",
            description = "Pilih payload.bin untuk melihat metadata firmware",
        )
        Spacer(modifier = Modifier.height(10.dp))

        FilePickerCard(
            title = "Pilih payload.bin",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = {
                errorMessage = null
                showSourceSelector = true
            },
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontFamily = LocalFontFamily.current)
        }

        Spacer(modifier = Modifier.height(8.dp))

        LiveStatusCard(
            activity = liveActivity,
            idleText = "Idle — pilih payload.bin untuk memulai",
            busy = isRunning || isInspectorLoading || progressMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Firmware inspector — data langsung dari Workspace (hasil analisis nyata)
        FirmwareInspector(
            metadata =
                when {
                    selectedInputFile == null -> emptyFirmwareMetadata()
                    isInspectorLoading -> FirmwareMetadata(isLoading = true)
                    else -> inspectorMetadata ?: emptyFirmwareMetadata()
                },
            emptyTitle = "No payload selected",
            emptyDescription = "Select a payload.bin file to begin analysis",
            emptyIconRes = R.drawable.ic_payload,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 2: Extract ──
        StepSectionTitle(
            step = "02",
            title = "Extract",
            description = "Ekstrak semua partisi dari payload.bin",
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        try {
                            errorMessage = null
                            val result = afftService.extractPayload(file)
                            if (!result.ok) {
                                errorMessage = result.message
                            }
                        } catch (_: CancellationException) {
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        try {
                            errorMessage = null
                            val result = afftService.extractPayload(uri)
                            if (!result.ok) {
                                errorMessage = result.message
                            }
                        } catch (_: CancellationException) {
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract payload.bin")
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(
                isRunning = true,
                message =
                    if (currentPartition.isNotEmpty()) {
                        "Extracting: $currentPartition"
                    } else {
                        "Extracting payload.bin..."
                    },
                progressPercent = if (progressPercent > 0) progressPercent.toFloat() else null,
            )
        }

        // Bottom spacing agar tombol/isi terakhir tidak menempel bottom nav
        Spacer(modifier = Modifier.height(24.dp))
    }
}
