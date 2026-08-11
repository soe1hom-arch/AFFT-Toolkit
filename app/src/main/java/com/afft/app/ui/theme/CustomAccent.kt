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

import android.graphics.Color as AColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

private fun mix(a: Color, b: Color, t: Float): Color =
    Color(
        a.red + (b.red - a.red) * t,
        a.green + (b.green - a.green) * t,
        a.blue + (b.blue - a.blue) * t,
        1f,
    )

private fun onColor(bg: Color): Color =
    if (bg.luminance() > 0.55f) Color(0xFF0B0F12) else Color.White

private fun shiftHue(c: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    AColor.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees / 360f).mod(1f)
    return Color.hsv(hsv[0] * 360f, hsv[1], hsv[2])
}

/**
 * Menggantikan aksen global (primary/secondary/tertiary + surfaceTint)
 * pada [ColorScheme] dengan warna [accent] pilihan pengguna, tanpa mengubah
 * warna [error] agar status kegagalan/peringatan tetap terbaca.
 */
fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val primary = accent
    val secondary = shiftHue(primary, +40f)
    val tertiary = shiftHue(primary, -40f)

    val primaryContainer = if (dark) mix(Color.Black, primary, 0.28f) else mix(primary, Color.White, 0.85f)
    val onPrimaryContainer = if (dark) mix(primary, Color.White, 0.80f) else mix(primary, Color.Black, 0.45f)
    val secondaryContainer = if (dark) mix(Color.Black, secondary, 0.24f) else mix(secondary, Color.White, 0.85f)
    val onSecondaryContainer = if (dark) mix(secondary, Color.White, 0.80f) else mix(secondary, Color.Black, 0.45f)
    val tertiaryContainer = if (dark) mix(Color.Black, tertiary, 0.24f) else mix(tertiary, Color.White, 0.85f)
    val onTertiaryContainer = if (dark) mix(tertiary, Color.White, 0.80f) else mix(tertiary, Color.Black, 0.45f)

    return copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = onColor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onColor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        surfaceTint = primary,
    )
}
