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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
    var showSize by rememberSaveable { mutableStateOf(true) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
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
    var showSortMenu by remember { mutableStateOf(false) }
    var listError by remember { mutableStateOf<String?>(null) }
    var listLoading by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableIntStateOf(0) }

    val inputDir = afftService.getInputDir()
    val downloadDir = afftService.getExportDir()
    // Show toast
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    suspend fun refreshFiles(dir: File) {
        val version = ++refreshVersion
        val query = searchQuery
        val mode = sortMode
        val ascending = sortAsc
        currentDir = dir
        listLoading = true
        listError = null
        files = emptyList()
        selectedFiles = emptySet()
        selectMode = false
        val (list, error) =
            withContext(Dispatchers.IO) {
                val entries = dir.listFiles()
                val rawList = entries?.toList() ?: emptyList()
                val filtered =
                    if (query.isBlank()) {
                        rawList
                    } else {
                        rawList.filter { it.name.lowercase().contains(query.lowercase()) }
                    }

                val comparator =
                    when (mode) {
                        "date" -> compareBy<File> { it.lastModified() }
                        "size" -> compareBy<File> { it.length() }
                        "type" -> compareBy<File> { it.extension.lowercase() }
                        else -> compareBy<File> { it.name.lowercase() }
                    }
                val ordered = if (ascending) comparator else comparator.reversed()
                val list = filtered.sortedWith(compareBy<File> { !it.isDirectory }.then(ordered).thenBy { it.name.lowercase() })
                list to if (entries == null) "Folder tidak dapat dibaca atau tidak tersedia." else null
            }
        if (version == refreshVersion) {
            files = list
            listError = error
            listLoading = false
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

    fun navigateTo(dir: File, keepCurrentInHistory: Boolean = true) {
        currentDir?.let { current ->
            if (keepCurrentInHistory && current != dir) pathHistory = pathHistory + current
        }
        searchQuery = ""
        showSearch = false
        scope.launch { refreshFiles(dir) }
    }

    fun navigateBack() {
        val previous = pathHistory.last()
        pathHistory = pathHistory.dropLast(1)
        searchQuery = ""
        showSearch = false
        scope.launch { refreshFiles(previous) }
    }

    LaunchedEffect(Unit) {
        refreshFiles(inputDir)
    }

    BackHandler(enabled = selectMode || showSearch || pathHistory.isNotEmpty()) {
        when {
            selectMode -> {
                selectedFiles = emptySet()
                selectMode = false
            }
            showSearch -> {
                showSearch = false
                searchQuery = ""
                scope.launch { currentDir?.let { refreshFiles(it) } }
            }
            pathHistory.isNotEmpty() -> navigateBack()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_folder_open),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (selectMode) "${selectedFiles.size} dipilih" else "AFFT Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = LocalFontFamily.current,
                    )
                    if (!selectMode) {
                        Text(
                            "File dan hasil firmware",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (selectMode) {
                TextButton(onClick = {
                    selectedFiles = emptySet()
                    selectMode = false
                }) {
                    Text("Batal", fontSize = 12.sp)
                }
            } else {
                IconButton(onClick = {
                    showSearch = !showSearch
                    if (!showSearch && searchQuery.isNotEmpty()) {
                        searchQuery = ""
                        scope.launch { currentDir?.let { refreshFiles(it) } }
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_search), "Cari", tint = LocalIconTint.current)
                }
                IconButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !operationInProgress,
                ) {
                    Icon(painterResource(R.drawable.ic_file_open), "Impor file", tint = LocalIconTint.current)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick access locations
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val locations = listOf(
                Triple("Penyimpanan", R.drawable.ic_phone_android, File("/storage/emulated/0")),
                Triple("Filesystem", R.drawable.ic_folder, afftService.getFilesystemContentsDir()),
                Triple("Input", R.drawable.ic_drive_file_move, inputDir),
                Triple("Downloads", R.drawable.ic_download, downloadDir),
            )
            locations.forEach { (label, icon, dir) ->
                val currentPath = currentDir?.absoluteFile?.normalize()?.path
                val locationPath = dir.absoluteFile.normalize().path
                val active = currentPath == locationPath ||
                    currentPath?.startsWith(locationPath.trimEnd(File.separatorChar) + File.separator) == true
                FilterChip(
                    selected = active,
                    onClick = {
                        pathHistory = emptyList()
                        navigateTo(dir, keepCurrentInHistory = false)
                    },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(painterResource(icon), null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Current folder card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        currentDir?.parentFile?.let { navigateTo(it) }
                    },
                    enabled = currentDir?.parentFile != null && !operationInProgress,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_back), "Folder sebelumnya", tint = LocalIconTint.current)
                }
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        currentDir?.name?.ifBlank { "Penyimpanan" } ?: "Memuat folder...",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        currentDir?.absolutePath.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { scope.launch { currentDir?.let { refreshFiles(it) } } },
                    enabled = !operationInProgress,
                ) {
                    Icon(painterResource(R.drawable.ic_refresh), "Muat ulang", tint = LocalIconTint.current)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (listLoading) "Memuat..." else if (listError != null) "Tidak dapat membaca folder" else "${files.size} item",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    val label = when (sortMode) {
                        "date" -> "Tanggal"
                        "size" -> "Ukuran"
                        "type" -> "Jenis"
                        else -> "Nama"
                    }
                    Text("Urut: $label ${if (sortAsc) "↑" else "↓"}")
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf("name" to "Nama", "date" to "Tanggal", "size" to "Ukuran", "type" to "Jenis")
                        .forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sortAsc = if (sortMode == mode) !sortAsc else true
                                    sortMode = mode
                                    showSortMenu = false
                                    scope.launch { currentDir?.let { refreshFiles(it) } }
                                },
                            )
                        }
                }
            }
            IconButton(
                onClick = {
                    newFolderName = ""
                    showCreateFolderDialog = true
                },
                enabled = !operationInProgress,
            ) {
                Icon(painterResource(R.drawable.ic_create_new_folder), "Folder baru", tint = LocalIconTint.current)
            }
        }

        // Search bar
        if (showSearch) {
            Spacer(modifier = Modifier.height(8.dp))
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
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                                    navigateTo(file)
                                }
                            },
                            onLongClick = {
                                toggleSelectFile(file)
                            },
                            showSize = showSize,
                        )
                        if (file != files.lastOrNull()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = if (selectMode) 52.dp else 48.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
                if (files.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (listLoading) "Memuat..." else listError
                                    ?: if (searchQuery.isNotBlank()) "Tidak ada hasil pencarian" else "Folder kosong",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Contextual selection actions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectMode) "${selectedFiles.size} dipilih" else "Tekan lama file untuk memilih",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectMode) {
                        TextButton(
                            onClick = {
                                selectedFiles = if (selectedFiles.size == files.size) emptySet() else files.toSet()
                                selectMode = selectedFiles.isNotEmpty()
                            },
                        ) {
                            Text(if (selectedFiles.size == files.size) "Batal pilih semua" else "Pilih semua")
                        }
                    } else {
                        TextButton(onClick = { showSize = !showSize }) {
                            Text(if (showSize) "Sembunyikan ukuran" else "Ukuran")
                        }
                    }
                }
                if (selectMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectedFiles.size == 1) {
                            FilledTonalButton(
                                onClick = {
                                    renameTarget = selectedFiles.first()
                                    renameText = renameTarget?.name.orEmpty()
                                    showRenameDialog = true
                                },
                                enabled = !operationInProgress,
                            ) {
                                Icon(painterResource(R.drawable.ic_edit), null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ubah nama")
                            }
                            FilledTonalButton(
                                onClick = {
                                    showPropertiesFor = selectedFiles.first()
                                    showPropertiesDialog = true
                                },
                            ) {
                                Icon(painterResource(R.drawable.ic_info), null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Detail")
                            }
                        }
                        FilledTonalButton(
                            onClick = { showCopyDestDialog = true },
                            enabled = !operationInProgress,
                        ) {
                            Icon(painterResource(R.drawable.ic_content_copy), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Salin")
                        }
                        FilledTonalButton(
                            onClick = { showMoveDestDialog = true },
                            enabled = !operationInProgress,
                        ) {
                            Icon(painterResource(R.drawable.ic_drive_file_move), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pindah")
                        }
                        FilledTonalButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !operationInProgress,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hapus")
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
                            toastMessage = "Salin: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Salin: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Salin: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Pindah: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Pindah: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Pindah: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
                            toastMessage = "Pindah: $ok berhasil, ${selectedFiles.size - ok} gagal. Lihat konsol untuk detail."
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
    val isDirectory = file.isDirectory
    val isImage = file.extension.equals("img", ignoreCase = true)
    val isArchive = file.extension.equals("bin", ignoreCase = true) ||
        file.extension.equals("zip", ignoreCase = true)
    val iconRes = when {
        isDirectory -> R.drawable.ic_folder
        isImage -> R.drawable.ic_sd_storage
        isArchive -> R.drawable.ic_archive
        else -> R.drawable.ic_description
    }
    val iconTint = when {
        isDirectory -> MaterialTheme.colorScheme.primary
        isImage -> MaterialTheme.colorScheme.tertiary
        isArchive -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectMode || isSelected) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontFamily = LocalFontFamily.current,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 14.sp,
                    fontWeight = if (isDirectory) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isDirectory) {
                    Text(
                        text = "Folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (showSize) {
                    Text(
                        text = "${formatFileSize(file.length())} • ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(file.lastModified()))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (isDirectory && !selectMode) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
