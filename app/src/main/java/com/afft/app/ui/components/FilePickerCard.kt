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

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afft.app.R

@Composable
fun FilePickerCard(
    title: String,
    selectedUri: Uri?,
    selectedFileName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedFileName != null) {
                Text(
                    text = selectedFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = LocalFontFamily.current,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(onClick = onClick) {
                Icon(
                    painterResource(R.drawable.ic_folder_open),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = LocalIconTint.current,
                )
                Text(if (selectedUri == null) "Pilih File" else "Ganti File")
            }

            // Tampilkan source indicator
            if (selectedFileName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val sourceText =
                    if (selectedUri != null) {
                        "Dari penyimpanan perangkat"
                    } else {
                        "Dari folder kerja"
                    }
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = LocalFontFamily.current,
                )
            }
        }
    }
}