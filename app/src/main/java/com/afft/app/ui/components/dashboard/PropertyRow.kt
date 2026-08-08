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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afft.app.R

@Composable
fun PropertyRow(
    name: String,
    value: String,
    modifier: Modifier = Modifier,
    valueMonospace: Boolean = true,
    iconRes: Int? = null,
    status: StatusType? = null,
    copyable: Boolean = false,
    tooltip: String? = null,
    interactive: Boolean = shouldBeInteractive(value),
    onCopyRequest: ((String) -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    var tooltipVisible by rememberSaveable(name) { mutableStateOf(false) }
    val canOpenDetails = interactive && onOpenDetails != null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (canOpenDetails) {
                            Modifier.clickable(onClick = { onOpenDetails.invoke() })
                        } else {
                            Modifier
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = LocalIconTint.current,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalFontFamily.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (valueMonospace) LocalFontFamily.current else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.9f),
            )

            if (status != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(status.color),
                )
            }

            if (tooltip != null) {
                IconButton(
                    onClick = { tooltipVisible = !tooltipVisible },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_info),
                        contentDescription = tooltip,
                        tint = LocalIconTint.current,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (copyable) {
                IconButton(
                    onClick = { onCopyRequest?.invoke(value) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy),
                        contentDescription = "Salin $name",
                        tint = LocalIconTint.current,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (canOpenDetails) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "Buka detail $name",
                    tint = LocalIconTint.current,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = tooltipVisible && tooltip != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = tooltip ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = LocalFontFamily.current,
                )
            }
        }
    }
}