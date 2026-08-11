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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShellExecutorTest {
    // Binary asli yang dibundel di repo: dynamic (PIE, PT_INTERP) dan static.
    // Path relatif terhadap module app/ (working dir unit test).
    private val dynamicElf = File("src/main/jniLibs/arm64-v8a/libpayload-dumper-go.so")
    private val staticElf = File("src/main/jniLibs/arm64-v8a/libextract.erofs.so")

    @Test
    fun detectsDynamicElf() {
        assertTrue("payload-dumper-go harus terdeteksi dynamic", dynamicElf.exists())
        assertTrue(ShellExecutor.isDynamicElf(dynamicElf))
    }

    @Test
    fun detectsStaticElf() {
        assertTrue("extract.erofs harus ada", staticElf.exists())
        assertFalse(ShellExecutor.isDynamicElf(staticElf))
    }

    @Test
    fun nonElfIsNotDynamic() {
        val tmp = File.createTempFile("not-elf", ".bin")
        try {
            tmp.writeBytes(ByteArray(256) { 0x41.toByte() })
            assertFalse(ShellExecutor.isDynamicElf(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun missingFileAssumesDynamic() {
        // Sesuai kontrak: jika file tidak bisa dibaca, asumsikan dynamic
        // agar fallback linker64 tetap dicoba.
        assertTrue(ShellExecutor.isDynamicElf(File("/nonexistent/definitely-missing.bin")))
    }

    // ---------------- cancellation ----------------

    @Test
    fun execute_respectsCancellation_destroysProcessQuickly() = runBlocking {
        val started = System.currentTimeMillis()
        val job =
            launch(Dispatchers.Default) {
                ShellExecutor.execute(listOf("sleep", "60"))
            }
        delay(300)
        job.cancelAndJoin()
        val elapsed = System.currentTimeMillis() - started

        // Sebelum fix: waitFor() blocking membuat cancelAndJoin menunggu 60 detik.
        // Setelah fix: delay-poll mendeteksi cancellation dan memusnahkan proses.
        assertTrue("cancellation must stop the process quickly (took ${elapsed}ms)", elapsed < 15000)
    }
}
