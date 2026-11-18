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

    private fun fakeDebugfs(diagnostic: String) {
        val binary = File(tmp.root, "libs/libdebugfs.so")
        binary.parentFile!!.mkdirs()
        binary.writeText(
            """
            #!/bin/sh
            set -- ${'$'}2
            mkdir -p "${'$'}3"
            printf 'data' > "${'$'}3/file"
            printf '/ 0 0 0755\nfile 1000 1000 0644\n' > "${'$'}AFFT_FS_CONFIG_FILE"
            printf '%s\n' 'debugfs 1.47.0 (5-Feb-2023)' >&2
            $diagnostic
            exit 0
            """.trimIndent(),
        )
        check(binary.setExecutable(true))
    }

    private fun fakePayloadDumper(
        writeImage: Boolean,
        marker: Boolean = true,
        binary: File = File(tmp.root, "libs/libpayload-dumper-go.so"),
    ) {
        binary.parentFile!!.mkdirs()
        binary.writeText(
            """
            #!/bin/sh
            [ "${'$'}#" = 5 ] && [ "${'$'}1" = "-o" ] && [ "${'$'}3" = "-c" ] && [ "${'$'}4" = "1" ] && [ -f "${'$'}5" ] || exit 2
            mkdir -p "${'$'}2"
            ${if (writeImage) "printf 'partition' > \"${'$'}2/system.img\"" else ":"}
            ${if (marker) "echo AFFT_PAYLOAD_EXTRACTION_COMPLETE >&2" else ":"}
            exit 0
            """.trimIndent(),
        )
        check(binary.setExecutable(true))
    }

    private fun ext4Image(): File =
        File(tmp.root, "system.img").apply {
            writeBytes(ByteArray(0x43a).also {
                it[0x438] = 0x53
                it[0x439] = 0xef.toByte()
            })
        }

    @Test
    fun extractFilesystem_debugfsPartialExtractionIsFailureEvenWithZeroExit() = runBlocking {
        fakeDebugfs("echo 'rdump: Permission denied while creating symlink /vendor' >&2")
        val service = newService()

        val result = service.extractFilesystem(ext4Image())

        assertFalse(result.ok)
        assertTrue(result.message.contains("tidak lengkap"))
        assertTrue(result.message.contains("Permission denied"))
        assertTrue(service.progressMessage.value.contains("gagal"))
    }

    @Test
    fun extractFilesystem_debugfsBannerAloneDoesNotPreventSuccess() = runBlocking {
        fakeDebugfs("")
        val service = newService()

        val result = service.extractFilesystem(ext4Image())

        assertTrue(result.ok)
        assertTrue(result.outputPath.startsWith(File(tmp.root, "files/afft_work/filesystem/contents").absolutePath))
        assertTrue(service.getFilesystemConfigFile("system").isFile)
        assertTrue(service.logs.value.any { it == "[INFO] debugfs 1.47.0 (5-Feb-2023)" })
        assertFalse(service.logs.value.any { it == "[ERROR] debugfs 1.47.0 (5-Feb-2023)" })
        assertTrue(service.logs.value.any { it.contains(result.outputPath) })
    }

    @Test
    fun extractFilesystem_preservesLiveStatusWhenConsoleIsCleared() = runBlocking {
        fakeDebugfs("sleep 1")
        val service = newService()
        val operation = launch { service.extractFilesystem(ext4Image()) }
        while (!service.isRunning.value) delay(10)

        service.clearLogs()

        assertTrue(service.progressMessage.value.isNotBlank())
        operation.join()
    }

    @Test
    fun foregroundTimeout_reportsFailure() {
        var failure: String? = null
        AFFTExtractService.onFailure = { failure = it }
        try {
            AFFTExtractService().onTimeout(1, 0)
            assertTrue(failure?.contains("Batas waktu") == true)
        } finally {
            AFFTExtractService.onFailure = null
        }
    }

    @Test
    fun extractPayload_zeroExitWithoutImagesIsFailure() = runBlocking {
        fakePayloadDumper(writeImage = false)
        val input = File(tmp.root, "payload.bin").apply { writeText("payload") }
        val service = newService()
        File(service.getTempDir(), "Payload/stale.img").apply {
            parentFile!!.mkdirs()
            writeText("stale")
        }

        val result = service.extractPayload(input)

        assertFalse(result.ok)
        assertTrue(result.message.contains("file partisi .img"))
        assertEquals("stale", File(service.getTempDir(), "Payload/stale.img").readText())
    }

    @Test
    fun extractPayload_zeroExitWithImageIsSuccess() = runBlocking {
        fakePayloadDumper(writeImage = true)
        val input = File(tmp.root, "payload.bin").apply { writeText("payload") }

        val result = newService().extractPayload(input)

        assertTrue(result.ok)
        assertTrue(File(result.outputPath, "system.img").isFile)
        assertTrue(result.message.contains("1 img"))
    }

    @Test
    fun extractPayload_repeatedRunsPreservePreviousImages() = runBlocking {
        fakePayloadDumper(writeImage = true)
        val input = File(tmp.root, "payload.bin").apply { writeText("payload") }
        val service = newService()
        val first = service.extractPayload(input)
        File(first.outputPath, "system.img").writeText("previous")

        val second = service.extractPayload(input)

        assertTrue(first.ok)
        assertTrue(second.ok)
        assertFalse(first.outputPath == second.outputPath)
        assertEquals("previous", File(first.outputPath, "system.img").readText())
        assertEquals("partition", File(second.outputPath, "system.img").readText())
    }

    @Test
    fun extractPayload_oldDumperCannotReportSuccessWithPartialImage() = runBlocking {
        fakePayloadDumper(writeImage = true, marker = false)
        val input = File(tmp.root, "payload.bin").apply { writeText("payload") }

        val result = newService().extractPayload(input)

        assertFalse(result.ok)
        assertTrue(result.message.contains("tidak kompatibel"))
    }

    @Test
    fun extractPayload_fallbackUsesFreshDirectoryAndCorrectArguments() = runBlocking {
        val primary = File(tmp.root, "libs/libpayload-dumper-go.so")
        primary.parentFile!!.mkdirs()
        primary.writeText(
            """
            #!/bin/sh
            printf 'partial' > "${'$'}2/system.img"
            echo 'CANNOT LINK' >&2
            exit 1
            """.trimIndent(),
        )
        check(primary.setExecutable(true))
        val binDir = File(tmp.root, "files/afft_bin").apply { mkdirs() }
        File(binDir, "liblzma.so.5").writeText("unused")
        File(binDir, "payload-dumper-go").writeText("old binary")
        fakePayloadDumper(writeImage = true, binary = File(binDir, "payload-dumper-go-afft-v1"))
        val input = File(tmp.root, "payload.bin").apply { writeText("payload") }
        val service = newService()

        val result = service.extractPayload(input)

        assertTrue(result.ok)
        assertEquals("partition", File(result.outputPath, "system.img").readText())
        val outputs = File(service.getTempDir(), "Payload").listFiles()!!.filter { it.isDirectory }
        assertEquals(2, outputs.size)
        assertTrue(outputs.any { File(it, "system.img").readText() == "partial" })
    }

    @Test
    fun repackFilesystem_passesSavedFsConfigToErofsMaker() = runBlocking {
        fakeDebugfs("")
        val binary = File(tmp.root, "libs/libmkfs.erofs.so")
        binary.parentFile!!.mkdirs()
        binary.writeText(
            """
            #!/bin/sh
            [ "${'$'}1" = "-z" ] && [ "${'$'}2" = "lz4hc,9" ] || exit 2
            case "${'$'}5" in --fs-config-file=*/system.txt) ;; *) exit 3 ;; esac
            printf 'image' > "${'$'}6"
            """.trimIndent(),
        )
        check(binary.setExecutable(true))
        val service = newService()
        val extractResult = service.extractFilesystem(ext4Image())
        check(extractResult.ok)

        val configFile = service.getFilesystemConfigFile("system")
        val result = service.repackFilesystem("system")

        assertTrue(result.ok)
        assertTrue(configFile.isFile)
    }

    @Test
    fun repackFilesystem_missingRequiredFsConfigIsFailure() = runBlocking {
        fakeDebugfs("")
        val service = newService()
        val extractResult = service.extractFilesystem(ext4Image())
        check(extractResult.ok)
        check(service.getFilesystemConfigFile("system").delete())

        val result = service.repackFilesystem("system")

        assertFalse(result.ok)
        assertTrue(result.message.contains("Metadata UID/GID/mode"))
    }

    @Test
    fun repackFilesystem_compressionFailureFallsBackWithoutCompressionFlag() = runBlocking {
        val binary = File(tmp.root, "libs/libmkfs.erofs.so")
        binary.parentFile!!.mkdirs()
        binary.writeText(
            """
            #!/bin/sh
            if [ "${'$'}1" = "-z" ]; then exit 1; fi
            [ "${'$'}1" = "-C" ] && [ "${'$'}2" = "4096" ] || exit 2
            printf 'image' > "${'$'}3"
            """.trimIndent(),
        )
        check(binary.setExecutable(true))
        val source = File(tmp.root, "filesystem").apply { mkdirs() }
        File(source, "file").writeText("data")

        val result = newService().repackFilesystem("system", source.absolutePath)

        assertTrue(result.ok)
        assertTrue(result.message.contains("uncompressed"))
    }

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

    private fun fakeMagiskBoot(script: String) {
        val binary = File(tmp.root, "libs/libmagiskboot.so")
        binary.parentFile!!.mkdirs()
        binary.writeText("#!/bin/sh\n$script\n")
        check(binary.setExecutable(true))
    }

    private fun bootSource(name: String): File {
        val dir = File(tmp.root, "boot-source").apply { mkdirs() }
        File(dir, name).writeText("original")
        return dir
    }

    @Test
    fun repackBoot_failedProcessWithOutputIsNotReportedAsSuccess() = runBlocking {
        fakeMagiskBoot("printf partial > new-boot.img\nexit 1")
        val source = bootSource("boot.img")
        val service = newService()

        val result = service.repackBoot("boot.img", source.absolutePath)

        assertFalse(result.ok)
        assertTrue(result.message.contains("exit 1"))
        assertTrue(service.progressMessage.value.contains("gagal"))
        assertFalse(File(service.getTempDir(), "repacked/boot.img").exists())
        assertFalse(File(source, "new-boot.img").exists())
    }

    @Test
    fun repackBoot_existingOutputIsPreservedAndRejected() = runBlocking {
        fakeMagiskBoot("printf new > new-boot.img\nexit 0")
        val source = bootSource("boot.img")
        val existing = File(source, "new-boot.img").apply { writeText("existing") }
        val service = newService()

        val result = service.repackBoot("boot.img", source.absolutePath)

        assertFalse(result.ok)
        assertTrue(result.message.contains("sudah ada"))
        assertEquals("existing", existing.readText())
    }

    @Test
    fun repackBoot_missingOutputIsFailure() = runBlocking {
        fakeMagiskBoot("exit 0")
        val source = bootSource("boot.img")
        val service = newService()

        val result = service.repackBoot("boot.img", source.absolutePath)

        assertFalse(result.ok)
        assertTrue(result.message.contains("tidak menghasilkan"))
    }

    @Test
    fun repackBoot_emptyOutputIsFailure() = runBlocking {
        fakeMagiskBoot(": > new-boot.img\nexit 0")
        val source = bootSource("boot.img")
        val service = newService()

        val result = service.repackBoot("boot.img", source.absolutePath)

        assertFalse(result.ok)
        assertTrue(result.message.contains("kosong"))
        assertFalse(File(source, "new-boot.img").exists())
    }

    @Test
    fun repackVbmetaDoesNotClaimAvb2Signing() = runBlocking {
        fakeMagiskBoot("printf image > new-boot.img\nexit 0")
        val source = bootSource("vbmeta.img")
        val service = newService()

        val result = service.repackBoot("vbmeta.img", source.absolutePath)

        assertFalse(result.ok)
        assertTrue(result.message.contains("AVB 2.0"))
        assertTrue(result.message.contains("Jangan flash"))
        assertTrue(File(result.outputPath).isFile)
        assertFalse(File(source, "new-boot.img").exists())
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
    fun deleteFileWithSafety_protectsRootAndReportsFullPaths() = runBlocking {
        val work = File(tmp.root, "work").apply { mkdirs() }
        val downloads = File(tmp.root, "downloads/AFFT").apply { mkdirs() }
        val protectedFile = File(downloads, "keep").apply { writeText("keep") }
        val deleted = File(work, "contents").apply { mkdirs() }
        val messages = mutableListOf<String>()
        val storage = StorageManager({ work }, { downloads }, messages::add, {})

        assertFalse(storage.deleteFileWithSafety(downloads))
        assertTrue(protectedFile.exists())
        assertTrue(messages.any { it.contains(downloads.canonicalPath) && it.contains("dilindungi") })
        assertTrue(storage.deleteFileWithSafety(deleted))
        assertTrue(messages.any { it.contains(deleted.canonicalPath) })
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
