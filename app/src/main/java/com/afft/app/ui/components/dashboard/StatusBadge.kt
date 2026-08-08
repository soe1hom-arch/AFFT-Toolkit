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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.afft.app.ui.theme.Cyan500
import com.afft.app.ui.theme.Green500
import com.afft.app.ui.theme.Red500
import com.afft.app.ui.theme.Yellow500

enum class StatusType {
    READY,
    WARNING,
    ERROR,
    INFO,
    RUNNING,
}

internal val StatusType.color: Color
    get() =
        when (this) {
            StatusType.READY -> Green500
            StatusType.WARNING -> Yellow500
            StatusType.ERROR -> Red500
            StatusType.INFO -> Cyan500
            StatusType.RUNNING -> Green500
        }

internal val StatusType.label: String
    get() =
        when (this) {
            StatusType.READY -> "READY"
            StatusType.WARNING -> "WARNING"
            StatusType.ERROR -> "ERROR"
            StatusType.INFO -> "INFO"
            StatusType.RUNNING -> "RUNNING"
        }

@Composable
fun StatusBadge(
    type: StatusType,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val color = type.color
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label ?: type.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = LocalFontFamily.current,
            color = color,
        )
    }
}
