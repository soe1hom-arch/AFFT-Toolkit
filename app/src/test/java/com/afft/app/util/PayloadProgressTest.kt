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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadProgressTest {
    @Test
    fun parsesStandardProgressBar() {
        val result = parsePayloadProgressLine("system (821 MB) [========>       ] 45%")
        assertEquals(PayloadProgress("system", 45), result)
    }

    @Test
    fun parsesAnsiProgressBar() {
        val result =
            parsePayloadProgressLine("\u001b[2K\u001b[1A\u001b[2K product (1.2 GB) [==>          ] 12%\u001b[0K")
        assertEquals(PayloadProgress("product", 12), result)
    }

    @Test
    fun ignoresNonProgressLines() {
        assertNull(parsePayloadProgressLine(""))
        assertNull(parsePayloadProgressLine("   "))
        assertNull(parsePayloadProgressLine("Extracting system"))
        assertNull(parsePayloadProgressLine("Found partitions: system, product"))
    }
}
