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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.afft.app.R

/** Isi halaman Lisensi & Atribusi (dipakai di dalam dialog About maupun sendiri). */
@Composable
fun AboutLegalContent(
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val docs =
        remember {
            listOf(
                "NOTICE" to "legal/NOTICE",
                "Third-Party" to "legal/THIRD_PARTY_NOTICES.md",
                "Apache-2.0" to "legal/APACHE-2.0.txt",
                "OFL-1.1 Fonts" to "legal/OFL-1.1.txt",
            )
        }
    val texts =
        remember {
            docs.associate { (label, path) ->
                label to
                    runCatching {
                        context.assets.open(path).bufferedReader().use { it.readText() }
                    }.getOrDefault("(Gagal memuat $path)")
            }
        }
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        DialogHeader(
            iconRes = R.drawable.ic_description,
            title = "Lisensi & Atribusi",
            subtitle = "Dokumen lisensi yang dibundel dalam aplikasi",
            onDismiss = onDismiss,
            onBack = onBack,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            docs.forEachIndexed { index, (label, _) ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val (label, _) = docs[selectedIndex]
        Text(
            texts[label] ?: "",
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            fontFamily = LocalFontFamily.current,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
