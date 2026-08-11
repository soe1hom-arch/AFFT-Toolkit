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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.afft.app.R
import com.afft.app.ui.theme.AppFont
import com.afft.app.ui.theme.AppLanguage
import com.afft.app.ui.theme.FontController
import com.afft.app.ui.theme.LanguageController
import com.afft.app.ui.theme.LocalFontFamily
import com.afft.app.ui.theme.ThemeController
import com.afft.app.ui.theme.ThemeMode
import com.afft.app.ui.theme.ThemePreset

/** Isi halaman pengaturan (dipakai di dalam dialog About maupun sendiri). */
@Composable
fun SettingsContentPage(
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val english by LanguageController.language.collectAsState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        DialogHeader(
            iconRes = R.drawable.ic_settings,
            title = if (english.isEnglish) "Settings" else "Pengaturan",
            subtitle =
                if (english.isEnglish) "Language & appearance" else "Bahasa & tampilan",
            onDismiss = onDismiss,
            onBack = onBack,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            SettingsContent()
        }
    }
}

@Composable
private fun SettingsContent() {
    val english by LanguageController.language.collectAsState()
    val isEnglish = english.isEnglish

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // ── Language ──
            SectionTitle(if (isEnglish) "Language" else "Bahasa")
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEachIndexed { index, lang ->
                    SegmentedButton(
                        selected = english == lang,
                        onClick = { LanguageController.setLanguage(lang) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppLanguage.entries.size,
                            ),
                    ) {
                        Text(lang.displayName, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── Font ──
            SectionTitle(if (isEnglish) "Font" else "Font")
            Spacer(modifier = Modifier.height(8.dp))
            val selectedFont by FontController.font.collectAsState()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppFont.entries.forEachIndexed { index, font ->
                    SegmentedButton(
                        selected = selectedFont == font,
                        onClick = { FontController.setFont(font) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppFont.entries.size,
                            ),
                    ) { Text(font.displayName, fontSize = 12.sp) }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── Display mode ──
            SectionTitle(if (isEnglish) "Display Mode" else "Mode Tampilan")
            Spacer(modifier = Modifier.height(8.dp))
            val mode by ThemeController.mode.collectAsState()
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { ThemeController.setMode(m) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size,
                            ),
                    ) {
                        Text(
                            if (isEnglish) {
                                when (m) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.DARK -> "Dark"
                                    ThemeMode.LIGHT -> "Light"
                                }
                            } else {
                                m.displayName
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val dynamic by ThemeController.dynamicColor.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isEnglish) "Dynamic color" else "Warna dinamis",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isEnglish) {
                            "Use wallpaper-based accent (ignores preset)"
                        } else {
                            "Pakai warna berdasar wallpaper (abaikan preset)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = dynamic,
                    onCheckedChange = { ThemeController.setDynamicColor(it) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── Theme presets ──
            SectionTitle(if (isEnglish) "Theme Preset" else "Preset Tema")
            Spacer(modifier = Modifier.height(8.dp))
            val preset by ThemeController.preset.collectAsState()
            ThemePreset.entries.forEach { p ->
                ThemePresetOptionCard(
                    preset = p,
                    selected = preset == p,
                    english = isEnglish,
                    onClick = {
                        // Pilih preset harus tampil warnanya: reset custom accent
                        // agar tidak menimpa warna preset sebelumnya.
                        ThemeController.setPreset(p)
                        ThemeController.setAccent(null)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            CustomAccentSection(isEnglish = isEnglish)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            CustomIconTintSection(isEnglish = isEnglish)
        }
    }
}

/** Warna preset premium untuk aksen & warna icon — user cukup memilih. */
private val premiumColorPresets: List<Pair<String, Color>> =
    listOf(
        "AFFT Green" to Color(0xFF20E89A),
        "Midnight Cyan" to Color(0xFF22D3EE),
        "Amber Gold" to Color(0xFFFFB020),
        "Violet Nebula" to Color(0xFFA78BFA),
        "Cherry Red" to Color(0xFFFF6B5B),
        "Ocean Blue" to Color(0xFF3B82F6),
        "Rose Pink" to Color(0xFFF472B6),
        "Slate Gray" to Color(0xFFB0BCC6),
    )

@Composable
private fun ColorPresetGrid(
    presets: List<Pair<String, Color>>,
    selected: Color?,
    onPick: (Color) -> Unit,
) {
    presets.chunked(4).forEach { rowColors ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowColors.forEach { (name, color) ->
                val isSelected = selected != null && selected.value == color.value
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = 2.dp,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    shape = CircleShape,
                                )
                                .clickable { onPick(color) },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun CustomAccentSection(isEnglish: Boolean) {
    val currentAccent by ThemeController.accent.collectAsState()
    var r by remember { mutableFloatStateOf(0.126f) }
    var g by remember { mutableFloatStateOf(0.892f) }
    var b by remember { mutableFloatStateOf(0.615f) }
    LaunchedEffect(currentAccent) {
        val accent = currentAccent
        if (accent != null) {
            r = accent.red
            g = accent.green
            b = accent.blue
        }
    }
    val preview = Color(r, g, b)

    SectionTitle(if (isEnglish) "Custom Accent Color" else "Warna Aksen Kustom")
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(preview)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                if (isEnglish) "Choose any color" else "Pilih warna bebas",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "#${java.lang.String.format("%02X%02X%02X", (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = LocalFontFamily.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    ChannelSlider("R", r) { r = it }
    ChannelSlider("G", g) { g = it }
    ChannelSlider("B", b) { b = it }

    Spacer(modifier = Modifier.height(14.dp))
    SectionTitle(if (isEnglish) "Premium Presets" else "Preset Premium")
    Spacer(modifier = Modifier.height(8.dp))
    ColorPresetGrid(
        presets = premiumColorPresets,
        selected = currentAccent,
        onPick = { c ->
            r = c.red
            g = c.green
            b = c.blue
            ThemeController.setAccent(c)
        },
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { ThemeController.setAccent(preview) },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isEnglish) "Apply" else "Terapkan")
        }
        OutlinedButton(
            onClick = { ThemeController.setAccent(null) },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isEnglish) "Reset" else "Reset")
        }
    }
}

@Composable
private fun CustomIconTintSection(isEnglish: Boolean) {
    val currentTint by ThemeController.iconTint.collectAsState()
    var r by remember { mutableFloatStateOf(0.77f) }
    var g by remember { mutableFloatStateOf(0.79f) }
    var b by remember { mutableFloatStateOf(0.81f) }
    LaunchedEffect(currentTint) {
        val tint = currentTint
        if (tint != null) {
            r = tint.red
            g = tint.green
            b = tint.blue
        }
    }
    val preview = Color(r, g, b)

    SectionTitle(if (isEnglish) "Custom Icon Color" else "Warna Icon Kustom")
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_palette),
            contentDescription = if (isEnglish) "Icon color preview" else "Pratinjau warna icon",
            tint = preview,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                if (isEnglish) "Choose icon color" else "Pilih warna icon",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "#${java.lang.String.format("%02X%02X%02X", (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = LocalFontFamily.current,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        if (isEnglish) {
            "Applies to icons only. Text and cards keep the theme colors."
        } else {
            "Hanya memengaruhi icon. Teks & kartu tetap memakai warna tema."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(8.dp))
    ChannelSlider("R", r) { r = it }
    ChannelSlider("G", g) { g = it }
    ChannelSlider("B", b) { b = it }

    Spacer(modifier = Modifier.height(14.dp))
    SectionTitle(if (isEnglish) "Premium Presets" else "Preset Premium")
    Spacer(modifier = Modifier.height(8.dp))
    ColorPresetGrid(
        presets = premiumColorPresets,
        selected = currentTint,
        onPick = { c ->
            r = c.red
            g = c.green
            b = c.blue
            ThemeController.setIconTint(c)
        },
    )

    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { ThemeController.setIconTint(preview) },
            modifier = Modifier.weight(1f),
        ) { Text(if (isEnglish) "Apply" else "Terapkan") }
        OutlinedButton(
            onClick = { ThemeController.setIconTint(null) },
            modifier = Modifier.weight(1f),
        ) { Text(if (isEnglish) "Reset" else "Reset") }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (value * 255).toInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
}

@Composable
private fun ThemePresetOptionCard(
    preset: ThemePreset,
    selected: Boolean,
    english: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .border(
                    width = 1.dp,
                    color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) colorScheme.primaryContainer else colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThemePreset.preview(preset).forEach { color ->
                    Box(
                        modifier =
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(color),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    preset.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                )
                Text(
                    if (english) preset.descriptionEn else preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
            } else {
                Box(modifier = Modifier.size(24.dp))
            }
        }
    }
}
