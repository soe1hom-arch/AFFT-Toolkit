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

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

private const val TYPE_TICK_MILLIS = 18L
private const val TYPE_START_MILLIS = 60L
private const val TYPE_SETTLE_MILLIS = 120L

/** Jumlah karakter yang benar-benar di-typing sebelum sisanya muncul instan. */
private const val TYPE_REVEAL_LIMIT = 28
private const val TYPING_CURSOR = "▌"

/**
 * Teks metadata dengan efek "mengetik sekali": karakter muncul satu per satu,
 * lalu sisa nilai panjang langsung tampil agar tidak menunggu lama.
 *
 * - Hanya berjalan sekali saat [text] pertama kali muncul (bukan looping).
 * - Nilai panjang dipangkas saat typing, lalu reveal penuh agar tetap cepat.
 * - Menghormati pengaturan "reduce motion" (skip animasi di Android).
 *
 * Dipakai untuk nilai di kartu Inspector / Workspace / Status agar terlihat
 * hidup layaknya aplikasi profesional, tanpa mengganggu performa.
 */
@Composable
fun TypedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val context = LocalContext.current
    val reduceMotion =
        remember {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }

    if (text.isBlank() || reduceMotion) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            fontFamily = LocalFontFamily.current,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    var visible by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        delay(TYPE_START_MILLIS)
        while (visible < text.length) {
            delay(TYPE_TICK_MILLIS)
            visible = (visible + 1).coerceAtMost(text.length)
            if (visible >= TYPE_REVEAL_LIMIT) break
        }
        // Sisanya langsung tampil penuh (jika panjang).
        visible = text.length
        delay(TYPE_SETTLE_MILLIS)
    }

    val typing = visible < text.length
    val rendered =
        if (typing) {
            text.take(visible) + TYPING_CURSOR
        } else {
            text
        }

    Text(
        text = rendered,
        modifier = modifier,
        color = color,
        style = style,
        fontFamily = LocalFontFamily.current,
        maxLines = maxLines,
        overflow = overflow,
    )
}
