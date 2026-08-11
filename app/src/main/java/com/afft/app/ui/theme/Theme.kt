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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Warna default semua icon biasa (VectorDrawable) — diambil dari MaterialTheme. */
val LocalIconTint = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun AFFTTheme(
    preset: ThemePreset = ThemePreset.AFFT_GREEN,
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    accent: Color? = null,
    iconTint: Color? = null,
    fontFamily: FontFamily = AppFont.INTER.family(),
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }

    val baseScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            darkTheme -> preset.darkScheme()
            else -> preset.lightScheme()
        }
    val colorScheme = if (accent != null) baseScheme.withAccent(accent, darkTheme) else baseScheme
    val resolvedIconTint = iconTint ?: colorScheme.onSurfaceVariant

    CompositionLocalProvider(
        LocalFontFamily provides fontFamily,
        LocalIconTint provides resolvedIconTint,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography(fontFamily),
            content = content,
        )
    }
}
