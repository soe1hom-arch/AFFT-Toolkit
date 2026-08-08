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

package com.afft.app.ui.components.dashboard
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.LocalIconTint
import com.afft.app.ui.components.TypedText

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal val StatusType.iconRes: Int
    get() =
        when (this) {
            StatusType.READY -> R.drawable.ic_check_circle
            StatusType.WARNING -> R.drawable.ic_warning
            StatusType.ERROR -> R.drawable.ic_error
            StatusType.INFO -> R.drawable.ic_info
            StatusType.RUNNING -> R.drawable.ic_refresh
        }

data class StatusSection(
    val label: String,
    val value: String,
)

@Composable
fun StatusPanel(
    title: String,
    status: StatusType,
    message: String? = null,
    progress: Float? = null,
    sections: List<StatusSection> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val statusColor by animateColorAsState(targetValue = status.color, label = "statusColor")

    val cardAlpha = remember { Animatable(0f) }
    val slideOffset = remember { Animatable(-24f) }
    val iconScale = remember { Animatable(0.90f) }
    val badgeAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { cardAlpha.animateTo(1f, tween(durationMillis = 250)) }
            launch { slideOffset.animateTo(0f, tween(durationMillis = 250)) }
            launch {
                iconScale.animateTo(1.05f, tween(durationMillis = 350))
                iconScale.animateTo(1.00f, tween(durationMillis = 150))
            }
            launch { badgeAlpha.animateTo(1f, tween(durationMillis = 200)) }
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = cardAlpha.value
                    translationY = slideOffset.value
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(status.iconRes),
                    contentDescription = null,
                    tint = LocalIconTint.current,
                    modifier = Modifier.size(28.dp).graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = LocalFontFamily.current,
                    )
                    if (message != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        TypedText(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                StatusBadge(
                    type = status,
                    modifier = Modifier.graphicsLayer { alpha = badgeAlpha.value },
                )
            }

            if (sections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                )
                Spacer(modifier = Modifier.height(10.dp))
                sections.forEachIndexed { index, section ->
                    PropertyRow(name = section.label, value = section.value)
                    if (index < sections.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}