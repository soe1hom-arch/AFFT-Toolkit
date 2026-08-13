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

package com.afft.app.ui.navigation

import androidx.compose.runtime.saveable.Saver

/** Tool firmware yang tersedia di Tools Hub. */
enum class FirmwareTool(
    val id: String,
    val label: String,
) {
    PAYLOAD("payload", "Payload"),
    SUPER("super", "Super"),
    FILESYSTEM("filesystem", "Filesystem"),
    BOOT("boot", "Boot"),
    ;

    companion object {
        fun fromId(id: String?): FirmwareTool? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Rute layar utama — pengganti string section.
 *
 * Tipe aman saat compile: salah ketik rute terdeteksi, dan rute bisa
 * membawa argumen (mis. [AppScreen.Tools.tool]).
 */
sealed class AppScreen {
    data object Home : AppScreen()

    data class Tools(
        val tool: FirmwareTool = FirmwareTool.PAYLOAD,
    ) : AppScreen()

    data object Files : AppScreen()

    /** Kunci unik per layar, dipakai SaveableStateProvider agar state bertahan. */
    val key: String
        get() =
            when (this) {
                Home -> "home"
                is Tools -> "tools:${tool.id}"
                Files -> "files"
            }
}

/** Me-rekonstruksi [AppScreen] dari [AppScreen.key]. */
private fun screenFromKey(key: String): AppScreen? =
    when {
        key == AppScreen.Home.key -> AppScreen.Home
        key == AppScreen.Files.key -> AppScreen.Files
        key.startsWith("tools:") ->
            AppScreen.Tools(
                FirmwareTool.fromId(key.removePrefix("tools:")) ?: FirmwareTool.PAYLOAD,
            )
        else -> null
    }

/** Saver agar [AppScreen] bisa disimpan via rememberSaveable. */
val AppScreenSaver: Saver<AppScreen, String> =
    Saver(
        save = { screen -> screen.key },
        restore = { value -> screenFromKey(value) ?: AppScreen.Home },
    )

/** Saver untuk back-stack [AppScreen] (rute dipisah '|'). */
val AppScreenListSaver: Saver<List<AppScreen>, String> =
    Saver(
        save = { list -> list.joinToString("|") { it.key } },
        restore = { value ->
            val screens = value.split("|").mapNotNull { screenFromKey(it) }
            if (screens.isEmpty()) listOf(AppScreen.Home) else screens
        },
    )
