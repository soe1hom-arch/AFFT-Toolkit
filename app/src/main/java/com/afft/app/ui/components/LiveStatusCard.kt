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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afft.app.ui.theme.Green500
import com.afft.app.ui.theme.Yellow500

/** Simpan pesan "akhir proses" terakhir (selesai/gagal) untuk kartu status.
 * - Saat [isRunning] = true, pesan di-reset (operasi baru dimulai).
 * - Saat tidak berjalan dan [progressMessage] terisi, itu dianggap status
 *   final (mis. "Ekstrak selesai!", "File disalin: x") dan dipertahankan
 *   sampai operasi berikutnya dimulai, agar tampilan Live Status benar-benar
 *   memperlihatkan hasil pekerjaan yang baru selesai.
 */
@Composable
fun rememberDoneMessage(progressMessage: String, isRunning: Boolean): String? {
    var doneMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isRunning, progressMessage) {
        if (isRunning) {
            doneMessage = null
        } else if (progressMessage.isNotBlank()) {
            doneMessage = progressMessage
        }
    }
    return doneMessage
}

private const val STATUS_MIN_LINES = 2
private const val STATUS_MAX_LINES = 2
private const val IDLE_DOT_ALPHA = 0.9f

/**
 * Kartu status berjalan untuk layar operasi & home.
 *
 * - Tinggi teks status dikunci 2 baris agar kartu TIDAK pernah berubah tinggi.
 * - Animasi berdenyut (dot & teks) HANYA berjalan saat [busy] = true untuk
 *   hemat baterai; saat idle tampil statis dengan alpha tenang.
 * - Saat [activity] tersedia ditampilkan dengan efek ketik (typewriter-on).
 */
@Composable
fun LiveStatusCard(
    activity: String?,
    modifier: Modifier = Modifier,
    idleText: String = "Idle",
    busy: Boolean = false,
) {
    val dotColor = if (busy) Yellow500 else Green500

    // Pulse animation only while busy; static values otherwise (no battery cost).
    val dotAlpha: Float
    val textAlpha: Float
    if (busy) {
        val pulse = rememberInfiniteTransition(label = "liveStatusPulse")
        val pulseDot by pulse.animateFloat(
            initialValue = 0.30f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "liveDotAlpha",
        )
        val pulseText by pulse.animateFloat(
            initialValue = 0.70f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "liveTextAlpha",
        )
        dotAlpha = pulseDot
        textAlpha = pulseText
    } else {
        dotAlpha = IDLE_DOT_ALPHA
        textAlpha = 1f
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = dotAlpha)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalFontFamily.current,
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (activity != null) {
                    TypingStatusText(
                        text = activity,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                        minLines = STATUS_MIN_LINES,
                        maxLines = STATUS_MAX_LINES,
                    )
                } else {
                    Text(
                        text = idleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                        fontFamily = LocalFontFamily.current,
                        minLines = STATUS_MIN_LINES,
                        maxLines = STATUS_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
