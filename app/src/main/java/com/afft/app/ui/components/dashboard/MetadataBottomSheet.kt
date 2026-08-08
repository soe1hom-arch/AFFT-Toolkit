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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.R
import kotlinx.coroutines.launch
import java.util.Locale

private const val INTERACTIVE_VALUE_LENGTH_THRESHOLD = 20

private val NON_INTERACTIVE_TOKENS =
    setOf(
        "READY",
        "WARNING",
        "ERROR",
        "UNKNOWN",
        "INFO",
        "EXCELLENT",
        "GOOD",
        "CRITICAL",
        "EROFS",
        "EXT4",
        "F2FS",
        "YES",
        "NO",
        "TRUE",
        "FALSE",
        "NONE",
        "N/A",
    )

/**
 * Determines whether a metadata value deserves the interactive detail treatment.
 * Short values, status tokens, filesystem names, percentages and simple
 * yes/no values stay passive; long or truncated values open a bottom sheet.
 */
fun shouldBeInteractive(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.contains("…")) return true
    if (trimmed.contains("\n")) return true
    if (trimmed.length <= INTERACTIVE_VALUE_LENGTH_THRESHOLD) return false
    if (trimmed.uppercase(Locale.US) in NON_INTERACTIVE_TOKENS) return false
    val numeric = trimmed.removeSuffix("%").replace(",", "").replace(".", "")
    if (numeric.isNotEmpty() && numeric.all { it.isDigit() }) return false
    return true
}

data class MetadataSheetDetail(
    val title: String,
    val value: String,
    val description: String? = null,
    val allowShare: Boolean = true,
    val onOpenFolder: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataBottomSheet(
    detail: MetadataSheetDetail,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun copyValue() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(detail.title, detail.value))
        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = LocalFontFamily.current,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Tutup",
                            tint = LocalIconTint.current,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "VALUE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = LocalFontFamily.current,
                )
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = detail.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = LocalFontFamily.current,
                    )
                }

                if (detail.description != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = LocalFontFamily.current,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = LocalFontFamily.current,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = ::copyValue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }

                if (detail.onOpenFolder != null || detail.allowShare) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (detail.onOpenFolder != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    detail.onOpenFolder.invoke()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder_open),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Folder")
                            }
                            if (detail.allowShare) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        if (detail.allowShare) {
                            OutlinedButton(
                                onClick = {
                                    val sendIntent =
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, detail.title)
                                            putExtra(Intent.EXTRA_TEXT, detail.value)
                                        }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_share),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close")
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}