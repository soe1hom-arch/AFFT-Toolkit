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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afft.app.ui.theme.Red500
import com.afft.app.ui.theme.Green500

enum class TimelineState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class TimelineStep(
    val title: String,
    val detail: String = "",
    val timestamp: String? = null,
    val state: TimelineState,
)

internal fun TimelineState.color(pendingColor: Color): Color =
    when (this) {
        TimelineState.PENDING -> pendingColor
        TimelineState.RUNNING -> Green500
        TimelineState.COMPLETED -> Green500
        TimelineState.FAILED -> Red500
    }

@Composable
fun OperationTimeline(
    steps: List<TimelineStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val isLast = index == steps.lastIndex
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.width(28.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimelineNode(step = step)
                    if (!isLast) {
                        Spacer(
                            modifier =
                                Modifier
                                    .width(2.dp)
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (step.state == TimelineState.COMPLETED) {
                                            Green500.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                        },
                                    ),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp),
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = LocalFontFamily.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (step.detail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = LocalFontFamily.current,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (step.timestamp != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = step.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = LocalFontFamily.current,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(step: TimelineStep) {
    val color = step.state.color(MaterialTheme.colorScheme.onSurfaceVariant)
    Box(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .size(14.dp)
                .clip(CircleShape)
                .then(
                    if (step.state == TimelineState.RUNNING) {
                        Modifier
                            .background(color.copy(alpha = 0.2f))
                            .border(2.dp, color, CircleShape)
                    } else {
                        Modifier.background(color)
                    },
                ),
    ) {
        if (step.state == TimelineState.RUNNING) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}
