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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

private const val TYPE_TICK_MILLIS = 22L
private const val CURSOR_BLINK_MILLIS = 500L
private const val STABILIZE_MILLIS = 400L
private const val CURSOR_BLINK_TICKS = 3
private const val TYPING_CURSOR = "▌"
private const val DISPLAY_MIN_LINES = 2
private const val DISPLAY_MAX_LINES = 2

/**
 * Teks dengan efek "typewriter satu kali": karakter muncul satu per satu,
 * lalu kursor berkedip beberapa kali dan berhenti (tidak ulang terus).
 *
 * Hemat untuk kartu status berjalan: mengetik hanya mulai setelah teks
 * berhenti berubah (debounce [STABILIZE_MILLIS]), jadi update yang cepat
 * tidak membuat teks restart terus-menerus pada semua perubahan.
 */
@Composable
fun TypingStatusText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    minLines: Int = DISPLAY_MIN_LINES,
    maxLines: Int = DISPLAY_MAX_LINES,
) {
    if (text.isBlank()) return

    // Debounce: tunggu teks tidak berubah sebelum mulai mengetik.
    var settled by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        delay(STABILIZE_MILLIS)
        settled = text
    }

    var visibleCount by remember(settled) { mutableIntStateOf(0) }
    var cursorVisible by remember(settled) { mutableStateOf(true) }

    LaunchedEffect(settled) {
        val targetText = settled ?: return@LaunchedEffect
        visibleCount = 0
        cursorVisible = true
        while (visibleCount < targetText.length) {
            delay(TYPE_TICK_MILLIS)
            visibleCount = (visibleCount + 1).coerceAtMost(targetText.length)
        }
        repeat(CURSOR_BLINK_TICKS) {
            delay(CURSOR_BLINK_MILLIS)
            cursorVisible = !cursorVisible
        }
        cursorVisible = false
    }

    Text(
        text = (settled ?: "").take(visibleCount) + if (cursorVisible) TYPING_CURSOR else "",
        modifier = modifier,
        color = color,
        style = style,
        fontFamily = LocalFontFamily.current,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
