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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.R

/** Isi halaman Credits (dipakai di dalam dialog About maupun sendiri). */
@Composable
fun AboutCreditsContent(
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
            iconRes = R.drawable.ic_code,
            title = if (english) "Third-Party Credits" else "Kredit Pihak Ketiga",
            subtitle =
                if (english) "Binaries & fonts bundled in this app" else "Binary & font yang dibundel di aplikasi ini",
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
            SectionTitle(if (english) "Tools & Binaries" else "Tools & Binary")
            Spacer(modifier = Modifier.height(4.dp))
            CreditRow("payload-dumper-go", "ssut", "https://github.com/ssut/payload-dumper-go")
            CreditRow("magiskboot", "topjohnwu", "https://github.com/topjohnwu/Magisk")
            CreditRow("lpmake", "AOSP", "https://android.googlesource.com/platform/system/core/")
            CreditRow("lpunpack", "AOSP", "https://android.googlesource.com/platform/system/core/")
            CreditRow("mkfs.erofs / extract.erofs", "erofs-utils", "https://git.kernel.org/pub/scm/linux/kernel/git/xiang/erofs-utils.git")
            CreditRow("make_ext4fs / debugfs", "AOSP", "https://android.googlesource.com/platform/system/core/")
            CreditRow("simg2img", "AOSP", "https://android.googlesource.com/platform/system/core/")
            CreditRow("liblzma", "tukaani-project/xz", "https://github.com/tukaani-project/xz")
            CreditRow("libzstd", "facebook/zstd", "https://github.com/facebook/zstd")

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(if (english) "Fonts" else "Font")
            Spacer(modifier = Modifier.height(4.dp))
            CreditRow("Inter", "rsms/inter (OFL-1.1)", "https://github.com/rsms/inter")
            CreditRow("JetBrains Mono", "JetBrains (OFL-1.1)", "https://github.com/JetBrains/JetBrainsMono")

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (english) {
                    "Tap an item to open its source page. Each item belongs to its respective owner and license."
                } else {
                    "Ketuk item untuk membuka halaman sumbernya. Setiap item milik pemiliknya masing-masing dan tunduk pada lisensi sumber."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditRow(
    name: String,
    author: String,
    url: String,
) {
    val ctx = LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable {
                    try {
                        val intent =
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url),
                            )
                        ctx.startActivity(intent)
                    } catch (_: Exception) {
                    }
                },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                name,
                fontFamily = LocalFontFamily.current,
                fontSize = 13.sp,
            )
            Text(
                author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Icon(
            painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            tint = LocalIconTint.current,
        )
    }
}