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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingOverlay(
    isRunning: Boolean,
    message: String = "Processing...",
    modifier: Modifier = Modifier,
    progressPercent: Float? = null, // null = indeterminate, 0f-100f = determinate
) {
    if (isRunning) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Bagian atas: spinner + message
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (progressPercent == null) {
                    // Indeterminate spinner
                    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(1200),
                                repeatMode = RepeatMode.Restart,
                            ),
                        label = "angle",
                    )
                    val arcProgress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(600, delayMillis = 600),
                                repeatMode = RepeatMode.Restart,
                            ),
                        label = "arcLen",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Canvas(modifier = Modifier.size(28.dp)) {
                            val sweepAngle = 60f + arcProgress * 240f
                            drawArc(
                                color = primaryColor,
                                startAngle = angle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height),
                            )
                        }
                    }
                } else {
                    // Determinate: tunjukkan persentase
                    Text(
                        text = "${progressPercent.toInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontFamily = LocalFontFamily.current,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar (battery style)
            val barProgress = if (progressPercent != null) progressPercent / 100f else 0f

            if (progressPercent != null) {
                // Determinate: battery-style bar with percentage fill
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(surfaceVariantColor, RoundedCornerShape(10.dp)),
                ) {
                    // Filled portion
                    Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                        val barWidth = size.width * barProgress
                        if (barWidth > 0) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = Offset.Zero,
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                            )
                        }
                    }
                    // Percentage text in the middle of the bar
                }
                // Mini percentage label below bar
                Text(
                    text = "${progressPercent.toInt()}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                // Indeterminate animated bar
                val infiniteTransition = rememberInfiniteTransition(label = "bar")
                val barProgressAnim by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1500),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "barProgress",
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(surfaceVariantColor, RoundedCornerShape(3.dp)),
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                        val barWidth = size.width * barProgressAnim
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset.Zero,
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
