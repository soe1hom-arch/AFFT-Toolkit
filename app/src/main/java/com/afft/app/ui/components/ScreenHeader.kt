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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.R
import com.afft.app.ui.components.dashboard.StatusBadge
import com.afft.app.ui.components.dashboard.StatusType

/**
 * Header konsisten untuk semua layar tool.
 *
 * - Chip ikon dengan warna aksen (dari warna ikon global).
 * - Judul + deskripsi, seragam di semua screen.
 * - [status] opsional: badge status di kanan atas (mis. RUNNING / READY).
 * - Garis aksen horizontal sebagai penutup.
 */
@Composable
fun ScreenHeader(
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    status: Pair<StatusType, String>? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = LocalIconTint.current,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = LocalFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = LocalFontFamily.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status != null) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(type = status.first, label = status.second)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                LocalIconTint.current.copy(alpha = 0.9f),
                                LocalIconTint.current.copy(alpha = 0.15f),
                            ),
                        ),
                    ),
        )
    }
}

/**
 * Judul seksi bernomor untuk alur wizard (Langkah 1, 2, 3, dst).
 *
 * Konsisten di semua screen: angka dalam lingkaran + judul + deskripsi opsional.
 */
@Composable
fun StepSectionTitle(
    step: String,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val accent = if (enabled) LocalIconTint.current else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(26.dp),
            shape = RoundedCornerShape(50),
            color = accent.copy(alpha = 0.14f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = LocalFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = LocalFontFamily.current,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = LocalFontFamily.current,
                )
            }
        }
    }
}

/**
 * Kartu pemilih folder sumber repack.
 *
 * Mendukung 2 jalur:
 * - "Browser" → [WorkspaceFileBrowserDialog] mode pilih folder (bebas ke mana saja).
 * - "Sistem"  → SAF [ACTION_OPEN_DOCUMENT_TREE] lewat [onPickSystemFolder].
 *
 * Menampilkan path terpilih (atau default) + tombol hapus.
 */
@Composable
fun RepackSourceCard(
    selectedPath: String?,
    defaultHint: String,
    onBrowse: () -> Unit,
    onPickSystemFolder: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder_open),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalIconTint.current,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Folder Sumber Repack",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = LocalFontFamily.current,
                    )
                    Text(
                        selectedPath ?: defaultHint,
                        fontFamily = LocalFontFamily.current,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectedPath != null) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_clear),
                            contentDescription = "Reset sumber",
                            tint = LocalIconTint.current,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onBrowse,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LocalIconTint.current,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browser Folder", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onPickSystemFolder,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_storage),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LocalIconTint.current,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pilih via Sistem", fontSize = 11.sp)
                }
            }
        }
    }
}
