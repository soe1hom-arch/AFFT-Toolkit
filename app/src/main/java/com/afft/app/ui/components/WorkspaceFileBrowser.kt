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
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.LocalIconTint

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afft.app.R
import com.afft.app.util.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceFileBrowserDialog(
    title: String,
    currentDir: File,
    onNavigate: (File) -> Unit,
    onFileSelected: (File) -> Unit,
    onDismiss: () -> Unit,
    selectFolderMode: Boolean = false,
    onFolderSelected: ((File) -> Unit)? = null,
    quickLocations: List<QuickLocation> = emptyList(),
) {
    val internalRoot = File("/storage/emulated/0")
    val downloadDir =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AFFT")

    val quickLocationsAll: List<QuickLocation> =
        remember(currentDir, quickLocations) {
            val defaults =
                listOfNotNull(
                    QuickLocation("Internal", R.drawable.ic_phone_android, internalRoot),
                    QuickLocation("Device", R.drawable.ic_sd_storage, File("/")),
                    if (downloadDir.exists()) QuickLocation("DL/AFFT", R.drawable.ic_download, downloadDir) else null,
                )
            (quickLocations + defaults).distinctBy { it.dir.absolutePath }
        }

    AppDialog(
        iconRes = R.drawable.ic_folder_open,
        title = title,
        subtitle = currentDir.absolutePath,
        onDismiss = onDismiss,
        content = {
            val files =
                remember(currentDir) {
                    currentDir
                        .listFiles()
                        ?.filter { it.isFile }
                        ?.sortedWith(compareBy<File> { it.extension }.thenBy { it.name.lowercase() })
                        ?: emptyList()
                }
            val dirs =
                remember(currentDir) {
                    currentDir
                        .listFiles()
                        ?.filter { it.isDirectory }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }

            Column {
                // ── Quick access locations ──
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    quickLocationsAll.forEach { loc ->
                        QuickAccessChip(
                            label = loc.label,
                            iconRes = loc.iconRes,
                            active = currentDir.absolutePath.startsWith(loc.dir.absolutePath),
                            onClick = { onNavigate(loc.dir) },
                        )
                    }
                }

                if (selectFolderMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (currentDir.canRead()) {
                        Button(
                            onClick = {
                                onFolderSelected?.invoke(currentDir)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_folder_open),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = LocalIconTint.current,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gunakan Folder Ini Sebagai Sumber Repack")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (dirs.isEmpty() && files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Folder kosong",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    ) {
                        // Parent directory navigation
                        if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            "../",
                                            fontFamily = LocalFontFamily.current,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            painterResource(R.drawable.ic_folder),
                                            null,
                                            tint = LocalIconTint.current,
                                        )
                                    },
                                    modifier =
                                        Modifier.clickable {
                                            currentDir.parentFile?.let { onNavigate(it) }
                                        },
                                )
                                HorizontalDivider()
                            }
                        }

                        // Directories
                        items(dirs) { dir ->
                            val itemCount =
                                remember(dir) {
                                    val children = dir.listFiles()
                                    if (children.isNullOrEmpty()) 0 else children.size
                                }
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "${dir.name}/",
                                        fontFamily = LocalFontFamily.current,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    if (itemCount > 0) {
                                        Text(
                                            "$itemCount item",
                                            fontFamily = LocalFontFamily.current,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                        )
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        painterResource(R.drawable.ic_folder),
                                        null,
                                        tint = LocalIconTint.current,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        null,
                                        tint = LocalIconTint.current,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                modifier = Modifier.clickable { onNavigate(dir) },
                            )
                            HorizontalDivider()
                        }

                        // Files — sembunyikan saat mode pilih folder agar tidak membingungkan
                        if (!selectFolderMode) {
                            items(files) { file ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            file.name,
                                            fontFamily = LocalFontFamily.current,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            formatFileSize(file.length()) + " · " + file.extension.uppercase(),
                                            fontFamily = LocalFontFamily.current,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            when (file.extension.lowercase()) {
                                                "img", "bin" -> painterResource(R.drawable.ic_disc_full)
                                                "zip", "gz", "xz" -> painterResource(R.drawable.ic_archive)
                                                "txt", "log" -> painterResource(R.drawable.ic_text_snippet)
                                                else -> painterResource(R.drawable.ic_insert_drive_file)
                                            },
                                            null,
                                            tint = LocalIconTint.current,
                                        )
                                    },
                                    modifier = Modifier.clickable { onFileSelected(file) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        footer = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
            if (selectFolderMode) {
                Button(
                    onClick = { onFolderSelected?.invoke(currentDir) },
                    enabled = currentDir.canRead(),
                ) {
                    Text("Pilih Folder Ini")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Tutup")
                }
            }
        },
    )
}

/** Lokasi pintasan untuk browser folder. */
data class QuickLocation(
    val label: String,
    val iconRes: Int,
    val dir: File,
)

@Composable
private fun QuickAccessChip(
    label: String,
    iconRes: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        leadingIcon = {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (active) LocalIconTint.current else LocalIconTint.current.copy(alpha = 0.7f),
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor =
                    if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    )
}

/**
 * Dialog awal untuk memilih sumber file:
 * 1. "Penyimpanan (Sistem)" → System file picker
 * 2. "File Manager" → browser file bawaan AFFT
 * 3. "Folder Kerja" → Workspace browser
 */
@Composable
fun FileSourceSelectorDialog(
    onPickFromStorage: () -> Unit,
    onPickFromWorkspace: () -> Unit,
    onPickFromFileManager: () -> Unit,
    onDismiss: () -> Unit,
    targetLabel: String? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(R.drawable.ic_file_open),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Pilih Sumber File",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = LocalFontFamily.current,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (targetLabel != null) {
                                "Pilih $targetLabel dari salah satu lokasi berikut"
                            } else {
                                "Pilih dari salah satu lokasi berikut"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = LocalFontFamily.current,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = "Tutup",
                            tint = LocalIconTint.current,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Opsi sumber ──
                DialogOptionCard(
                    iconRes = R.drawable.ic_storage,
                    title = "Penyimpanan (Sistem)",
                    description = "Dialog file Android — bebas akses semua folder di perangkat",
                    emphasized = true,
                    onClick = {
                        onDismiss()
                        onPickFromStorage()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_file_manager,
                    title = "File Manager",
                    description = "Browser file bawaan AFFT dengan pencarian & sortir",
                    onClick = {
                        onDismiss()
                        onPickFromFileManager()
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                DialogOptionCard(
                    iconRes = R.drawable.ic_folder_open,
                    title = "Folder Kerja",
                    description = "Langsung ke folder input/ di workspace AFFT",
                    onClick = {
                        onDismiss()
                        onPickFromWorkspace()
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Footer ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", fontFamily = LocalFontFamily.current)
                    }
                }
            }
        }
    }
}

/**
 * Dialog browser file yang bisa navigasi ke seluruh penyimpanan perangkat,
 * mirip dengan File Manager. Mulai dari /storage/emulated/0/.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFileBrowserDialog(
    initialDir: File = File("/storage/emulated/0"),
    title: String = "Pilih File dari Penyimpanan",
    onFileSelected: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentDir by remember { mutableStateOf(initialDir) }

    AppDialog(
        iconRes = R.drawable.ic_phone_android,
        title = title,
        subtitle = currentDir.absolutePath,
        onDismiss = onDismiss,
        content = {
            val dirs =
                remember(currentDir) {
                    currentDir
                        .listFiles()
                        ?.filter { it.isDirectory }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }
            val files =
                remember(currentDir) {
                    currentDir
                        .listFiles()
                        ?.filter { it.isFile }
                        ?.sortedWith(compareBy<File> { it.extension }.thenBy { it.name.lowercase() })
                        ?: emptyList()
                }

            if (dirs.isEmpty() && files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Folder kosong",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                ) {
                    // Parent directory navigation
                    if (currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "../",
                                        fontFamily = LocalFontFamily.current,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painterResource(R.drawable.ic_folder),
                                        null,
                                        tint = LocalIconTint.current,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        currentDir.parentFile?.let { currentDir = it }
                                    },
                            )
                            HorizontalDivider()
                        }
                    }

                    // Quick root navigation
                    item {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "/storage/emulated/0/",
                                    fontFamily = LocalFontFamily.current,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painterResource(R.drawable.ic_phone_android),
                                    null,
                                    tint = LocalIconTint.current,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    currentDir = File("/storage/emulated/0")
                                },
                        )
                        HorizontalDivider()
                    }

                    // Directories
                    items(dirs) { dir ->
                        if (dir.canRead()) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "${dir.name}/",
                                        fontFamily = LocalFontFamily.current,
                                        fontWeight = FontWeight.Medium,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painterResource(R.drawable.ic_folder),
                                        null,
                                        tint = LocalIconTint.current,
                                    )
                                },
                                modifier = Modifier.clickable { currentDir = dir },
                            )
                            HorizontalDivider()
                        }
                    }

                    // Files
                    items(files) { file ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    file.name,
                                    fontFamily = LocalFontFamily.current,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    formatFileSize(file.length()) + " · " + file.extension.uppercase(),
                                    fontFamily = LocalFontFamily.current,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    when (file.extension.lowercase()) {
                                        "img", "bin" -> painterResource(R.drawable.ic_disc_full)
                                        "zip", "gz", "xz" -> painterResource(R.drawable.ic_archive)
                                        "txt", "log" -> painterResource(R.drawable.ic_text_snippet)
                                        else -> painterResource(R.drawable.ic_insert_drive_file)
                                    },
                                    null,
                                    tint = LocalIconTint.current,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onFileSelected(file)
                                    onDismiss()
                                },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        footer = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
    )
}

/**
 * Full-screen dialog file picker with complete file manager capabilities.
 * Mirip dengan FileManagerScreen tapi dalam mode picker (memilih file).
 * Bisa search, sort, navigasi ke seluruh penyimpanan.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerPickerDialog(
    initialDir: File = File("/storage/emulated/0"),
    title: String = "Pilih File dari File Manager",
    onFileSelected: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentDir by remember { mutableStateOf(initialDir) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var pathHistory by remember { mutableStateOf<List<File>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var sortMode by rememberSaveable { mutableStateOf("name") }
    var sortAsc by rememberSaveable { mutableStateOf(true) }

    // Resolve directories
    val storageRoot = File("/storage/emulated/0")
    val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AFFT")

    fun refreshFiles(dir: File) {
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
        files = if (sortAsc) sorted else sorted.reversed()
        currentDir = dir
    }

    LaunchedEffect(Unit) {
        refreshFiles(initialDir)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
            ) {
                // ── Header ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = LocalFontFamily.current,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = "Tutup",
                            tint = LocalIconTint.current,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Path bar ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pathHistory.isNotEmpty()) {
                        IconButton(onClick = {
                            val prev = pathHistory.last()
                            pathHistory = pathHistory.dropLast(1)
                            refreshFiles(prev)
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ic_arrow_back), "Back", tint = LocalIconTint.current)
                        }
                    }
                    IconButton(
                        onClick = {
                            currentDir.parentFile?.let { parent ->
                                pathHistory = pathHistory + currentDir
                                refreshFiles(parent)
                            }
                        },
                        enabled = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_subdirectory_arrow_left), "↑", tint = LocalIconTint.current)
                    }
                    Text(
                        text = currentDir.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalFontFamily.current,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { refreshFiles(currentDir) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_refresh), "Refresh", tint = LocalIconTint.current)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Quick location row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = {
                            pathHistory = emptyList()
                            refreshFiles(storageRoot)
                        },
                        label = { Text("Device", fontSize = 11.sp) },
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
                            pathHistory = emptyList()
                            refreshFiles(File("/storage/emulated/0"))
                        },
                        label = { Text("Internal", fontSize = 11.sp) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_folder), null, tint = LocalIconTint.current) },
                    )
                    if (downloadDir.exists()) {
                        AssistChip(
                            onClick = {
                                pathHistory = emptyList()
                                refreshFiles(downloadDir)
                            },
                            label = { Text("DL/AFFT", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_download),
                                    null,
                                    tint = LocalIconTint.current,
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Sort + Search row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = sortMode == "name",
                        onClick = {
                            if (sortMode == "name") {
                                sortAsc = !sortAsc
                            } else {
                                sortMode = "name"
                                sortAsc = true
                            }
                            refreshFiles(currentDir)
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
                            )
                        },
                        modifier = Modifier.height(32.dp),
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
                            refreshFiles(currentDir)
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
                            )
                        },
                        modifier = Modifier.height(32.dp),
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
                            refreshFiles(currentDir)
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
                            )
                        },
                        modifier = Modifier.height(32.dp),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_search), "Search", tint = LocalIconTint.current)
                    }
                }

                // ── Search bar ──
                if (showSearch) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            refreshFiles(currentDir)
                        },
                        placeholder = { Text("Cari file...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = LocalIconTint.current) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    refreshFiles(currentDir)
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(painterResource(R.drawable.ic_clear), null, tint = LocalIconTint.current)
                                }
                            }
                        },
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── File list ──
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    if (currentDir.listFiles().isNullOrEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painterResource(R.drawable.ic_folder_off),
                                    null,
                                    tint = LocalIconTint.current,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Folder kosong",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(files) { file ->
                                FileManagerPickerItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            pathHistory = pathHistory + currentDir
                                            refreshFiles(file)
                                        } else {
                                            onFileSelected(file)
                                            onDismiss()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Bottom info ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${files.size} item | ${currentDir.absolutePath}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileManagerPickerItem(
    file: File,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 1.dp)
                .combinedClickable(
                    onClick = onClick,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (file.isDirectory) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
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
                if (file.isFile) {
                    Text(
                        text = formatFileSize(file.length()),
                        fontFamily = LocalFontFamily.current,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Folder",
                        fontFamily = LocalFontFamily.current,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = LocalIconTint.current,
            )
        }
    }
}
