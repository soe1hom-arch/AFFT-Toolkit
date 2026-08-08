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

package com.afft.app.service

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.afft.app.model.OperationResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Test AFFTService tanpa emulator/dependency baru:
 * FakeContext (MockContext bawaan android.jar) + testOptions
 * `returnDefaultValues=true` membuat method framework no-op/null.
 */
class AFFTServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /**
     * Fake Context: file di bawah folder temp, tanpa system service nyata.
     * Pakai subclass Application (bukan MockContext) karena android.test
     * tidak tersedia di unit test compileSdk 36.
     */
    private class FakeContext(private val root: File) : Application() {
        override fun getExternalFilesDir(type: String?): File = File(root, "external").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.afft.app"
        override fun getApplicationInfo(): ApplicationInfo =
            ApplicationInfo().apply {
                packageName = "com.afft.app"
                nativeLibraryDir = File(root, "libs").absolutePath
            }
    }

    private fun newService(): AFFTService = AFFTService(FakeContext(tmp.root))

    // ---------------- error handling ----------------

    @Test
    fun extractPayload_missingBinary_returnsFailureWithoutCrash() = runBlocking {
        val service = newService()
        val input = File(tmp.root, "payload.bin").apply { writeBytes(ByteArray(16)) }

        val result = service.extractPayload(input)

        assertFalse(result.ok)
        assertEquals("Extract Payload", result.title)
        assertTrue(result.message.contains("payload-dumper-go not found"))
        assertFalse(service.isRunning.value)
    }

    @Test
    fun extractPayload_missingFile_returnsFailure() = runBlocking {
        val service = newService()
        val missing = File(tmp.root, "tidak-ada.bin")

        val result = service.extractPayload(missing)

        assertFalse(result.ok)
    }

    // ---------------- invalid input ----------------

    @Test
    fun createFolder_rejectsInvalidNames() = runBlocking {
        val service = newService()
        val workDir = service.getWorkDir()

        assertFalse(service.createFolder(workDir, "../evil"))
        assertFalse(service.createFolder(workDir, "a/b"))
        assertFalse(service.createFolder(workDir, "   "))

        assertTrue(service.createFolder(workDir, "folder_valid"))
        assertTrue(File(workDir, "folder_valid").isDirectory)
    }

    @Test
    fun renameFile_rejectsInvalidNames() = runBlocking {
        val service = newService()
        val file = File(service.getWorkDir(), "asal.txt").apply { writeText("x") }

        assertFalse(service.renameFile(file, "a/b"))
        assertFalse(service.renameFile(file, ""))
        assertTrue(service.renameFile(file, "baru.txt"))
        assertTrue(File(service.getWorkDir(), "baru.txt").exists())
    }

    @Test
    fun deleteFileWithSafety_rejectsOutsideWorkDir() = runBlocking {
        val service = newService()
        val outside = File(tmp.root, "outside.txt").apply { writeText("x") }

        val result = service.deleteFileWithSafety(outside)

        assertFalse(result)
        assertTrue(outside.exists()) // tidak dihapus
    }

    @Test
    fun deleteFileWithSafety_deletesInsideWorkDir() = runBlocking {
        val service = newService()
        val inside = File(service.getWorkDir(), "inside.txt").apply { writeText("x") }

        assertTrue(service.deleteFileWithSafety(inside))
        assertFalse(inside.exists())
    }

    @Test
    fun checkStorageSpace_insufficient_returnsFalse() = runBlocking {
        val service = newService()
        val dest = File(service.getWorkDir(), "dest").apply { mkdirs() }

        // Ukuran tak masuk akal pasti melebihi ruang kosong
        assertFalse(service.checkStorageSpace(Long.MAX_VALUE, dest.absolutePath))
    }

    // ---------------- valid operations ----------------

    @Test
    fun copyFileTo_copiesAndVerifies() = runBlocking {
        val service = newService()
        val src = File(service.getWorkDir(), "src.bin").apply { writeBytes(ByteArray(8192) { 0x2A.toByte() }) }
        val destDir = File(service.getWorkDir(), "dest").apply { mkdirs() }

        val ok = service.copyFileTo(src, destDir)

        assertTrue(ok)
        val copied = File(destDir, "src.bin")
        assertTrue(copied.exists())
        assertEquals(src.length(), copied.length())
    }

    @Test
    fun copyFileTo_missingSource_fails() = runBlocking {
        val service = newService()
        val missing = File(service.getWorkDir(), "missing.bin")
        val destDir = File(service.getWorkDir(), "dest").apply { mkdirs() }

        assertFalse(service.copyFileTo(missing, destDir))
    }

    @Test
    fun getLatestInputFile_emptyDir_returnsNull() = runBlocking {
        val service = newService()
        assertNull(service.getLatestInputFile())

        File(service.getInputDir(), "a.img").writeBytes(ByteArray(4))
        assertEquals("a.img", service.getLatestInputFile()?.name)
    }

    // ---------------- cancellation ----------------

    @Test
    fun copyFileTo_respectsCancellation_noWritesAfterJoin() = runBlocking {
        val service = newService()
        val big = File(service.getWorkDir(), "big.bin")
        FileOutputStream(big).use { out ->
            val block = ByteArray(1 shl 20) // 1 MB
            repeat(128) { out.write(block) } // total 128 MB
        }
        val destDir = File(service.getWorkDir(), "dest").apply { mkdirs() }

        val job =
            launch(Dispatchers.Default) {
                service.copyFileTo(big, destDir)
            }
        delay(50)
        job.cancelAndJoin() // tidak boleh menggantung

        // Setelah join, tidak boleh ada penulisan lanjutan
        val dest = File(destDir, "big.bin")
        val size1 = if (dest.exists()) dest.length() else 0L
        delay(300)
        val size2 = if (dest.exists()) dest.length() else 0L
        assertEquals("copy must not continue after cancellation", size1, size2)
    }

    // ---------------- concurrent operations ----------------

    @Test
    fun concurrentFileOps_doNotCrash() = runBlocking {
        val service = newService()
        val workDir = service.getWorkDir()

        val jobs =
            (1..6).map { i ->
                launch(Dispatchers.IO) {
                    val folder = File(workDir, "folder_$i")
                    service.createFolder(workDir, "folder_$i")
                    val src = File(folder, "data.bin")
                    src.writeBytes(ByteArray(1024))
                    service.copyFileTo(src, File(workDir, "collect_$i").apply { mkdirs() })
                }
            }
        jobs.joinAll()

        (1..6).forEach { i ->
            assertTrue("folder_$i harus ada", File(workDir, "folder_$i").isDirectory)
        }
        assertFalse(service.isRunning.value)
    }
}
