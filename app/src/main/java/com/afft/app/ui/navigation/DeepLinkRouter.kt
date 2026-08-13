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

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Meneruskan deep link (`afft://...`) dari MainActivity ke navigasi Compose.
 *
 * Event dikirim saat MainActivity menerima intent baru (onNewIntent);
 * MainScreen mengumpulkan event dan menavigasi ke rute yang diminta.
 */
object DeepLinkRouter {
    private val _events =
        MutableSharedFlow<AppScreen>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<AppScreen> = _events

    fun handle(uri: Uri?) {
        val screen = uri?.toAppScreen() ?: return
        _events.tryEmit(screen)
    }
}

/**
 * Memetakan URI deep link ke rute [AppScreen].
 *
 * Format: `afft://home`, `afft://files`, `afft://tools`,
 * `afft://tools/{toolId}` (toolId: payload | super | filesystem | boot).
 */
fun Uri.toAppScreen(): AppScreen? = appScreenFromDeepLink(scheme, host, pathSegments)

/** Pemetaan murni (tanpa Android Uri) agar mudah diuji unit. */
internal fun appScreenFromDeepLink(
    scheme: String?,
    host: String?,
    pathSegments: List<String>,
): AppScreen? {
    if (scheme != "afft") return null
    return when (host) {
        "home" -> AppScreen.Home
        "files" -> AppScreen.Files
        "tools" ->
            AppScreen.Tools(
                FirmwareTool.fromId(pathSegments.firstOrNull()) ?: FirmwareTool.PAYLOAD,
            )
        else -> null
    }
}
