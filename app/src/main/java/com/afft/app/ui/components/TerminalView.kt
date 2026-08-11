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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afft.app.ui.theme.*

private val TerminalBg = Color(0xFF0A0E14)
private val TerminalBorder = Color(0xFF1C2A3A)
private val TabActiveBg = Color(0xFF141B24)
private val GreenAccent = Color(0xFF00E676)
private val CyanAccent = Color(0xFF00BCD4)
private val YellowAccent = Color(0xFFFFD54F)
private val RedAccent = Color(0xFFFF5252)
private val TextDim = Color(0xFF607D8B)

@Composable
fun TerminalView(
    logs: List<String>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 400,
    isRunning: Boolean = false,
    progressPercent: Int = 0,
    currentPartition: String = "",
) {
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val showScrollHint = scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue - 100

    LaunchedEffect(logs.size) {
        if (scrollState.value >= scrollState.maxValue - 200) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier) {
        // ── Terminal tab bar ──
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(TerminalBorder)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Window buttons (traffic light style)
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(RedAccent, RoundedCornerShape(50))
                            .padding(end = 4.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(YellowAccent, RoundedCornerShape(50))
                            .padding(end = 4.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(GreenAccent, RoundedCornerShape(50))
                            .padding(end = 4.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))

                // Tab title
                Text(
                    "Console Output",
                    fontSize = 11.sp,
                    fontFamily = LocalFontFamily.current,
                    color = TextDim,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "${logs.size} lines",
                    fontSize = 10.sp,
                    fontFamily = LocalFontFamily.current,
                    color = TextDim,
                )
            }
        }

        // ── Terminal body ──
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(TerminalBg),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    // Top bar with a prompt-like header
                    Text(
                        text = "AFFT@android:~$ _",
                        fontSize = 11.sp,
                        fontFamily = LocalFontFamily.current,
                        color = GreenAccent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Log lines
                    logs.forEach { line ->
                        ColoredLogLine(text = line)
                    }

                    // Blinking cursor when running
                    if (isRunning) {
                        Text(
                            text = "█",
                            fontSize = 11.sp,
                            fontFamily = LocalFontFamily.current,
                            color = GreenAccent.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Scroll hint indicator
            if (showScrollHint) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .background(
                                TerminalBorder.copy(alpha = 0.8f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "⬆ scroll to see more",
                        fontSize = 9.sp,
                        fontFamily = LocalFontFamily.current,
                        color = TextDim,
                    )
                }
            }

            // Scan line overlay (subtle CRT effect)
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (y in 0 until size.height.toInt() step 4) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.015f),
                        topLeft = Offset(0f, y.toFloat()),
                        size = Size(size.width, 1f),
                    )
                }
            }
        }

        // ── Battery-style progress bar (payload extraction only) ──
        if (isRunning) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(TerminalBorder)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    // Partition info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "◆ ${if (currentPartition.isNotEmpty()) currentPartition else "Initializing..."}",
                            fontSize = 10.sp,
                            fontFamily = LocalFontFamily.current,
                            color = GreenAccent,
                        )
                        Text(
                            text = "$progressPercent%",
                            fontSize = 10.sp,
                            fontFamily = LocalFontFamily.current,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // Battery-style bar
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                    ) {
                        // Outer frame (battery body)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(
                                        Color(0xFF0A0A0A),
                                        RoundedCornerShape(3.dp),
                                    ),
                        ) {
                            // Fill
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val barWidth = size.width * (progressPercent / 100f)
                                if (barWidth > 0) {
                                    // Gradient green → cyan
                                    drawRoundRect(
                                        color = GreenAccent,
                                        topLeft = Offset.Zero,
                                        size = Size(barWidth, size.height),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                    )
                                    if (barWidth > 30.dp.toPx()) {
                                        drawRoundRect(
                                            color = CyanAccent.copy(alpha = 0.4f),
                                            topLeft = Offset((barWidth - 24.dp.toPx()).coerceAtLeast(0f), 0f),
                                            size = Size(24.dp.toPx(), size.height),
                                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                        )
                                    }
                                }
                            }
                            // Percentage in center
                            Text(
                                text = "$progressPercent%",
                                fontSize = 10.sp,
                                fontFamily = LocalFontFamily.current,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        // Battery tip
                        Box(
                            modifier =
                                Modifier
                                    .width(4.dp)
                                    .height(10.dp)
                                    .align(Alignment.CenterEnd)
                                    .offset(x = 4.dp)
                                    .background(
                                        if (progressPercent > 0) CyanAccent else Color(0xFF333333),
                                        RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp),
                                    ),
                        )
                    }

                    // Done message when complete
                    if (progressPercent >= 100 && currentPartition.startsWith("Done")) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✓ $currentPartition",
                            fontSize = 10.sp,
                            fontFamily = LocalFontFamily.current,
                            color = GreenAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColoredLogLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            text.startsWith("[ERROR]") || text.startsWith("[FAIL]") -> RedAccent
            text.startsWith("[OK]") -> GreenAccent
            text.startsWith("[WARN]") -> YellowAccent
            text.startsWith("[INFO]") || text.startsWith("=== ") -> CyanAccent
            else -> Color(0xFFC8D6E5) // ice blue default
        }

    Text(
        text = text,
        fontFamily = LocalFontFamily.current,
        fontSize = 11.sp,
        color = color,
        modifier = modifier,
        lineHeight = 16.sp,
    )
}
