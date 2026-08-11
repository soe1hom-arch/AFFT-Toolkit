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

package com.afft.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.afft.app.R

/** Font aplikasi yang dapat dipilih pengguna di Settings. */
enum class AppFont(
    val id: String,
    val displayName: String,
) {
    INTER("inter", "Inter (Sans)"),
    JETBRAINS_MONO("jetbrains", "JetBrains Mono");

    companion object {
        fun fromId(id: String?): AppFont? = entries.firstOrNull { it.id == id }
    }
}

private val InterFamily =
    FontFamily(
        Font(R.font.inter_regular),
        Font(R.font.inter_medium, weight = FontWeight.Medium),
        Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
        Font(R.font.inter_bold, weight = FontWeight.Bold),
    )

private val JetBrainsFamily =
    FontFamily(
        Font(R.font.jetbrains_mono_regular),
        Font(R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, weight = FontWeight.Bold),
    )

/** Resolusi [FontFamily] untuk pilihan font pengguna. */
fun AppFont.family(): FontFamily =
    when (this) {
        AppFont.INTER -> InterFamily
        AppFont.JETBRAINS_MONO -> JetBrainsFamily
    }

/**
 * Font yang sedang aktif, disediakan oleh [AFFTTheme].
 * Semua komponen (termasuk teks teknis/terminal) membaca font dari sini
 * agar tampilan seragam dan bisa diganti pengguna.
 */
val LocalFontFamily = staticCompositionLocalOf { AppFont.INTER.family() }
