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

package com.afft.app.ui.components.dashboard
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.LocalIconTint

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class WorkspaceInfo(
    val status: StatusType = StatusType.READY,
    val project: String = "No Project Loaded",
    val firmware: String = "None",
    val androidVersion: String = "Unknown",
    val device: String = "Unknown",
    val path: String = "",
    val lastActivity: String = "None",
    val lastOpened: String = "—",
    val isEmpty: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceCard(
    workspace: WorkspaceInfo,
    modifier: Modifier = Modifier,
    onOpenFolder: (() -> Unit)? = null,
) {
    val cardAlpha = remember { Animatable(0f) }
    val slideOffset = remember { Animatable(-24f) }
    var sheetDetail by remember { mutableStateOf<MetadataSheetDetail?>(null) }

    fun openSheet(
        title: String,
        value: String,
        description: String? = null,
        openFolder: Boolean = false,
    ) {
        sheetDetail =
            MetadataSheetDetail(
                title = title,
                value = value,
                description = description,
                allowShare = !openFolder,
                onOpenFolder = if (openFolder) onOpenFolder else null,
            )
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { cardAlpha.animateTo(1f, tween(durationMillis = 250)) }
            launch { slideOffset.animateTo(0f, tween(durationMillis = 250)) }
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = cardAlpha.value
                    translationY = slideOffset.value
                },
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = null,
                    tint = LocalIconTint.current,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = LocalFontFamily.current,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(type = workspace.status)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (workspace.isEmpty) {
                EmptyWorkspace()
            } else {
                MetadataItem(
                    iconRes = R.drawable.ic_folder_open,
                    title = "Current Project",
                    value = workspace.project,
                    onClick = {
                        openSheet(
                            title = "Current Project",
                            value = workspace.project,
                            description = "Nama project workspace aktif",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_insert_drive_file,
                    title = "Selected Firmware",
                    value = workspace.firmware,
                    onClick = {
                        openSheet(
                            title = "Selected Firmware",
                            value = workspace.firmware,
                            description = "Berkas firmware yang sedang dipilih",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_phone_android,
                    title = "Android Version",
                    value = workspace.androidVersion,
                    onClick = {
                        openSheet(
                            title = "Android Version",
                            value = workspace.androidVersion,
                            description = "Versi Android dari project firmware",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_sd_storage,
                    title = "Device",
                    value = workspace.device,
                    onClick = {
                        openSheet(
                            title = "Device",
                            value = workspace.device,
                            description = "Perangkat target firmware",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_storage,
                    title = "Workspace Location",
                    value = workspace.path,
                    onClick = {
                        openSheet(
                            title = "Workspace Location",
                            value = workspace.path,
                            description = "Lokasi folder project workspace di penyimpanan",
                            openFolder = true,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_description,
                    title = "Last Operation",
                    value = workspace.lastActivity,
                    onClick = {
                        openSheet(
                            title = "Last Operation",
                            value = workspace.lastActivity,
                            description = "Operasi terakhir yang dicatat workspace",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                MetadataItem(
                    iconRes = R.drawable.ic_open_in_new,
                    title = "Last Opened",
                    value = workspace.lastOpened,
                    onClick = {
                        openSheet(
                            title = "Last Opened",
                            value = workspace.lastOpened,
                            description = "Waktu project terakhir dibuka",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    sheetDetail?.let { detail ->
        MetadataBottomSheet(
            detail = detail,
            onDismiss = { sheetDetail = null },
        )
    }
}

@Composable
private fun EmptyWorkspace() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder_off),
            contentDescription = null,
            tint = LocalIconTint.current,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "No active firmware project.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = LocalFontFamily.current,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Select a firmware file to begin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalFontFamily.current,
            )
        }
    }
}