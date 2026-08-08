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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afft.app.R
import com.afft.app.ui.theme.Cyan500
import com.afft.app.ui.theme.Green500
import com.afft.app.ui.theme.Red500
import com.afft.app.ui.theme.Yellow500
import com.afft.app.ui.theme.LocalIconTint
import com.afft.app.ui.components.TypedText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class HealthLevel { EXCELLENT, GOOD, WARNING, CRITICAL }

internal val HealthLevel.color: Color
    get() =
        when (this) {
            HealthLevel.EXCELLENT -> Green500
            HealthLevel.GOOD -> Cyan500
            HealthLevel.WARNING -> Yellow500
            HealthLevel.CRITICAL -> Red500
        }

enum class ValidationState { READY, WARNING, ERROR, UNKNOWN }

internal fun ValidationState.toStatusType(): StatusType =
    when (this) {
        ValidationState.READY -> StatusType.READY
        ValidationState.WARNING -> StatusType.WARNING
        ValidationState.ERROR -> StatusType.ERROR
        ValidationState.UNKNOWN -> StatusType.INFO
    }

enum class IndicatorKind { POSITIVE, WARNING, CRITICAL }

internal val IndicatorKind.color: Color
    get() =
        when (this) {
            IndicatorKind.POSITIVE -> Green500
            IndicatorKind.WARNING -> Yellow500
            IndicatorKind.CRITICAL -> Red500
        }

internal val IndicatorKind.iconRes: Int
    get() =
        when (this) {
            IndicatorKind.POSITIVE -> R.drawable.ic_check_circle
            IndicatorKind.WARNING -> R.drawable.ic_warning
            IndicatorKind.CRITICAL -> R.drawable.ic_error
        }

data class HealthIndicator(
    val text: String,
    val kind: IndicatorKind = IndicatorKind.POSITIVE,
)

data class InspectorRow(
    val label: String,
    val value: String,
    val iconRes: Int? = null,
    val copyable: Boolean = false,
    val status: StatusType? = null,
    val tooltip: String? = null,
    val mono: Boolean = true,
)

data class InspectorSection(
    val title: String,
    val rows: List<InspectorRow>,
    val defaultExpanded: Boolean = true,
)

data class ValidationPanel(
    val status: ValidationState,
    val reason: String,
    val suggestedAction: String,
    val estimatedCompatibility: String,
)

data class FirmwareMetadata(
    val headline: String = "Firmware Inspector",
    val healthScore: Int = 0,
    val healthLevel: HealthLevel = HealthLevel.EXCELLENT,
    val healthIndicators: List<HealthIndicator> = emptyList(),
    val validationPanel: ValidationPanel? = null,
    val sections: List<InspectorSection> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
)

fun emptyFirmwareMetadata(): FirmwareMetadata = FirmwareMetadata(isEmpty = true)

fun demoPayloadMetadata(): FirmwareMetadata {
    val general =
        InspectorSection(
            title = "General",
            rows =
                listOf(
                    InspectorRow("Filename", "payload.bin", iconRes = R.drawable.ic_insert_drive_file, copyable = true),
                    InspectorRow("File Size", "4.4 GB", iconRes = R.drawable.ic_storage),
                    InspectorRow(
                        "SHA256",
                        "a3f9b2c7…7c21d4e8",
                        copyable = true,
                        tooltip = "SHA-256 hash dari payload.bin",
                    ),
                    InspectorRow("Last Modified", "2026-08-01 09:42", iconRes = R.drawable.ic_description),
                ),
        )
    val android =
        InspectorSection(
            title = "Android",
            rows =
                listOf(
                    InspectorRow("Android Version", "15 (VanillaIceCream)", iconRes = R.drawable.ic_phone_android, copyable = true),
                    InspectorRow("Device Codename", "shiba", copyable = true),
                    InspectorRow("Device Name", "Pixel 8"),
                    InspectorRow(
                        "Build Fingerprint",
                        "google/shiba/shiba:15/AP3A.240805",
                        copyable = true,
                        tooltip = "Fingerprint build dari OTA",
                    ),
                    InspectorRow("Security Patch", "2026-08-01", copyable = true),
                ),
        )
    val technical =
        InspectorSection(
            title = "Technical",
            defaultExpanded = false,
            rows =
                listOf(
                    InspectorRow("Dynamic Partition", "Yes", iconRes = R.drawable.ic_super, status = StatusType.READY),
                    InspectorRow("Compression", "lz4 / xz", copyable = true),
                    InspectorRow("Partition Count", "54", iconRes = R.drawable.ic_list, copyable = true),
                    InspectorRow("Filesystem", "ext4", iconRes = R.drawable.ic_filesystem, copyable = true),
                    InspectorRow("Architecture", "arm64-v8a", iconRes = R.drawable.ic_code, copyable = true),
                ),
        )
    return FirmwareMetadata(
        headline = "Firmware Inspector",
        healthScore = 98,
        healthLevel = HealthLevel.EXCELLENT,
        healthIndicators =
            listOf(
                HealthIndicator("Complete"),
                HealthIndicator("Supported"),
                HealthIndicator("Verified"),
            ),
        validationPanel =
            ValidationPanel(
                status = ValidationState.READY,
                reason = "Supported firmware",
                suggestedAction = "Ready for Extraction",
                estimatedCompatibility = "Estimated extraction supported",
            ),
        sections = listOf(general, android, technical),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareInspector(
    metadata: FirmwareMetadata,
    modifier: Modifier = Modifier,
    emptyTitle: String = "No payload selected",
    emptyDescription: String = "Select a payload.bin file to begin analysis",
    emptyIconRes: Int = R.drawable.ic_folder_off,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheetDetail by remember { mutableStateOf<MetadataSheetDetail?>(null) }
    val onCopy: (String, String) -> Unit = { value, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
    }
    val onOpenDetails: (MetadataSheetDetail) -> Unit = { detail -> sheetDetail = detail }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            FadeBox(delayMs = 0L, modifier = Modifier.fillMaxWidth()) {
                when {
                    metadata.isEmpty ->
                        EmptyState(
                            title = emptyTitle,
                            description = emptyDescription,
                            iconRes = emptyIconRes,
                        )
                    metadata.isLoading -> SkeletonInspector()
                    else ->
                        FilledInspector(
                            metadata = metadata,
                            onCopy = onCopy,
                            onOpenDetails = onOpenDetails,
                        )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    sheetDetail?.let { detail ->
        MetadataBottomSheet(
            detail = detail,
            onDismiss = { sheetDetail = null },
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    iconRes: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Firmware Inspector",
            style = MaterialTheme.typography.titleSmall,
            fontFamily = LocalFontFamily.current,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = LocalIconTint.current,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        TypedText(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        TypedText(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "INSPECTOR INFO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = LocalFontFamily.current,
        )
        Spacer(modifier = Modifier.height(8.dp))
        EmptyStateHint(
            iconRes = R.drawable.ic_phone_android,
            text = "Identitas firmware (device, codename, versi Android)",
        )
        Spacer(modifier = Modifier.height(6.dp))
        EmptyStateHint(
            iconRes = R.drawable.ic_content_copy,
            text = "Hash & verifikasi (SHA-256, build fingerprint, patch keamanan)",
        )
        Spacer(modifier = Modifier.height(6.dp))
        EmptyStateHint(
            iconRes = R.drawable.ic_check_circle,
            text = "Health score, status validasi, dan rekomendasi tindakan",
        )
    }
}

@Composable
private fun EmptyStateHint(
    iconRes: Int,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = LocalIconTint.current,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = LocalFontFamily.current,
        )
    }
}

@Composable
private fun SkeletonInspector() {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        repeat(4) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier =
                        Modifier
                            .width(90.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(
                    modifier =
                        Modifier
                            .size(width = 140.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FilledInspector(
    metadata: FirmwareMetadata,
    onCopy: (String, String) -> Unit,
    onOpenDetails: (MetadataSheetDetail) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_archive),
                contentDescription = null,
                tint = LocalIconTint.current,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = metadata.headline,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = LocalFontFamily.current,
                modifier = Modifier.weight(1f),
            )
            metadata.validationPanel?.let { panel ->
                StatusBadge(
                    type = panel.status.toStatusType(),
                    label = panel.status.name,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HealthBlock(metadata = metadata)

        Spacer(modifier = Modifier.height(16.dp))

        metadata.sections.forEachIndexed { sectionIndex, section ->
            FadeBox(delayMs = (80 + sectionIndex * 160).toLong(), modifier = Modifier.fillMaxWidth()) {
                CollapsibleSection(
                    section = section,
                    sectionIndex = sectionIndex,
                    onCopy = onCopy,
                    onOpenDetails = onOpenDetails,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(10.dp))
        }

        metadata.validationPanel?.let { panel ->
            FadeBox(
                delayMs = (80 + metadata.sections.size * 160).toLong(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollapsibleValidationSection(
                    panel = panel,
                    onCopy = onCopy,
                    onOpenDetails = onOpenDetails,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    section: InspectorSection,
    sectionIndex: Int,
    onCopy: (String, String) -> Unit,
    onOpenDetails: (MetadataSheetDetail) -> Unit,
) {
    var expanded by rememberSaveable(section.title) { mutableStateOf(section.defaultExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevronSection",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = LocalFontFamily.current,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = if (expanded) "Tutup ${section.title}" else "Buka ${section.title}",
                tint = LocalIconTint.current,
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                section.rows.forEachIndexed { rowIndex, row ->
                    FadeBox(delayMs = (sectionIndex * 160 + rowIndex * 40).toLong()) {
                        PropertyRow(
                            name = row.label,
                            value = row.value,
                            iconRes = row.iconRes,
                            status = row.status,
                            copyable = row.copyable,
                            tooltip = row.tooltip,
                            valueMonospace = row.mono,
                            onCopyRequest = { value -> onCopy(value, row.label) },
                            onOpenDetails = {
                                onOpenDetails(
                                    MetadataSheetDetail(
                                        title = row.label,
                                        value = row.value,
                                        description = row.tooltip,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (rowIndex < section.rows.lastIndex) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleValidationSection(
    panel: ValidationPanel,
    onCopy: (String, String) -> Unit,
    onOpenDetails: (MetadataSheetDetail) -> Unit,
) {
    var expanded by rememberSaveable("validation") { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevronValidation",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "VALIDATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = LocalFontFamily.current,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = if (expanded) "Tutup Validation" else "Buka Validation",
                tint = LocalIconTint.current,
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Validation Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = LocalFontFamily.current,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    StatusBadge(
                        type = panel.status.toStatusType(),
                        label = panel.status.name,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                PropertyRow(
                    name = "Reason",
                    value = panel.reason,
                    onOpenDetails = {
                        onOpenDetails(
                            MetadataSheetDetail(
                                title = "Reason",
                                value = panel.reason,
                                description = "Alasan hasil validasi firmware",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PropertyRow(
                    name = "Suggested Action",
                    value = panel.suggestedAction,
                    status = panel.status.toStatusType(),
                    copyable = true,
                    onCopyRequest = { value -> onCopy(value, "Suggested Action") },
                    onOpenDetails = {
                        onOpenDetails(
                            MetadataSheetDetail(
                                title = "Suggested Action",
                                value = panel.suggestedAction,
                                description = "Tindakan yang disarankan untuk langkah berikutnya",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PropertyRow(
                    name = "Estimated Compatibility",
                    value = panel.estimatedCompatibility,
                    copyable = true,
                    tooltip = "Perkiraan dukungan extractor terhadap firmware ini",
                    onCopyRequest = { value -> onCopy(value, "Estimated Compatibility") },
                    onOpenDetails = {
                        onOpenDetails(
                            MetadataSheetDetail(
                                title = "Estimated Compatibility",
                                value = panel.estimatedCompatibility,
                                description = "Perkiraan dukungan extractor terhadap firmware ini",
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HealthBlock(metadata: FirmwareMetadata) {
    val healthColor = metadata.healthLevel.color
    val score = remember(metadata.healthScore) { Animatable(0f) }
    LaunchedEffect(metadata.healthScore) {
        score.snapTo(0f)
        score.animateTo(metadata.healthScore.toFloat(), tween(durationMillis = 600))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(healthColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${score.value.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = healthColor,
                fontFamily = LocalFontFamily.current,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata.healthLevel.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                color = healthColor,
                fontFamily = LocalFontFamily.current,
            )
            Spacer(modifier = Modifier.height(6.dp))
            metadata.healthIndicators.forEach { indicator ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(indicator.kind.iconRes),
                        contentDescription = null,
                        tint = indicator.kind.color,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = indicator.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = LocalFontFamily.current,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun FadeBox(
    delayMs: Long,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val cardAlpha = remember { Animatable(0f) }
    val slideOffset = remember { Animatable(24f) }

    LaunchedEffect(delayMs) {
        delay(delayMs)
        coroutineScope {
            launch { cardAlpha.animateTo(1f, tween(durationMillis = 300)) }
            launch { slideOffset.animateTo(0f, tween(durationMillis = 300)) }
        }
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                alpha = cardAlpha.value
                translationY = slideOffset.value
            },
    ) {
        content()
    }
}