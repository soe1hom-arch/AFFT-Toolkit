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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.afft.app.R

/** Isi halaman Developer (dipakai di dalam dialog About maupun sendiri). */
@Composable
fun AboutDeveloperContent(
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
            iconRes = R.drawable.ic_person,
            title = if (english) "Developer" else "Pengembang",
            subtitle =
                if (english) "Creator & tech stack" else "Pembuat dan tumpukan teknologi",
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
            SectionTitle(
                if (english) "Developer" else "Pengembang",
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(R.drawable.ic_person, "soe1hom-arch (Wandi)")
            InfoRow(R.drawable.ic_code, "Kotlin + Jetpack Compose")
            ClickableRow(
                iconRes = R.drawable.ic_open_in_new,
                text = "soe1hom-arch/AFFT-Toolkit",
                url = "https://github.com/soe1hom-arch/AFFT-Toolkit",
            )
            ClickableRow(
                iconRes = R.drawable.ic_bug_report,
                text = if (english) "Report an issue" else "Laporkan Masalah",
                url = "https://github.com/soe1hom-arch/AFFT-Toolkit/issues",
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(if (english) "Technology Stack" else "Tumpukan Teknologi")
            Spacer(modifier = Modifier.height(8.dp))
            BulletRow("Kotlin", "Bahasa pemrograman utama")
            BulletRow("Jetpack Compose + Material 3", "UI toolkit modern")
            BulletRow("Coroutines & StateFlow", "Konkurensi & state reaktif")
            BulletRow("Android SDK", "minSdk 26 · targetSdk 35")
            BulletRow("Workspace Engine", "Manajemen proyek firmware")
            BulletRow("Firmware Analysis Engine", "Parser payload, boot, super, filesystem")
            BulletRow("Health Score & Validasi", "Penilaian kesehatan firmware")
            BulletRow("Font Custom", "Inter & JetBrains Mono (OFL-1.1)")
        }
    }
}
