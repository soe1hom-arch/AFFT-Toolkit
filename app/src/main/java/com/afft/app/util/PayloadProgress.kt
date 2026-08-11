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

package com.afft.app.util

data class PayloadProgress(
    val partition: String,
    val percent: Int,
)

/**
 * Parse satu baris output payload-dumper-go menjadi progress, atau null jika
 * baris tersebut bukan baris progress bar.
 *
 * Format: "system (821 MB) [========>       ] 45%"
 */
internal fun parsePayloadProgressLine(raw: String): PayloadProgress? {
    // Hapus ANSI escape sequences dari line
    val clean = raw.replace(Regex("\u001b\\[[0-9;]*[a-zA-Z]"), "").trim()
    if (clean.isEmpty()) return null

    val progressRegex = Regex("""([a-zA-Z_0-9.-]+)\s+\([^)]+\)\s*\[([=> ]+)\]\s*(\d+)%""")
    val match = progressRegex.find(clean) ?: return null

    return PayloadProgress(
        partition = match.groupValues[1],
        percent = match.groupValues[3].toIntOrNull() ?: 0,
    )
}
