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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.R
import com.afft.app.core.coordinator.WorkspaceCoordinator
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
import com.afft.app.util.partitionListSaver
import com.afft.app.util.safTreeToFile
import com.afft.app.util.stringSetSaver
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.io.File

@Composable
fun SuperScreen(
    workspace: WorkspaceCoordinator,
    afftService: AFFTService,
    logs: List<String>,
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var repackResult by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedInputFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedInputFile: File? = selectedInputFilePath?.let { path -> File(path) }
    val workspaceState by workspace.state.collectAsState()
    val progressMessage by afftService.progressMessage.collectAsState()
    val isSuperLoading = workspaceState.isAnalyzing && workspaceState.currentFile == selectedInputFile?.name
    val superInspectorMetadata = workspaceState.firmwareMetadata
    val doneMessage = rememberDoneMessage(progressMessage, isRunning)
    val liveActivity =
        when {
            isRunning -> progressMessage.ifBlank { "Memproses super.img..." }
            doneMessage != null -> doneMessage
            isSuperLoading -> "Menganalisis ${selectedInputFile?.name ?: "super.img"}..."
            progressMessage.isNotBlank() -> progressMessage
            else -> null
        }

    // Dialogs state
    var showSourceSelector by rememberSaveable { mutableStateOf(false) }
    var showWorkspaceBrowser by rememberSaveable { mutableStateOf(false) }
    var showFileManagerPicker by rememberSaveable { mutableStateOf(false) }
    var browseDir by remember { mutableStateOf(afftService.getInputDir()) }

    // Custom source folder for repack
    var repackSourcePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showRepackSourceBrowser by rememberSaveable { mutableStateOf(false) }
    var repackBrowseDir by remember { mutableStateOf(afftService.getTempDir()) }

    // Partition selection for repack
    var partitions by rememberSaveable(stateSaver = partitionListSaver) {
        mutableStateOf<List<Pair<String, Long>>>(emptyList())
    }
    var selectedPartitions by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf<Set<String>>(emptySet()) }
    var showPartitionSelector by rememberSaveable { mutableStateOf(false) }

    // Refresh partition list after unpack
    fun refreshPartitionsFrom(dir: File?) {
        val parts = dir?.partitionEntries() ?: emptyList()
        partitions = parts
        selectedPartitions = parts.map { it.first }.toSet()
    }

    LaunchedEffect(repackResult) {
        if (repackResult?.startsWith("Unpack") == true) {
            refreshPartitionsFrom(File(afftService.getTempDir(), "img"))
        }
    }

    // Auto-detect file dari input/ saat screen dimuat (untuk menghindari copy ulang)
    // Load latest input file AND existing partitions on init
    LaunchedEffect(Unit) {
        val latestFile = workspace.latestInputFor("super", afftService.getInputDir())
        if (latestFile != null) {
            selectedInputFilePath = latestFile.absolutePath
            selectedFileName = latestFile.name
            selectedUri = null
        }
        val imgDir = File(afftService.getTempDir(), "img")
        if (imgDir.exists()) {
            refreshPartitionsFrom(imgDir)
        }
    }

    // Analisis super.img via WorkspaceCoordinator (metadata logical partition saja).
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
                    android.util.Log.w("SuperScreen", "Query failed: ${e.message}")
                    selectedFileName = it.lastPathSegment
                }
                // Auto-copy picked file to input/ directory dan simpan referensi lokal
                scope.launch {
                    selectedInputFilePath = afftService.copyPickedFileToInput(it)?.absolutePath
                }
            }
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
                    refreshPartitionsFrom(folder)
                } else {
                    Toast.makeText(
                        context,
                        "Folder tidak didukung — gunakan folder di penyimpanan internal atau Browser Folder",
                        Toast.LENGTH_LONG,
                    ).show()
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
            title = "Pilih folder sumber partisi",
            currentDir = repackBrowseDir,
            onNavigate = { dir -> repackBrowseDir = dir },
            onFileSelected = { file ->
                repackSourcePath = file.parentFile?.absolutePath ?: file.absolutePath
                refreshPartitionsFrom(repackSourcePath?.let { File(it) })
                showRepackSourceBrowser = false
            },
            onDismiss = { showRepackSourceBrowser = false },
            selectFolderMode = true,
            onFolderSelected = { folder ->
                repackSourcePath = folder.absolutePath
                refreshPartitionsFrom(folder)
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
            title = "Pilih super.img",
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
            title = "Pilih super.img dari File Manager",
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
            iconRes = R.drawable.ic_super,
            title = "Super Image Operations",
            subtitle = "Unpack & repack super.img logical partitions",
            status =
                when {
                    isRunning -> StatusType.RUNNING to "PROCESSING"
                    doneMessage != null || repackResult != null -> StatusType.READY to "DONE"
                    else -> null
                },
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 1: Pilih & Analisis ──
        StepSectionTitle(
            step = "01",
            title = "Pilih & Analisis",
            description = "Pilih super.img untuk melihat metadata partisi",
        )
        Spacer(modifier = Modifier.height(10.dp))

        FilePickerCard(
            title = "Pilih super.img",
            selectedUri = if (selectedInputFile != null) null else selectedUri,
            selectedFileName = selectedFileName,
            onClick = { showSourceSelector = true },
        )

        Spacer(modifier = Modifier.height(8.dp))

        LiveStatusCard(
            activity = liveActivity,
            idleText = "Idle — pilih super.img untuk memulai",
            busy = isRunning || isSuperLoading || progressMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Firmware inspector — metadata super dari Workspace (analisis header nyata)
        FirmwareInspector(
            metadata =
                when {
                    selectedInputFile == null -> emptyFirmwareMetadata()
                    isSuperLoading -> FirmwareMetadata(isLoading = true)
                    else -> superInspectorMetadata ?: emptyFirmwareMetadata()
                },
            emptyTitle = "No super image selected",
            emptyDescription = "Select a super.img file to begin analysis",
            emptyIconRes = R.drawable.ic_super,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 2: Unpack ──
        StepSectionTitle(
            step = "02",
            title = "Unpack",
            description = "Ekstrak partisi logical ke temp/img/",
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                selectedInputFile?.let { file ->
                    scope.launch {
                        val result = afftService.unpackSuper(file)
                        if (result.ok) {
                            repackResult = "Unpack selesai. Partisi di temp/img/"
                        }
                    }
                } ?: selectedUri?.let { uri ->
                    scope.launch {
                        val result = afftService.unpackSuper(uri)
                        if (result.ok) {
                            repackResult = "Unpack selesai. Partisi di temp/img/"
                        }
                    }
                }
            },
            enabled = (selectedUri != null || selectedInputFile != null) && !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_unarchive), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unpack Super")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Langkah 3: Repack ──
        StepSectionTitle(
            step = "03",
            title = "Repack",
            description = "Pilih folder sumber partisi dari mana saja, lalu repack",
        )
        Spacer(modifier = Modifier.height(10.dp))

        RepackSourceCard(
            selectedPath = repackSourcePath,
            defaultHint = "temp/img/ (default)",
            onBrowse = {
                repackBrowseDir = repackSourcePath?.let { File(it) } ?: afftService.getTempDir()
                showRepackSourceBrowser = true
            },
            onPickSystemFolder = { folderPicker.launch(null) },
            onClear = {
                repackSourcePath = null
                refreshPartitionsFrom(File(afftService.getTempDir(), "img"))
            },
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (partitions.isNotEmpty()) {
                    showPartitionSelector = true
                } else {
                    scope.launch {
                        val result =
                            afftService.repackSuper(
                                customSourceDir = repackSourcePath,
                            )
                        repackResult =
                            if (result.ok) {
                                "Repack selesai: temp/repacked/super_repack.img"
                            } else {
                                "Repack gagal"
                            }
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = LocalIconTint.current)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (partitions.isNotEmpty()) {
                    "Repack Super (${partitions.size} partisi)"
                } else {
                    "Repack Super"
                },
            )
        }

        if (isRunning) {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingOverlay(isRunning = true)
        }

        repackResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, fontFamily = LocalFontFamily.current, color = MaterialTheme.colorScheme.primary)
        }

        // Bottom spacing agar tombol/isi terakhir tidak menempel bottom nav
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Partition selector dialog
    if (showPartitionSelector && partitions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPartitionSelector = false },
            icon = { Icon(painterResource(R.drawable.ic_list), null, tint = LocalIconTint.current) },
            title = { Text("Pilih Partisi untuk Repack") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${partitions.size} partisi ditemukan:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = {
                            selectedPartitions = partitions.map { it.first }.toSet()
                        }) { Text("Select All", fontSize = 11.sp) }
                        TextButton(onClick = {
                            selectedPartitions = emptySet()
                        }) { Text("Clear", fontSize = 11.sp) }
                    }

                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(scrollState)) {
                        partitions.forEach { (name, size) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedPartitions.contains(name),
                                    onCheckedChange = {
                                        selectedPartitions =
                                            if (it) {
                                                selectedPartitions + name
                                            } else {
                                                selectedPartitions - name
                                            }
                                    },
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    name,
                                    fontFamily = LocalFontFamily.current,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatFileSize(size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPartitionSelector = false
                        scope.launch {
                            val result =
                                afftService.repackSuper(
                                    selectedPartitions.toList().map { "$it.img" },
                                    customSourceDir = repackSourcePath,
                                )
                            repackResult =
                                if (result.ok) {
                                    "Repack selesai: temp/repacked/super_repack.img"
                                } else {
                                    "Repack gagal"
                                }
                        }
                    },
                    enabled = selectedPartitions.isNotEmpty(),
                ) { Text("Repack (${selectedPartitions.size})") }
            },
            dismissButton = {
                TextButton(onClick = { showPartitionSelector = false }) { Text("Batal") }
            },
        )
    }
}

/** Daftar partisi (.img) dalam sebuah folder, tanpa super.img/super_raw.img. */
private fun File.partitionEntries(): List<Pair<String, Long>> =
    listFiles()
        ?.filter { it.isFile }
        ?.filter { it.name !in setOf("super.img", "super_raw.img") }
        ?.sortedBy { it.name }
        ?.map { it.nameWithoutExtension to it.length() }
        ?: emptyList()
