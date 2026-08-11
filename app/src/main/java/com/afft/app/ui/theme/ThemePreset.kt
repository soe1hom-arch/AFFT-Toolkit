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

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Mode pemilihan terang/gelap global. */
enum class ThemeMode(
    val id: String,
    val displayName: String,
) {
    SYSTEM("system", "Ikuti Sistem"),
    DARK("dark", "Gelap"),
    LIGHT("light", "Terang");

    companion object {
        fun fromId(id: String?): ThemeMode? = entries.firstOrNull { it.id == id }
    }
}

/** Warna aksen & latar dasar setiap preset (varian gelap & terang). */
data class PresetPalette(
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color,
    val darkBackground: Color,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val lightBackground: Color,
)

/** Preset tema premium yang bisa dipilih pengguna. */
enum class ThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val descriptionEn: String,
    val palette: PresetPalette?,
) {
    AFFT_GREEN(
        id = "afft_green",
        displayName = "AFFT Green",
        description = "Tema bawaan dengan identitas brand hijau + cyan",
        descriptionEn = "Default theme with green + cyan brand identity",
        palette = null,
    ),
    MIDNIGHT_CYAN(
        id = "midnight_cyan",
        displayName = "Midnight Cyan",
        description = "Aksen cyan dominan di atas latar navy lebih pekat",
        descriptionEn = "Dominant cyan accent over a deeper navy background",
        palette =
            PresetPalette(
                darkPrimary = Color(0xFF22D3EE),
                darkSecondary = Color(0xFF2DD4BF),
                darkTertiary = Color(0xFF60A5FA),
                darkBackground = Color(0xFF050B18),
                lightPrimary = Color(0xFF0E7490),
                lightSecondary = Color(0xFF0D9488),
                lightTertiary = Color(0xFF2563EB),
                lightBackground = Color(0xFFF4FAFD),
            ),
    ),
    AMBER_SOLAR(
        id = "amber_solar",
        displayName = "Amber Solar",
        description = "Aksen amber/emas dengan nuansa hangat",
        descriptionEn = "Warm amber/gold accent",
        palette =
            PresetPalette(
                darkPrimary = Color(0xFFFFB020),
                darkSecondary = Color(0xFFFF8A3C),
                darkTertiary = Color(0xFFFFC94D),
                darkBackground = Color(0xFF120C04),
                lightPrimary = Color(0xFFB45309),
                lightSecondary = Color(0xFFC2410C),
                lightTertiary = Color(0xFFA16207),
                lightBackground = Color(0xFFFFFBF0),
            ),
    ),
    VIOLET_NEBULA(
        id = "violet_nebula",
        displayName = "Violet Nebula",
        description = "Aksen ungu futuristik dengan sentuhan magenta",
        descriptionEn = "Futuristic violet accent with a magenta touch",
        palette =
            PresetPalette(
                darkPrimary = Color(0xFFA78BFA),
                darkSecondary = Color(0xFFF472B6),
                darkTertiary = Color(0xFF60A5FA),
                darkBackground = Color(0xFF0E0A1C),
                lightPrimary = Color(0xFF7C3AED),
                lightSecondary = Color(0xFFDB2777),
                lightTertiary = Color(0xFF2563EB),
                lightBackground = Color(0xFFFBFAFF),
            ),
    ),
    CHERRY_RED(
        id = "cherry_red",
        displayName = "Cherry Red",
        description = "Aksen merah crimson, cocok untuk mode berbahaya",
        descriptionEn = "Crimson red accent, suited for danger mode",
        palette =
            PresetPalette(
                darkPrimary = Color(0xFFFF6B5B),
                darkSecondary = Color(0xFFFF9F7C),
                darkTertiary = Color(0xFFFFC94D),
                darkBackground = Color(0xFF150708),
                lightPrimary = Color(0xFFC62828),
                lightSecondary = Color(0xFFE53935),
                lightTertiary = Color(0xFFEF6C00),
                lightBackground = Color(0xFFFFF9F9),
            ),
    ),
    DARK_GRAY(
        id = "dark_gray",
        displayName = "Dark Gray Premium",
        description = "Monokrom abu-abu gelap yang elegan dan netral",
        descriptionEn = "Elegant, neutral dark-gray monochrome",
        palette =
            PresetPalette(
                darkPrimary = Color(0xFFB0BCC6),
                darkSecondary = Color(0xFF8E9AA5),
                darkTertiary = Color(0xFF6E7B87),
                darkBackground = Color(0xFF0A0C0F),
                lightPrimary = Color(0xFF37474F),
                lightSecondary = Color(0xFF546E7A),
                lightTertiary = Color(0xFF78909C),
                lightBackground = Color(0xFFF5F6F8),
            ),
    );

    companion object {
        fun fromId(id: String?): ThemePreset? = entries.firstOrNull { it.id == id }

        fun preview(p: ThemePreset?): List<Color> =
            when (p) {
                null -> emptyList()
                AFFT_GREEN -> listOf(Green500, Cyan500, Yellow500)
                else ->
                    listOf(
                        p.palette!!.darkPrimary,
                        p.palette!!.darkSecondary,
                        p.palette!!.darkTertiary,
                    )
            }
    }
}

private fun mix(a: Color, b: Color, t: Float): Color =
    Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        1f,
    )

private fun onColor(bg: Color): Color =
    if (bg.luminance() > 0.55f) Color(0xFF0B0F12) else Color.White

private fun buildDarkScheme(p: PresetPalette): ColorScheme {
    val bg = p.darkBackground
    return darkColorScheme(
        primary = p.darkPrimary,
        onPrimary = onColor(p.darkPrimary),
        primaryContainer = mix(bg, p.darkPrimary, 0.28f),
        onPrimaryContainer = mix(p.darkPrimary, Color.White, 0.80f),
        inversePrimary = p.darkPrimary,
        secondary = p.darkSecondary,
        onSecondary = onColor(p.darkSecondary),
        secondaryContainer = mix(bg, p.darkSecondary, 0.24f),
        onSecondaryContainer = mix(p.darkSecondary, Color.White, 0.80f),
        tertiary = p.darkTertiary,
        onTertiary = onColor(p.darkTertiary),
        tertiaryContainer = mix(bg, p.darkTertiary, 0.24f),
        onTertiaryContainer = mix(p.darkTertiary, Color.White, 0.80f),
        background = bg,
        onBackground = mix(bg, Color.White, 0.82f),
        surface = mix(bg, Color.White, 0.10f),
        onSurface = mix(bg, Color.White, 0.85f),
        surfaceVariant = mix(bg, Color.White, 0.05f),
        onSurfaceVariant = mix(bg, Color.White, 0.55f),
        surfaceTint = p.darkPrimary,
        inverseSurface = Color(0xFFE3E9ED),
        inverseOnSurface = Color(0xFF111C24),
        error = Red500,
        onError = Color(0xFF3B0502),
        errorContainer = Color(0xFF5C1F1B),
        onErrorContainer = Color(0xFFFFDAD5),
        outline = mix(bg, p.darkPrimary, 0.35f),
        outlineVariant = mix(bg, Color.White, 0.12f),
        scrim = Color.Black,
        surfaceBright = mix(bg, Color.White, 0.22f),
        surfaceDim = bg,
        surfaceContainerLowest = mix(bg, Color.White, 0.03f),
        surfaceContainerLow = mix(bg, Color.White, 0.08f),
        surfaceContainer = mix(bg, Color.White, 0.12f),
        surfaceContainerHigh = mix(bg, Color.White, 0.17f),
        surfaceContainerHighest = mix(bg, Color.White, 0.22f),
    )
}

/** Skema warna gelap untuk preset ini. */
fun ThemePreset.darkScheme(): ColorScheme =
    when (this) {
        ThemePreset.AFFT_GREEN -> GreenDarkScheme
        else -> buildDarkScheme(requireNotNull(palette))
    }

private fun buildLightScheme(p: PresetPalette): ColorScheme {
    val bg = p.lightBackground
    return lightColorScheme(
        primary = p.lightPrimary,
        onPrimary = onColor(p.lightPrimary),
        primaryContainer = mix(p.lightPrimary, Color.White, 0.85f),
        onPrimaryContainer = mix(p.lightPrimary, Color.Black, 0.45f),
        secondary = p.lightSecondary,
        onSecondary = onColor(p.lightSecondary),
        secondaryContainer = mix(p.lightSecondary, Color.White, 0.85f),
        onSecondaryContainer = mix(p.lightSecondary, Color.Black, 0.45f),
        tertiary = p.lightTertiary,
        onTertiary = onColor(p.lightTertiary),
        tertiaryContainer = mix(p.lightTertiary, Color.White, 0.85f),
        onTertiaryContainer = mix(p.lightTertiary, Color.Black, 0.45f),
        background = bg,
        onBackground = mix(bg, Color.Black, 0.80f),
        surface = Color.White,
        onSurface = mix(bg, Color.Black, 0.82f),
        surfaceVariant = mix(bg, Color.Black, 0.06f),
        onSurfaceVariant = mix(bg, Color.Black, 0.52f),
        surfaceTint = p.lightPrimary,
        inverseSurface = mix(bg, Color.Black, 0.85f),
        inverseOnSurface = Color(0xFFE6ECEF),
        error = Red500,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD5),
        onErrorContainer = Color(0xFF3B0502),
        outline = mix(bg, Color.Black, 0.50f),
        outlineVariant = mix(bg, Color.Black, 0.28f),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = mix(bg, Color.White, 0.70f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = mix(bg, Color.Black, 0.02f),
        surfaceContainer = mix(bg, Color.Black, 0.04f),
        surfaceContainerHigh = mix(bg, Color.Black, 0.06f),
        surfaceContainerHighest = mix(bg, Color.Black, 0.09f),
    )
}

/** Skema warna terang untuk preset ini. */
fun ThemePreset.lightScheme(): ColorScheme =
    when (this) {
        ThemePreset.AFFT_GREEN -> GreenLightScheme
        else -> buildLightScheme(requireNotNull(palette))
    }

// ── Skema default AFFT Green (identitas brand, dipertahankan persis) ──

private val GreenDarkScheme =
    darkColorScheme(
        primary = Green500,
        onPrimary = Color(0xFF002016),
        primaryContainer = Color(0xFF0F3B2E),
        onPrimaryContainer = Color(0xFFB8F7DD),
        inversePrimary = Green700,
        secondary = Cyan500,
        onSecondary = Color(0xFF00363C),
        secondaryContainer = Color(0xFF0C3944),
        onSecondaryContainer = Color(0xFFB7F4F9),
        tertiary = Yellow500,
        onTertiary = Color(0xFF3D2A00),
        tertiaryContainer = Color(0xFF4A3A12),
        onTertiaryContainer = Color(0xFFFFE3A8),
        background = DarkBackground,
        onBackground = Color(0xFFE6ECEF),
        surface = DarkSurface,
        onSurface = Color(0xFFE8EEF1),
        surfaceVariant = DarkSurface2,
        onSurfaceVariant = Color(0xFF9FB2BE),
        surfaceTint = Green500,
        inverseSurface = Color(0xFFE8EEF1),
        inverseOnSurface = Color(0xFF101B22),
        error = Red500,
        onError = Color(0xFF3B0502),
        errorContainer = Color(0xFF5C1F1B),
        onErrorContainer = Color(0xFFFFDAD5),
        outline = Color(0xFF2F4A59),
        outlineVariant = Color(0xFF223945),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF1C2C36),
        surfaceDim = DarkBackground,
        surfaceContainerLowest = Color(0xFF030A0F),
        surfaceContainerLow = Color(0xFF0E1A21),
        surfaceContainer = Color(0xFF13232C),
        surfaceContainerHigh = Color(0xFF182A34),
        surfaceContainerHighest = Color(0xFF1E313C),
    )

private val GreenLightScheme =
    lightColorScheme(
        primary = Green700,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBEF5DD),
        onPrimaryContainer = Color(0xFF002017),
        inversePrimary = Green500,
        secondary = Cyan700,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC1EEF4),
        onSecondaryContainer = Color(0xFF002E34),
        tertiary = Color(0xFFB07C00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE29E),
        onTertiaryContainer = Color(0xFF332400),
        background = Color(0xFFF7FAFB),
        onBackground = Color(0xFF182125),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF182125),
        surfaceVariant = Color(0xFFE0EAEC),
        onSurfaceVariant = Color(0xFF45565D),
        surfaceTint = Green700,
        inverseSurface = Color(0xFF2D363B),
        inverseOnSurface = Color(0xFFE9F1F3),
        error = Red500,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD5),
        onErrorContainer = Color(0xFF3B0502),
        outline = Color(0xFF76878F),
        outlineVariant = Color(0xFFC5D2D6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD9E2E4),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F5F6),
        surfaceContainer = Color(0xFFEBF0F2),
        surfaceContainerHigh = Color(0xFFE5EBED),
        surfaceContainerHighest = Color(0xFFDFE5E8),
    )
