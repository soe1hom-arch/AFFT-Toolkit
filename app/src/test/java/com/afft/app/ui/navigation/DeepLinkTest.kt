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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun homeRoute_mapsToHome() {
        assertEquals(AppScreen.Home, appScreenFromDeepLink("afft", "home", emptyList()))
    }

    @Test
    fun filesRoute_mapsToFiles() {
        assertEquals(AppScreen.Files, appScreenFromDeepLink("afft", "files", emptyList()))
    }

    @Test
    fun toolsRoute_withoutTool_defaultsToPayload() {
        assertEquals(
            AppScreen.Tools(FirmwareTool.PAYLOAD),
            appScreenFromDeepLink("afft", "tools", emptyList()),
        )
    }

    @Test
    fun toolsRoute_withToolId_mapsToTool() {
        assertEquals(
            AppScreen.Tools(FirmwareTool.SUPER),
            appScreenFromDeepLink("afft", "tools", listOf("super")),
        )
    }

    @Test
    fun toolsRoute_withUnknownToolId_fallsBackToPayload() {
        assertEquals(
            AppScreen.Tools(FirmwareTool.PAYLOAD),
            appScreenFromDeepLink("afft", "tools", listOf("unknown")),
        )
    }

    @Test
    fun nonAfftScheme_isRejected() {
        assertNull(appScreenFromDeepLink("https", "tools", listOf("payload")))
    }

    @Test
    fun unknownHost_isRejected() {
        assertNull(appScreenFromDeepLink("afft", "other", emptyList()))
    }

    @Test
    fun nullScheme_isRejected() {
        assertNull(appScreenFromDeepLink(null, "home", emptyList()))
    }
}
