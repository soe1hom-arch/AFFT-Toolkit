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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.afft.app.R

/** Isi halaman Fitur (dipakai di dalam dialog About maupun sendiri). */
@Composable
fun AboutFeaturesContent(
    english: Boolean,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        DialogHeader(
            iconRes = R.drawable.ic_check_circle,
            title = if (english) "Features" else "Fitur",
            subtitle =
                if (english) "What AFFT Toolkit can do" else "Apa yang bisa dilakukan AFFT Toolkit",
            onDismiss = onDismiss,
            onBack = onBack,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            val items =
                if (english) {
                    listOf(
                        "Workspace projects (create, open, recent, history)",
                        "Extract & analyze payload.bin",
                        "Unpack & repack super.img (sparse)",
                        "Extract & repack filesystem (EROFS / ext4)",
                        "Unpack & repack boot images (7 types)",
                        "Firmware Inspector with health score & validation",
                        "Interactive metadata (copy/share via bottom sheet)",
                        "Process Monitor & real-time terminal log",
                        "Save logs to file",
                        "AFFT Manager & Export to Downloads",
                        "Premium theme presets & adjustable appearance",
                    )
                } else {
                    listOf(
                        "Proyek workspace (buat, buka, terbaru, riwayat)",
                        "Ekstrak & analisis payload.bin",
                        "Unpack & repack super.img (sparse)",
                        "Ekstrak & repack filesystem (EROFS / ext4)",
                        "Unpack & repack boot image (7 jenis)",
                        "Firmware Inspector dengan health score & validasi",
                        "Metadata interaktif (salin/bagikan via bottom sheet)",
                        "Process Monitor & log real-time",
                        "Simpan log ke file",
                        "AFFT Manager & ekspor ke Downloads",
                        "Preset tema premium & tampilan yang dapat diatur",
                    )
                }
            items.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        feature,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(if (english) "Engine Sections" else "Bagian Mesin")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (english) {
                    "Payload · Boot · Super · Filesystem · Recovery · Vendor Boot · Kernel · ROM Builder · APK Tools"
                } else {
                    "Payload · Boot · Super · Filesystem · Recovery · Vendor Boot · Kernel · ROM Builder · APK Tools"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
