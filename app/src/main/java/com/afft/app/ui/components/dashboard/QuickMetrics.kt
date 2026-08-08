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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class QuickMetric(
    val label: String,
    val value: String,
    val iconRes: Int,
    val status: StatusType = StatusType.INFO,
    val statusLabel: String = "",
)

@Composable
fun QuickMetrics(
    metrics: List<QuickMetric>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onMetricClick: ((Int) -> Unit)? = null,
) {
    val rows = metrics.chunked(columns.coerceAtLeast(1))
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, metric ->
                    val metricIndex = rowIndex * columns + colIndex
                    QuickMetricTile(
                        metric = metric,
                        onClick = onMetricClick?.let { click -> { click(metricIndex) } },
                        modifier = Modifier.weight(1f),
                    )
                    if (colIndex < row.lastIndex) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                }
                if (row.size < columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun QuickMetricTile(
    metric: QuickMetric,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val cardAlpha = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.96f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { cardAlpha.animateTo(1f, tween(durationMillis = 200)) }
            launch { cardScale.animateTo(1f, tween(durationMillis = 200)) }
        }
    }

    Card(
        modifier =
            modifier
                .graphicsLayer {
                    alpha = cardAlpha.value
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                painter = painterResource(metric.iconRes),
                contentDescription = null,
                tint = LocalIconTint.current,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleMedium,
                color = metric.status.color,
                fontFamily = LocalFontFamily.current,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalFontFamily.current,
            )
            if (metric.statusLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metric.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = metric.status.color.copy(alpha = 0.85f),
                    fontFamily = LocalFontFamily.current,
                )
            }
        }
    }
}