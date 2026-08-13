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

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.afft.app.model.OperationResult
import com.afft.app.util.BinaryManager
import com.afft.app.util.ShellExecutor
import com.afft.app.util.SparseImage
import com.afft.app.util.formatFileSize
import com.afft.app.util.parsePayloadProgressLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

class AFFTService(
    private val context: Context,
) {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Pipeline log (buffer, throttle StateFlow, file log) & operasi file
    // didelegasikan ke class pendamping agar service tetap fokus pada alur kerja.
    private val logManager =
        LogManager(
            prefs = context.getSharedPreferences("afft_log_prefs", Context.MODE_PRIVATE),
            tempDir = ::getTempDir,
            exportDir = ::getExportDir,
            onLogsChanged = { _logs.value = it },
        )

    private val storageManager =
        StorageManager(
            workDir = ::getWorkDir,
            exportDir = ::getExportDir,
            onLog = ::addLog,
            onLiveActivity = ::setLiveActivity,
        )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    // Progress percentage untuk UI (0-100)
    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent.asStateFlow()

    // Jumlah total partition yang akan diekstrak
    private val totalPartitions = MutableStateFlow(0)

    // Nama partition saat ini yang sedang diekstrak
    private val _currentPartition = MutableStateFlow("")
    val currentPartition: StateFlow<String> = _currentPartition.asStateFlow()

    fun setLogToFileEnabled(enabled: Boolean) = logManager.setLogToFileEnabled(enabled)

    fun isLogToFileEnabled(): Boolean = logManager.logToFileEnabled

    fun toggleDebug() = logManager.toggleDebug()

    fun isDebugMode(): Boolean = logManager.debugMode

    // Foreground service sudah di-start dari Application.onCreate() dan
    // akan tetap running selama aplikasi aktif. Kita hanya perlu manage
    // wake lock via notifikasi ke service (opsional).
    // Hindari start/stop berulang yang menyebabkan crash Android 14+.
    private fun ensureForegroundRunning() {
        // Start foreground service saat operasi dimulai agar proses tidak dibunuh
        // saat aplikasi di-background. Service di-stop setelah operasi selesai.
        try {
            AFFTExtractService.start(context)
        } catch (e: Exception) {
            android.util.Log.w("AFFTService", "Gagal start foreground service: ${e.message}")
        }
    }

    private fun ensureForegroundStopped() {
        // Stop service setelah operasi selesai (hemat baterai; tidak ada lagi
        // foreground service & wake lock seumur hidup aplikasi).
        try {
            AFFTExtractService.stop(context)
        } catch (e: Exception) {
            android.util.Log.w("AFFTService", "Gagal stop foreground service: ${e.message}")
        }
    }

    val currentLogFile: File? get() = logManager.currentLogFile

    init {
        // Baca preferensi & buka file log bila aktif (lihat LogManager).
        logManager.init()
    }

    // Parse progress dari stdout payload-dumper-go untuk StateFlow
    private fun parsePayloadProgress(raw: String) {
        // Progress bar: "system (821 MB) [========>       ] 45%"
        parsePayloadProgressLine(raw)?.let {
            _currentPartition.value = it.partition
            _progressPercent.value = it.percent
            return
        }

        // Hapus ANSI escape sequences dari line untuk pengecekan non-progress
        val clean = raw.replace(Regex("\u001b\\[[0-9;]*[a-zA-Z]"), "").trim()
        if (clean.isEmpty()) return

        // "Found partitions: system, product, vendor, ..."
        if (clean.contains("Found partitions:", ignoreCase = true)) {
            val parts =
                clean
                    .substringAfter(":")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                totalPartitions.value = parts.size
            }
            return
        }

        // "Extracting partition_name" fallback
        Regex("""[Ee]xtracting\s+([a-zA-Z_0-9.-]+)""").find(clean)?.let {
            _currentPartition.value = it.groupValues[1]
        }
    }

    private fun addLog(text: String) = logManager.addLog(text)

    // Publish snapshot log terbaru secara paksa (dipanggil saat operasi selesai)
    private fun flushLogs() = logManager.flush()

    private fun updateProgress(msg: String) {
        _progressMessage.value = msg
        if (logManager.debugMode) addLog("[INFO] $msg")
        android.util.Log.d("AFFTService", msg)
    }

    /**
     * Mulai operasi berat (serialisasi satu-per-satu) + reset indikator
     * progress agar kartu status tidak menampilkan nilai lama. Menolak (false)
     * bila ada operasi lain yang masih berjalan; pemanggil wajib menolak.
     */
    private fun beginOperation(
        opName: String,
        initialMessage: String,
    ): Boolean {
        if (_isRunning.value) {
            addLog("[WARN] '$opName' dilewati: operasi lain masih berjalan")
            return false
        }
        _isRunning.value = true
        _progressPercent.value = 0
        _currentPartition.value = ""
        updateProgress(initialMessage)
        return true
    }

    /** Terbitkan pesan aktivitas ke kartu Live Status tanpa log tambahan. */
    private fun setLiveActivity(msg: String) {
        _progressMessage.value = msg
    }

    /**
     * Catat kegagalan ke Live Status lalu kembalikan [OperationResult] gagal.
     * Semua jalur gagal operasi memakai ini agar user melihat error langsung
     * di kartu Live Status (bukan hanya log).
     */
    private fun failResult(
        title: String,
        message: String,
        outputPath: String = "",
    ): OperationResult {
        _progressMessage.value = "\u274C $title gagal: $message"
        return OperationResult(false, title, message, outputPath)
    }

    fun clearLogs() {
        _progressMessage.value = ""
        _progressPercent.value = 0
        _currentPartition.value = ""
        logManager.clear()
    }

    suspend fun getLogFiles(): List<File> = logManager.getLogFiles()

    suspend fun getLogContent(logFile: File): String = logManager.getLogContent(logFile)

    fun getLogsDir(): File = logManager.getLogsDir()

    /**
     * Simpan semua log saat ini ke file di Downloads/AFFT/logs/
     */
    suspend fun saveCurrentLogToDownloads(): File? = logManager.saveCurrentLogToDownloads(_logs.value)

    /**
     * Hapus log file lama, sisakan hanya [maxFiles] terbaru
     */
    suspend fun clearOldLogs(maxFiles: Int = 20) = logManager.clearOldLogs(maxFiles)

    fun getFreeSpace(path: String): Long = storageManager.getFreeSpace(path)

    fun checkStorageSpace(
        fileSize: Long,
        destPath: String,
    ): Boolean = storageManager.checkStorageSpace(fileSize, destPath)

    suspend fun deleteFileWithSafety(file: File): Boolean = storageManager.deleteFileWithSafety(file)

    suspend fun createFolder(
        parentDir: File,
        name: String,
    ): Boolean = storageManager.createFolder(parentDir, name)

    suspend fun renameFile(
        file: File,
        newName: String,
    ): Boolean = storageManager.renameFile(file, newName)

    suspend fun copyFileTo(
        src: File,
        destDir: File,
    ): Boolean = storageManager.copyFileTo(src, destDir)

    suspend fun moveFileTo(
        src: File,
        destDir: File,
    ): Boolean = storageManager.moveFileTo(src, destDir)

    suspend fun pickAndCopyToInput(uri: Uri): File? = copyPickedFileToInput(uri)

    fun getWorkDir(): File {
        val baseDir = context.getExternalFilesDir(null)
        if (baseDir == null) {
            // Fallback ke internal jika external storage tidak tersedia
            val dir = File(context.filesDir, "afft_work")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
        val dir = File(baseDir, "afft_work")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(): File {
        val dir = File(getWorkDir(), "temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getInputDir(): File {
        val dir = File(getWorkDir(), "input")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Direktori ekspor hasil kerja: Download/AFFT (portabel, tidak hardcoded). */
    fun getExportDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AFFT")

    private fun ensureDirs() {
        getWorkDir()
        getTempDir()
        getInputDir()
        File(getTempDir(), "img").mkdirs()
        File(getTempDir(), "contents").mkdirs()
        File(getTempDir(), "repacked").mkdirs()
        File(getTempDir(), "Payload").mkdirs()
        File(getTempDir(), "boot").mkdirs()
        File(getTempDir(), "boot_out").mkdirs()
        File(getTempDir(), "img_src").mkdirs()
        File(getTempDir(), "filesystem_work").mkdirs()
        File(getTempDir(), "logs").mkdirs()
        logManager.initLogFile()
    }

    /**
     * Copy a file picked via SAF (content URI) to the input/ directory.
     * This makes it visible in the File Manager.
     */
    suspend fun copyPickedFileToInput(uri: Uri): File? =
        // Jalankan di NonCancellable agar penyalinan TIDAK terputus saat layar
        // berpindah tab / keluar komposisi (bug: pilih file lalu pindah tab
        // menyebabkan copy dibatalkan dan gagal). Salinan butuh berjalan tuntas.
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val inputDir = getInputDir()
                val fileName = resolveFileName(uri) ?: "imported_${System.currentTimeMillis()}"
                // Hindari menimpa file dengan nama sama (payload.bin, dll).
                val destFile = storageManager.resolveDestFile(File(fileName), inputDir)
                setLiveActivity("Menyalin file $fileName...")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        storageManager.copyStreamCancellable(input, output)
                    }
                }
                addLog("[OK] File disalin ke input/: $fileName")
                updateProgress("File disalin: $fileName")
                destFile
            } catch (e: Exception) {
                addLog("[ERROR] Gagal menyalin file ke input/: ${e.message}")
                null
            }
        }

    /**
     * Resolve a display name from a content URI.
     */
    private fun resolveFileName(uri: Uri): String? =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) c.getString(nameIdx) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }

    /** Konversi sparse->raw dengan hormat terhadap pembatalan coroutine. */
    private suspend fun sparseToRawCancellable(
        src: File,
        dst: File,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val ctx = coroutineContext
            SparseImage.sparseToRaw(src, dst) { !ctx.isActive }
        }

    suspend fun copyUriToFile(
        uri: Uri,
        destFile: File,
    ): Boolean =
        try {
            if (logManager.debugMode) addLog("[INFO] Copying $uri -> ${destFile.absolutePath}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    storageManager.copyStreamCancellable(input, output)
                }
            }
            if (logManager.debugMode) addLog("[OK] Copy selesai: ${destFile.name}")
            true
        } catch (e: Exception) {
            addLog("[ERROR] Failed to copy file: ${e.message}")
            false
        }

    suspend fun extractPayload(inputUri: Uri): OperationResult {
        ensureDirs()
        val originalName = resolveFileName(inputUri) ?: "payload_src.bin"
        val payloadFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, payloadFile)) {
            return failResult("Extract Payload", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${payloadFile.length()} bytes)")
        return extractPayload(payloadFile)
    }

    suspend fun extractPayload(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Extract Payload", "Extracting payload, mohon tunggu...")) {
            return failResult("Extract Payload", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Extract payload.bin ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        // Scope monitoring progress — dideklarasikan di luar try agar bisa
        // dibatalkan dari `finally` pada semua jalur (termasuk cancellation).
        var monitorJob: Job? = null
        var monitorScope: CoroutineScope? = null
        return try {
            val payloadDumper =
                BinaryManager.getBinaryPath(context, "payload-dumper-go")
                    ?: return failResult("Extract Payload", "payload-dumper-go not found")

            updateProgress("Extracting payload, mohon tunggu...")
            addLog("Running payload-dumper-go...")

            // Deteksi mode eksekusi: dynamic (bisa via linker64) atau static
            val isStaticBinary = !ShellExecutor.isDynamicElf(File(payloadDumper))
            addLog("[INFO] Mode: ${if (isStaticBinary) "static binary" else "dynamic binary (linker64)"}")

            // Set LD_LIBRARY_PATH untuk memastikan liblzma.so.5 ditemukan
            // (dibutuhkan oleh payload-dumper-go yang dynamic link via CGO)
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val binDir = BinaryManager.getBinDirectory(context).absolutePath
            val ldLibraryPath = "$nativeLibDir:$binDir"
            addLog("[INFO] LD_LIBRARY_PATH=$ldLibraryPath")

            val cores = Runtime.getRuntime().availableProcessors()
            val concurrency = 1 // sequential (I/O contention fix)
            addLog("[INFO] CPU cores: $cores, concurrency: -c $concurrency")
            // Gunakan nice untuk prioritas CPU lebih tinggi (fallback jika gagal)
            val extractCmd =
                try {
                    val testProcess = Runtime.getRuntime().exec(arrayOf("nice", "-n", "-5", "echo", "test"))
                    testProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (!testProcess.isAlive && testProcess.exitValue() == 0) {
                        listOf(
                            "nice",
                            "-n",
                            "-5",
                            payloadDumper,
                            inputFile.absolutePath,
                            "-o",
                            File(getTempDir(), "Payload").absolutePath,
                            "-c",
                            concurrency.toString(),
                        )
                    } else {
                        listOf(
                            payloadDumper,
                            inputFile.absolutePath,
                            "-o",
                            File(getTempDir(), "Payload").absolutePath,
                            "-c",
                            concurrency.toString(),
                        )
                    }
                } catch (e: Exception) {
                    listOf(
                        payloadDumper,
                        inputFile.absolutePath,
                        "-o",
                        File(getTempDir(), "Payload").absolutePath,
                        "-c",
                        concurrency.toString(),
                    )
                }
            // Set initial progress state
            _progressPercent.value = 0
            _currentPartition.value = "Initializing..."
            addLog("[INFO] Command: ${extractCmd.joinToString(" ")}")

            // Start directory monitoring for progress tracking.
            // Scope dibatalkan di `finally` agar tidak bocor saat coroutine
            // di-cancel (mis. user meninggalkan screen di tengah ekstraksi).
            val payloadOutputDir = File(getTempDir(), "Payload")
            monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            monitorJob =
                monitorScope.launch {
                    var lastCount = 0
                    while (isActive) {
                        delay(2000)
                        val imgFiles =
                            payloadOutputDir
                                .listFiles()
                                ?.filter { it.extension == "img" || it.extension == "new" }
                                ?: emptyList()
                        if (imgFiles.isNotEmpty()) {
                            if (imgFiles.size > lastCount) {
                                lastCount = imgFiles.size
                                _currentPartition.value = imgFiles.last().nameWithoutExtension
                            }
                            if (totalPartitions.value > 0) {
                                _progressPercent.value = ((lastCount * 100) / totalPartitions.value).coerceIn(0, 100)
                            } else {
                                _progressPercent.value = lastCount.coerceAtMost(100)
                            }
                        }
                    }
                }

            val result =
                ShellExecutor.executeWithProgress(
                    command = extractCmd,
                    workingDir = getTempDir(),
                    envVars = mapOf("LD_LIBRARY_PATH" to ldLibraryPath),
                    onProgress = { line ->
                        parsePayloadProgress(line)
                        // Don't add payload-dumper-go output to logs (progress bar only)
                    },
                    timeoutMillis = 1800000L,
                )

            if (result.isTimeout) {
                addLog("[TIMEOUT] Extract Payload: proses tidak selesai dalam 30 menit, dibatalkan")
                addLog("[TIMEOUT] Perangkat mungkin kekurangan RAM atau sistem membekukan proses")
                updateProgress("Ekstrak payload gagal: timeout")
                return failResult("Extract Payload", "Timeout: proses tidak selesai dalam 30 menit")
            } else if (result.exitCode == 0) {
                addLog("[OK] Payload extracted successfully")
                // Set progress to 100% after success
                _progressPercent.value = 100
                val outputDir = File(getTempDir(), "Payload")
                val imgCount = outputDir.listFiles()?.filter { it.extension == "img" }?.size ?: 0
                _currentPartition.value = "Done ($imgCount img files)"
                updateProgress("Ekstrak payload selesai! ($imgCount img)")

                OperationResult(
                    true,
                    "Extract Payload",
                    "Payload extracted successfully",
                    File(getTempDir(), "Payload").absolutePath,
                )
            } else {
                addLog("[FAIL] payload-dumper-go exited with code ${result.exitCode}")
                result.errorOutput.forEach { addLog("[ERROR] $it") }

                // Cek apakah error karena missing library
                val linkError =
                    result.errorOutput.any {
                        it.contains("CANNOT LINK", ignoreCase = true) ||
                            it.contains("library.*not found".toRegex())
                    }
                if (linkError) {
                    addLog("[INFO] Mencoba fallback: deploy binary dari assets + LD_LIBRARY_PATH...")
                    addLog("[INFO] Pastikan payload-dumper-go adalah static binary (CGO_ENABLED=0)")
                    addLog("[INFO] atau bundle liblzma.so.5 di nativeLibraryDir")

                    // Coba deploy dan jalankan dari filesDir dengan LD_LIBRARY_PATH
                    val fallbackResult = runPayloadDumperFallback(inputFile)
                    if (fallbackResult != null) return fallbackResult
                }

                OperationResult(
                    false,
                    "Extract Payload",
                    "payload-dumper-go failed (exit ${result.exitCode})",
                )
            }
        } catch (e: CancellationException) {
            // Coroutine cancelled (user left screen), ignore
            addLog("[INFO] Extraction cancelled")
            failResult("Extract Payload", "Cancelled")
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Extract Payload", e.message ?: "Unknown error")
        } finally {
            // Batalkan monitoring progress apa pun jalurnya (sukses, gagal,
            // timeout, ataupun coroutine di-cancel).
            monitorJob?.cancel()
            monitorScope?.cancel()
            // Flush remaining logs
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    /**
     * Fallback: deploy payload-dumper-go dari assets ke filesDir dan jalankan
     * dengan LD_LIBRARY_PATH yang sesuai.
     */
    private suspend fun runPayloadDumperFallback(inputFile: File): OperationResult? {
        return try {
            val binDir = BinaryManager.getBinDirectory(context)
            val localBinary = File(binDir, "payload-dumper-go")

            // Deploy payload-dumper-go dari assets jika perlu
            if (!localBinary.exists() || !localBinary.canExecute()) {
                try {
                    context.assets.open("bin/payload-dumper-go").use { input ->
                        localBinary.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    localBinary.setExecutable(true, false)
                    localBinary.setReadable(true, false)
                    addLog("[INFO] Deployed payload-dumper-go dari assets ke ${localBinary.absolutePath}")
                } catch (e: Exception) {
                    addLog("[WARN] Gagal deploy dari assets: ${e.message}")
                    return null
                }
            }

            // Juga deploy liblzma.so.5 jika ada di assets/bin/
            val libLzmaFile = File(binDir, "liblzma.so.5")
            if (!libLzmaFile.exists()) {
                try {
                    context.assets.open("bin/liblzma.so.5").use { input ->
                        libLzmaFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    libLzmaFile.setReadable(true, false)
                    addLog("[INFO] Deployed liblzma.so.5 dari assets ke ${libLzmaFile.absolutePath}")
                } catch (e: Exception) {
                    addLog("[WARN] liblzma.so.5 tidak tersedia di assets: ${e.message}")
                }
            }

            if (!localBinary.exists()) {
                addLog("[WARN] Binary tidak tersedia di assets maupun filesDir")
                return null
            }

            // Jalankan dengan LD_LIBRARY_PATH yang mencakup binDir (tempat liblzma.so.5)
            val ldPath = "${context.applicationInfo.nativeLibraryDir}:${binDir.absolutePath}"
            addLog("[INFO] Fallback: menjalankan dari ${localBinary.absolutePath}")
            addLog("[INFO] LD_LIBRARY_PATH=$ldPath")

            val envVars = mapOf("LD_LIBRARY_PATH" to ldPath)
            // Gunakan ShellExecutor.executeBinary() yang memiliki fallback:
            // direct -> linker64 -> sh -c (untuk mengatasi SELinux noexec)
            val cores = Runtime.getRuntime().availableProcessors()
            val concurrency = 1 // sequential (I/O contention fix)
            addLog("[INFO] Fallback concurrency: -c $concurrency")
            val fallbackResult =
                ShellExecutor.executeBinary(
                    binaryPath = localBinary.absolutePath,
                    args =
                        listOf(
                            inputFile.absolutePath,
                            "-o",
                            File(getTempDir(), "Payload").absolutePath,
                            "-c",
                            concurrency.toString(),
                        ),
                    workingDir = getTempDir(),
                    envVars = envVars,
                    onOutput = { parsePayloadProgress(it) },
                    timeoutMillis = 1800000L,
                )

            if (fallbackResult.isTimeout) {
                addLog("[TIMEOUT] Fallback: proses tidak selesai dalam 30 menit, dibatalkan")
                return failResult("Extract Payload", "Timeout: fallback juga tidak selesai")
            } else if (fallbackResult.exitCode == 0) {
                addLog("[OK] Payload extracted (fallback)")
                updateProgress("Ekstrak payload selesai! (fallback)")
                return OperationResult(
                    true,
                    "Extract Payload",
                    "Payload extracted (fallback)",
                    File(getTempDir(), "Payload").absolutePath,
                )
            }

            addLog("[FAIL] Fallback juga gagal (exit ${fallbackResult.exitCode})")
            fallbackResult.errorOutput.forEach { addLog("[ERROR] $it") }
            null
        } catch (e: Exception) {
            addLog("[WARN] Fallback error: ${e.message}")
            null
        }
    }

    suspend fun unpackSuper(inputUri: Uri): OperationResult {
        ensureDirs()
        val originalName = resolveFileName(inputUri) ?: "super_src.img"
        val superFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, superFile)) {
            return failResult("Unpack Super", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${superFile.length()} bytes)")
        return unpackSuper(superFile)
    }

    suspend fun unpackSuper(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Unpack Super", "Unpacking super.img...")) {
            return failResult("Unpack Super", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Unpack super.img ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val lpunpack =
                BinaryManager.getBinaryPath(context, "lpunpack")
                    ?: return failResult("Unpack Super", "lpunpack not found")

            // Konversi super.img ke raw jika sparse (lpunpack butuh raw, simg2img binary gak bisa)
            val actualInput: File
            if (isSparseImage(inputFile)) {
                addLog("[INFO] super.img terdeteksi sparse, konversi ke raw (pure Kotlin)...")
                val rawSuperFile = File(getTempDir(), "super_raw.img")
                updateProgress("Mengkonversi sparse ke raw...")
                val convertOk = sparseToRawCancellable(inputFile, rawSuperFile)
                if (!convertOk || !rawSuperFile.exists() || rawSuperFile.length() == 0L) {
                    rawSuperFile.delete()
                    return failResult("Unpack Super", "Konversi sparse ke raw gagal")
                }
                addLog("[OK] Konversi sparse -> raw selesai: ${rawSuperFile.length()} bytes")
                updateProgress("Konversi sparse ke raw selesai!")
                actualInput = rawSuperFile
            } else {
                actualInput = inputFile
            }
            // Simpan device-size untuk referensi repack nanti
            saveSuperDeviceSize(actualInput.length())
            addLog("[INFO] Menggunakan file: ${actualInput.absolutePath}")

            updateProgress("Unpacking super.img...")
            val imgDir = File(getTempDir(), "img")
            imgDir.mkdirs()
            addLog("Running lpunpack...")
            val result =
                ShellExecutor.executeBinary(
                    binaryPath = lpunpack,
                    args = listOf(actualInput.absolutePath, imgDir.absolutePath),
                    workingDir = getTempDir(),
                    onOutput = { line -> addLog(line) },
                )

            // Hapus file raw sementara jika ada
            if (actualInput != inputFile) {
                actualInput.delete()
                addLog("[INFO] Temporary raw super.img cleaned up")
            }

            if (result.exitCode == 0) {
                addLog("[OK] Super unpacked successfully")
                updateProgress("Unpack super selesai!")
                OperationResult(true, "Unpack Super", "Super unpacked to temp/img/", imgDir.absolutePath)
            } else {
                addLog("[FAIL] lpunpack failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
                failResult("Unpack Super", "lpunpack failed (exit ${result.exitCode})")
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Unpack Super", e.message ?: "Unknown error")
        } finally {
            // Flush remaining logs
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun repackSuper(
        selectedPartitions: List<String>? = null,
        customSourceDir: String? = null,
    ): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Repack Super", "Repacking super.img...")) {
            return failResult("Repack Super", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Repack super.img ===")

        return try {
            val lpmake =
                BinaryManager.getBinaryPath(context, "lpmake")
                    ?: return failResult("Repack Super", "lpmake not found")

            val imgDir =
                if (customSourceDir != null) {
                    File(customSourceDir).also {
                        addLog("[INFO] Source folder: ${it.absolutePath}")
                    }
                } else {
                    File(getTempDir(), "img")
                }
            if (!imgDir.exists() || imgDir.listFiles().isNullOrEmpty()) {
                return failResult("Repack Super", "No images in ${imgDir.path}/")
            }
            addLog(
                "[INFO] Files in ${imgDir.path}: ${imgDir.listFiles()?.map { it.name }?.joinToString(
                    ", ",
                ) ?: "(empty)"}",
            )

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()

            addLog("Finding partition images...")
            val allImages =
                imgDir
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.filter { it.name !in setOf("super.img", "super_raw.img") }
                    ?.sortedBy { it.name } ?: emptyList()

            // Filter by selected partitions if provided
            val images =
                if (!selectedPartitions.isNullOrEmpty()) {
                    allImages.filter { img -> selectedPartitions.contains(img.name) }
                } else {
                    allImages
                }

            if (images.isEmpty()) {
                addLog("[FAIL] Tidak ada partition image yang dipilih")
                return failResult("Repack Super", "No partition images selected")
            }

            addLog("Partitions to repack:")
            images.forEach { addLog("  - ${it.name} (${formatFileSize(it.length())})") }

            // Convert sparse images to raw (lpmake butuh raw, lpunpack output mungkin sparse)
            val rawImages = mutableListOf<Pair<String, File>>()
            val tempRawFiles = mutableListOf<File>()
            // Cari converter binary: simg2img static (prioritas), fallback sparseToRaw
            val simg2img = BinaryManager.getBinaryPath(context, "simg2img")
            for (img in images) {
                val partName = img.nameWithoutExtension
                addLog("  [CHECK] ${img.name}: ${formatFileSize(img.length())}")

                // Handle 0-byte files: buat dummy raw image 4KB
                if (img.length() == 0L) {
                    addLog("  [INFO] File 0B, buat dummy 4KB: ${img.name}")
                    val dummyFile = File(repackedDir, "raw_${img.name}")
                    try {
                        dummyFile.outputStream().use { out ->
                            out.write(ByteArray(4096))
                        }
                        tempRawFiles.add(dummyFile)
                        rawImages.add(partName to dummyFile)
                        addLog("  [OK] Dummy 4KB dibuat untuk ${img.name}")
                    } catch (e: Exception) {
                        addLog("  [ERROR] Gagal buat dummy ${img.name}: ${e.message}")
                        for (f in tempRawFiles) {
                            if (f.exists()) f.delete()
                        }
                        return OperationResult(
                            false,
                            "Repack Super",
                            "Gagal buat dummy file: ${img.name}",
                        )
                    }
                    continue
                }

                // Cek apakah file sudah RAW — skip konversi
                if (!SparseImage.isSparseImage(img)) {
                    addLog("  [INFO] File sudah RAW, skip konversi: ${img.name}")
                    rawImages.add(partName to img)
                    continue
                }

                // STEP 1: Coba simg2img binary (static, reliable) — hanya untuk sparse
                val rawFile = File(repackedDir, "raw_${img.name}")
                var convOk = false
                if (simg2img != null) {
                    addLog("  [INFO] Converting via simg2img: ${img.name}")
                    try {
                        val result =
                            ShellExecutor.executeBinary(
                                binaryPath = simg2img,
                                args = listOf(img.absolutePath, rawFile.absolutePath),
                                workingDir = getTempDir(),
                                onOutput = { line -> if (logManager.debugMode) addLog(line) },
                            )
                        convOk = result.exitCode == 0 && rawFile.exists() && rawFile.length() > 0
                        if (convOk) {
                            addLog(
                                "  [OK] simg2img converted: ${img.name} (${formatFileSize(
                                    img.length(),
                                )} -> ${formatFileSize(rawFile.length())})",
                            )
                        } else {
                            addLog("  [WARN] simg2img exit ${result.exitCode}, fallback sparseToRaw")
                        }
                    } catch (e: Exception) {
                        addLog("  [WARN] simg2img error: ${e.message}, fallback sparseToRaw")
                    }
                }

                // STEP 2: Fallback ke sparseToRaw Kotlin
                if (!convOk) {
                    addLog("  [INFO] Converting via sparseToRaw: ${img.name}")
                    try {
                        val converted = sparseToRawCancellable(img, rawFile)
                        convOk = converted && rawFile.exists() && rawFile.length() > 0
                        if (convOk && rawFile.length() != img.length()) {
                            addLog(
                                "  [OK] sparseToRaw converted: ${img.name} (${formatFileSize(
                                    img.length(),
                                )} -> ${formatFileSize(rawFile.length())})",
                            )
                        }
                    } catch (e: Exception) {
                        addLog("  [WARN] sparseToRaw error: ${e.message}")
                    }
                }

                // STEP 3: Final — raw berhasil atau fallback ke original
                if (convOk) {
                    tempRawFiles.add(rawFile)
                    rawImages.add(partName to rawFile)
                } else {
                    addLog("  [WARN] Semua konversi gagal, pakai original: ${img.name}")
                    rawImages.add(partName to img)
                }
            }
            if (rawImages.isEmpty()) {
                return failResult("Repack Super", "No valid images after sparse conversion")
            }

            // Build lpmake command — deteksi device-size otomatis dari super_img_info.txt
            val deviceSize =
                readSavedSuperDeviceSize()
                    ?: calculateSuperDeviceSize(rawImages)
            // Pastikan device-size alignment 4096
            val alignedDeviceSize = (deviceSize + 4095) / 4096 * 4096
            val deviceSizeHex = "0x%x".format(alignedDeviceSize)
            addLog(
                "  [INFO] Total partitions: ${formatFileSize(
                    rawImages.sumOf { (_, f) ->
                        f.length()
                    },
                )}, device-size: $deviceSizeHex ($alignedDeviceSize bytes)",
            )
            val cmd =
                mutableListOf(
                    lpmake,
                    "--device-size=$deviceSizeHex",
                    "--metadata-size=65536",
                    "--super-name=super",
                    "--metadata-slots=2",
                    "--alignment=4096",
                )

            for ((partitionName, rawFile) in rawImages) {
                val alignedSize = (rawFile.length() + 4095) / 4096 * 4096
                cmd.add("--partition=$partitionName:readonly:$alignedSize")
            }

            for ((partitionName, rawFile) in rawImages) {
                cmd.add("--image=$partitionName=${rawFile.absolutePath}")
            }

            val outputFile = File(repackedDir, "super_repack.img")
            cmd.add("--output=${outputFile.absolutePath}")

            updateProgress("Menjalankan lpmake...")
            addLog("Running: lpmake ...")
            if (logManager.debugMode) addLog("[DEBUG] ${cmd.joinToString(" ")}")

            val result =
                ShellExecutor.executeBinary(
                    binaryPath = lpmake,
                    args = cmd.drop(1),
                    workingDir = getTempDir(),
                    onOutput = { line -> addLog(line) },
                )

            // Clean up temp raw files
            for (f in tempRawFiles) {
                if (f.exists()) f.delete()
            }

            if (result.exitCode == 0 && outputFile.exists()) {
                addLog("[OK] super_repack.img created: ${outputFile.length()} bytes")
                updateProgress("Repack super selesai! (${outputFile.length()} bytes)")
                copyResultToDownload(outputFile.absolutePath, "super_repack.img")
                OperationResult(
                    true,
                    "Repack Super",
                    "Repack selesai",
                    outputFile.absolutePath,
                )
            } else {
                addLog("[FAIL] lpmake failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
                OperationResult(
                    false,
                    "Repack Super",
                    "lpmake failed (exit ${result.exitCode})",
                )
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Repack Super", e.message ?: "Unknown error")
        } finally {
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun extractFilesystem(inputUri: Uri): OperationResult {
        ensureDirs()
        // Get original filename from URI
        val originalName = resolveFileName(inputUri) ?: "filesystem_src.img"
        val fsFile = File(getTempDir(), originalName)
        if (!copyUriToFile(inputUri, fsFile)) {
            return failResult("Extract Filesystem", "Failed to copy input file")
        }
        addLog("[INFO] File sumber: $originalName (${fsFile.length()} bytes)")
        return extractFilesystem(fsFile)
    }

    suspend fun extractFilesystem(inputFile: File): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        _isRunning.value = true
        _progressMessage.value = "Extracting filesystem..."
        clearLogs()
        addLog("=== Extract Filesystem ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")
        addLog("[INFO] File size: ${inputFile.length()} bytes")

        val contentsDir = File(getTempDir(), "contents")
        val name = inputFile.nameWithoutExtension
        val outDir = File(contentsDir, name)
        outDir.mkdirs()

        return try {
            // Check if image is sparse Android format, convert to raw if needed
            updateProgress("Memeriksa format gambar...")
            var workingFile = inputFile

            if (isSparseImage(inputFile)) {
                updateProgress("Mengkonversi sparse ke raw image (pure Kotlin)...")
                addLog("[INFO] Deteksi gambar sparse Android, mengkonversi ke raw...")
                val rawFile = File(getTempDir(), "${name}_raw.img")
                val convertOk = sparseToRawCancellable(inputFile, rawFile)
                if (convertOk && rawFile.exists() && rawFile.length() > 0) {
                    addLog("[OK] Konversi sparse->raw berhasil: ${rawFile.length()} bytes")
                    workingFile = rawFile
                } else {
                    addLog("[WARN] Konversi sparse->raw gagal, menggunakan file asli")
                    addLog("[WARN] File mungkin bukan sparse image yang valid, lanjut dengan file asli")
                }
            }

            // Detect filesystem type on the (possibly converted) raw file
            updateProgress("Menganalisis filesystem...")
            addLog("Mengidentifikasi tipe filesystem...")

            // Debug: check first bytes and EROFS magic
            if (logManager.debugMode) {
                try {
                    val debugBytes = ByteArray(16)
                    java.io.RandomAccessFile(workingFile, "r").use { it.readFully(debugBytes) }
                    val hexStr = debugBytes.joinToString(" ") { String.format("%02x", it.toInt() and 0xFF) }
                    addLog("[DEBUG] 16 bytes pertama: $hexStr")

                    val erofsBytes = ByteArray(4)
                    java.io.RandomAccessFile(workingFile, "r").use {
                        it.seek(0x400)
                        it.readFully(erofsBytes)
                    }
                    val erofsHex = erofsBytes.joinToString(" ") { String.format("%02x", it.toInt() and 0xFF) }
                    addLog("[DEBUG] EROFS magic di 0x400: $erofsHex")
                } catch (e: Exception) {
                    addLog("[DEBUG] Gagal baca header: ${e.message}")
                }
            }

            val fsType = detectFilesystemType(workingFile)
            addLog("[INFO] Terdeteksi filesystem: $fsType")

            // === EROFS: fast path ===
            if (fsType == "erofs") {
                val extractTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                if (extractTool != null) {
                    updateProgress("Mengekstrak EROFS filesystem...")
                    addLog("Menjalankan: extract.erofs ${workingFile.name} -> $name/")
                    val result =
                        ShellExecutor.executeWithProgress(
                            command =
                                listOf(
                                    extractTool,
                                    "-i",
                                    workingFile.absolutePath,
                                    "-x",
                                    "-o",
                                    outDir.absolutePath,
                                    "-f",
                                ),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) },
                        )
                    val fileCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                    if (result.exitCode == 0 && fileCount > 0) {
                        addLog("[OK] EROFS filesystem terekstrak ke $name/ ($fileCount item)")
                        updateProgress("Ekstrak EROFS selesai! $fileCount item")
                        return OperationResult(
                            true,
                            "Extract Filesystem",
                            "EROFS extracted ($fileCount items)",
                            outDir.absolutePath,
                        )
                    }
                    addLog("[FAIL] extract.erofs gagal (exit ${result.exitCode}, $fileCount item diekstrak)")
                    result.errorOutput.forEach { addLog("[ERROR] $it") }
                    val reason =
                        result.errorOutput.lastOrNull()?.trim()
                            ?: "extract.erofs gagal (exit ${result.exitCode})"
                    return failResult("Extract Filesystem", reason)
                }
                addLog("[INFO] extract.erofs binary tidak tersedia")
                return failResult("Extract Filesystem", "extract.erofs binary tidak tersedia")
            }

            // === GZIP: decompress and retry ===
            if (fsType == "gzip") {
                addLog("[INFO] File terkompresi gzip, mencoba dekompresi...")
                val decompressed = File(getTempDir(), "${name}_decompressed.img")
                val gunzipRes =
                    ShellExecutor.executeWithProgress(
                        command =
                            listOf(
                                "sh",
                                "-c",
                                "gzip -d -k -c '${workingFile.absolutePath}' > '${decompressed.absolutePath}'",
                            ),
                        workingDir = getTempDir(),
                        onProgress = { addLog(it) },
                    )
                if (gunzipRes.exitCode == 0 && decompressed.exists()) {
                    addLog("[OK] Dekompresi berhasil, mendeteksi ulang...")
                    return extractFilesystem(decompressed)
                }
                addLog("[FAIL] Gagal dekompresi gzip, lanjut ke debugfs...")
            }

            // === UNKNOWN: try extract.erofs as fallback ===
            if (fsType == "unknown") {
                addLog("[INFO] Tipe filesystem tidak terdeteksi, mencoba EROFS...")
                val fallbackTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                if (fallbackTool != null) {
                    val erofsResult =
                        ShellExecutor.executeWithProgress(
                            command =
                                listOf(
                                    fallbackTool,
                                    "-i",
                                    workingFile.absolutePath,
                                    "-x",
                                    "-o",
                                    outDir.absolutePath,
                                    "-f",
                                ),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) },
                        )
                    val fallbackCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                    if (erofsResult.exitCode == 0 && fallbackCount > 0) {
                        addLog("[OK] EROFS filesystem terekstrak ke $name/ ($fallbackCount item)")
                        updateProgress("Ekstrak EROFS selesai! $fallbackCount item")
                        return OperationResult(
                            true,
                            "Extract Filesystem",
                            "EROFS extracted ($fallbackCount items)",
                            outDir.absolutePath,
                        )
                    }
                    addLog("[INFO] extract.erofs gagal, mencoba debugfs...")
                } else {
                    addLog("[INFO] extract.erofs tidak tersedia, langsung ke debugfs...")
                }
            }

            // === DEBUGFS: universal fallback (ext4/unknown/erofs-fallback) ===
            val debugfsBin =
                BinaryManager.getBinaryPath(context, "debugfs")
                    ?: return failResult("Extract Filesystem", "debugfs not found (binary tidak ada)")

            updateProgress("Mengekstrak filesystem dengan debugfs...")
            addLog("Menjalankan: debugfs -R 'rdump /' ${workingFile.name} -> $name/")

            val debugfsResult =
                ShellExecutor.executeWithProgress(
                    command = listOf(debugfsBin, "-R", "rdump / ${outDir.absolutePath}", workingFile.absolutePath),
                    workingDir = getTempDir(),
                    onProgress = { addLog(it) },
                )

            val debugfsFileCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
            if (debugfsResult.exitCode == 0 && debugfsFileCount > 0) {
                addLog("[OK] Filesystem terekstrak ke $name/ ($debugfsFileCount item)")
                updateProgress("Ekstrak selesai! $debugfsFileCount item")
                OperationResult(
                    true,
                    "Extract Filesystem",
                    "Filesystem extracted ($debugfsFileCount items)",
                    outDir.absolutePath,
                )
            } else {
                addLog("[FAIL] debugfs rdump gagal (exit ${debugfsResult.exitCode})")
                debugfsResult.errorOutput.forEach { addLog("[ERROR] $it") }

                // Check if error is "Bad magic" - try EROFS as fallback
                val badMagicError =
                    debugfsResult.errorOutput.any {
                        it.contains("Bad magic number") || it.contains("Filesystem not open")
                    }
                if (badMagicError) {
                    addLog("[INFO] Bad magic number! File mungkin EROFS, coba extract.erofs...")
                    val erofsTool = BinaryManager.getBinaryPath(context, "extract.erofs")
                    if (erofsTool != null) {
                        val erofsResult =
                            ShellExecutor.executeWithProgress(
                                command =
                                    listOf(
                                        erofsTool,
                                        "-i",
                                        workingFile.absolutePath,
                                        "-x",
                                        "-o",
                                        outDir.absolutePath,
                                        "-f",
                                    ),
                                workingDir = getTempDir(),
                                onProgress = { addLog(it) },
                            )
                        val badMagicCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                        if (erofsResult.exitCode == 0 && badMagicCount > 0) {
                            addLog("[OK] EROFS filesystem terekstrak! ($badMagicCount item)")
                            updateProgress("Ekstrak EROFS selesai! $badMagicCount item")
                            return OperationResult(
                                true,
                                "Extract Filesystem",
                                "EROFS extracted ($badMagicCount items)",
                                outDir.absolutePath,
                            )
                        }
                        addLog("[INFO] extract.erofs juga gagal")
                    }
                }

                if (workingFile != inputFile) {
                    addLog("[INFO] Mencoba debugfs dengan file raw...")
                    val rawResult =
                        ShellExecutor.executeWithProgress(
                            command =
                                listOf(
                                    debugfsBin,
                                    "-R",
                                    "rdump / ${outDir.absolutePath}",
                                    workingFile.absolutePath,
                                ),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) },
                        )
                    if (rawResult.exitCode == 0) {
                        val fileCount = if (outDir.exists()) outDir.walkTopDown().count() - 1 else 0
                        addLog("[OK] Filesystem terekstrak dari file raw! ($fileCount item)")
                        updateProgress("Ekstrak selesai! $fileCount item")
                        OperationResult(
                            true,
                            "Extract Filesystem",
                            "Filesystem extracted via raw ($fileCount items)",
                            outDir.absolutePath,
                        )
                    } else {
                        addLog("[FAIL] debugfs pada raw juga gagal")
                        rawResult.errorOutput.forEach { addLog("[ERROR] $it") }
                        OperationResult(
                            false,
                            "Extract Filesystem",
                            "debugfs failed (exit ${debugfsResult.exitCode})",
                        )
                    }
                } else {
                    addLog("[INFO] debugfs rdump gagal. Mencoba ls untuk verifikasi...")
                    val lsResult =
                        ShellExecutor.executeWithProgress(
                            command = listOf(debugfsBin, "-R", "ls -l /", workingFile.absolutePath),
                            workingDir = getTempDir(),
                            onProgress = { addLog(it) },
                        )
                    if (lsResult.exitCode == 0) {
                        addLog("[INFO] debugfs ls berhasil! Mencoba rdump dengan path absolut...")
                        val rdumpResult =
                            ShellExecutor.executeWithProgress(
                                command =
                                    listOf(
                                        debugfsBin,
                                        "-R",
                                        "rdump / ${outDir.absolutePath}",
                                        workingFile.absolutePath,
                                    ),
                                workingDir = getTempDir(),
                                onProgress = { addLog(it) },
                            )
                        if (rdumpResult.exitCode == 0) {
                            addLog("[OK] Ekstrak berhasil dengan rdump!")
                            OperationResult(
                                true,
                                "Extract Filesystem",
                                "Filesystem extracted",
                                outDir.absolutePath,
                            )
                        } else {
                            addLog("[FAIL] Semua metode debugfs gagal")
                            OperationResult(
                                false,
                                "Extract Filesystem",
                                "debugfs failed after all attempts",
                            )
                        }
                    } else {
                        addLog("[FAIL] debugfs tidak dapat membaca image")
                        OperationResult(
                            false,
                            "Extract Filesystem",
                            "debugfs cannot read this image",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            e.printStackTrace()
            failResult("Extract Filesystem", e.message ?: "Unknown error")
        } finally {
            // Flush remaining logs
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    /**
     * Read saved super device-size from unpack phase.
     * File dibuat saat unpackSuper() untuk referensi repack.
     */
    private fun readSavedSuperDeviceSize(): Long? {
        val infoFile = File(getWorkDir(), "super_device_size.txt")
        if (!infoFile.exists()) return null
        return try {
            infoFile.readText().trim().toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate super device-size from partition sizes + margin.
     * Fallback jika data dari unpack tidak tersedia.
     */
    private fun calculateSuperDeviceSize(partitions: List<Pair<String, File>>): Long {
        val total = partitions.sumOf { (_, f) -> f.length() }
        // total + 20% + 32MB metadata, minimal 11 GB
        return maxOf(total * 12 / 10 + 0x2000000, 0x2C0000000L)
    }

    private fun saveSuperDeviceSize(size: Long) {
        try {
            val infoFile = File(getWorkDir(), "super_device_size.txt")
            infoFile.writeText(size.toString())
        } catch (e: Exception) {
            // Silent fail — tidak kritis
        }
    }

    /**
     * Check if file is an Android sparse image (magic: 0xED26FF3A).
     */
    private fun isSparseImage(file: File): Boolean =
        try {
            val magic = ByteArray(4)
            RandomAccessFile(file, "r").use { it.readFully(magic) }
            magic[0] == 0x3A.toByte() &&
                magic[1] == 0xFF.toByte() &&
                magic[2] == 0x26.toByte() &&
                magic[3] == 0xED.toByte()
        } catch (e: Exception) {
            false
        }

    private fun detectFilesystemType(file: File): String {
        // Delegate to SparseImage for core erofs/ext4/f2fs detection
        val detected = SparseImage.detectFilesystemType(file)
        return when (detected) {
            "unknown" -> {
                if (isGzipFile(file)) "gzip" else detected
            }
            else -> detected
        }
    }

    private fun isGzipFile(file: File): Boolean =
        try {
            val magic = ByteArray(2)
            RandomAccessFile(file, "r").use { it.readFully(magic) }
            magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()
        } catch (e: Exception) {
            false
        }

    suspend fun repackFilesystem(
        dirName: String,
        customSourceDir: String? = null,
    ): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Repack Filesystem", "Repacking filesystem...")) {
            return failResult("Repack Filesystem", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Repack Filesystem ===")

        return try {
            val srcDir =
                if (customSourceDir != null) {
                    File(customSourceDir).also {
                        addLog("[INFO] Source folder: ${it.absolutePath}")
                    }
                } else {
                    File(File(getTempDir(), "contents"), dirName)
                }
            if (!srcDir.exists()) {
                return OperationResult(
                    false,
                    "Repack Filesystem",
                    "Directory not found: ${srcDir.path}",
                )
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()
            val outputFile = File(repackedDir, "${dirName}_repack.img")
            if (outputFile.exists()) outputFile.delete()

            // Cari binary dan file_contexts
            val makeExt4 = BinaryManager.getBinaryPath(context, "make_ext4fs")
            val mkfsErofs = BinaryManager.getBinaryPath(context, "mkfs.erofs")
            val fileContextsPath = findFileContexts(File(getTempDir(), "contents"), dirName)

            updateProgress("Repacking filesystem...")

            // Prioritaskan mkfs.erofs untuk Android modern (EROFS)
            // Fallback ke make_ext4fs untuk Android lama (ext4)
            if (mkfsErofs != null) {
                addLog("[INFO] Repack dengan mkfs.erofs -z lz4hc,9 -C 4096 ${dirName}_repack.img $dirName/")
                var erofsOk =
                    repackErofs(
                        mkfsErofs,
                        outputFile,
                        srcDir,
                        fileContextsPath,
                        "lz4hc,9",
                    )
                if (erofsOk) {
                    addLog("[OK] Repacked EROFS: ${outputFile.name} (${outputFile.length()} bytes)")
                    updateProgress("Repack EROFS selesai!")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(
                        true,
                        "Repack Filesystem",
                        "Repack EROFS selesai",
                        outputFile.absolutePath,
                    )
                } else {
                    addLog("[INFO] lz4hc gagal, mencoba tanpa kompresi...")
                    if (outputFile.exists()) outputFile.delete()
                    erofsOk =
                        repackErofs(
                            mkfsErofs,
                            outputFile,
                            srcDir,
                            fileContextsPath,
                            "none",
                        )
                    if (erofsOk) {
                        addLog("[OK] Repacked EROFS (uncompressed): ${outputFile.name}")
                        updateProgress("Repack EROFS selesai!")
                        copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                        OperationResult(
                            true,
                            "Repack Filesystem",
                            "Repack EROFS (uncompressed) selesai",
                            outputFile.absolutePath,
                        )
                    } else {
                        addLog("[FAIL] mkfs.erofs lz4hc dan none gagal, fallback ke make_ext4fs...")
                        if (outputFile.exists()) outputFile.delete()
                        // Fallback ke make_ext4fs
                        if (makeExt4 != null) {
                            val fallbackOk = repackExt4(makeExt4, outputFile, srcDir)
                            if (fallbackOk) {
                                copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                                OperationResult(
                                    true,
                                    "Repack Filesystem",
                                    "Repack ext4 (fallback) selesai",
                                    outputFile.absolutePath,
                                )
                            } else {
                                OperationResult(
                                    false,
                                    "Repack Filesystem",
                                    "mkfs.erofs & make_ext4fs all failed",
                                )
                            }
                        } else {
                            OperationResult(
                                false,
                                "Repack Filesystem",
                                "mkfs.erofs failed, make_ext4fs not available",
                            )
                        }
                    }
                }
            } else if (makeExt4 != null) {
                addLog("Running: make_ext4fs -s ${dirName}_repack.img $dirName/")
                val repackOk = repackExt4(makeExt4, outputFile, srcDir)
                if (repackOk) {
                    addLog("[OK] Repacked ext4: ${outputFile.name} (${outputFile.length()} bytes)")
                    updateProgress("Repack ext4 selesai! (${outputFile.length()} bytes)")
                    copyResultToDownload(outputFile.absolutePath, "${dirName}_repack.img")
                    OperationResult(
                        true,
                        "Repack Filesystem",
                        "Repack ext4 selesai",
                        outputFile.absolutePath,
                    )
                } else {
                    addLog("[FAIL] make_ext4fs gagal dan tidak ada mkfs.erofs sebagai fallback")
                    OperationResult(
                        false,
                        "Repack Filesystem",
                        "make_ext4fs failed, mkfs.erofs not available",
                    )
                }
            } else {
                addLog("[FAIL] Tidak ada binary repack (make_ext4fs, mkfs.erofs)")
                OperationResult(
                    false,
                    "Repack Filesystem",
                    "Tidak ada binary repack yang tersedia",
                )
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Repack Filesystem", e.message ?: "Unknown error")
        } finally {
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    /**
     * Helper untuk menjalankan mkfs.erofs dengan parameter kompresi spesifik.
     * Mengikuti standar Google:
     *   -z lz4hc,9 -C 4096 untuk kompresi tinggi (standar Android modern)
     *   -z none untuk uncompressed EROFS
     *   --file-contexts=<path> untuk menyertakan konteks keamanan
     */
    private suspend fun repackErofs(
        mkfsErofs: String,
        outputFile: File,
        srcDir: File,
        fileContextsPath: String?,
        compression: String,
    ): Boolean {
        if (outputFile.exists()) outputFile.delete()

        val cmdArgs =
            mutableListOf(
                mkfsErofs,
                "-z",
                compression,
                "-C",
                "4096",
            )
        if (fileContextsPath != null) {
            cmdArgs.add("--file-contexts=$fileContextsPath")
        }
        cmdArgs.add(outputFile.absolutePath)
        cmdArgs.add(srcDir.absolutePath)

        val compressionLabel =
            when (compression) {
                "lz4hc,9" -> "lz4hc level 9"
                "none" -> "tanpa kompresi"
                else -> compression
            }
        addLog(
            "[INFO] Menjalankan mkfs.erofs ($compressionLabel) dengan flags: -z $compression -C 4096${if (fileContextsPath != null) " --file-contexts=$fileContextsPath" else ""}",
        )

        val result =
            ShellExecutor.executeBinary(
                binaryPath = cmdArgs.first(),
                args = cmdArgs.drop(1),
                workingDir = getTempDir(),
                onOutput = { line -> addLog(line) },
            )

        if (result.exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
            addLog("[OK] mkfs.erofs ($compressionLabel) berhasil: ${outputFile.length()} bytes")
            return true
        }
        addLog("[FAIL] mkfs.erofs ($compressionLabel) gagal (exit ${result.exitCode})")
        result.errorOutput.forEach { addLog("[ERROR] $it") }
        return false
    }

    /**
     * Helper untuk menjalankan make_ext4fs dengan parameter yang benar.
     * Digunakan sebagai fallback ketika mkfs.erofs tidak tersedia.
     */
    private suspend fun repackExt4(
        makeExt4: String,
        outputFile: File,
        srcDir: File,
    ): Boolean {
        if (outputFile.exists()) outputFile.delete()

        val sizeBytes = storageManager.calculateDirSize(srcDir)
        val partitionSize = ((sizeBytes * 1.25).toLong() + 4095) / 4096 * 4096
        addLog("[INFO] Menjalankan make_ext4fs -s -l $partitionSize ${outputFile.name} ${srcDir.name}/")

        val result =
            ShellExecutor.executeBinary(
                binaryPath = makeExt4,
                args =
                    listOf(
                        "-s",
                        "-l",
                        partitionSize.toString(),
                        outputFile.absolutePath,
                        srcDir.absolutePath,
                    ),
                workingDir = getTempDir(),
                onOutput = { line -> addLog(line) },
            )

        if (result.exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
            addLog("[OK] make_ext4fs berhasil: ${outputFile.length()} bytes")
            return true
        }
        addLog("[FAIL] make_ext4fs gagal (exit ${result.exitCode})")
        result.errorOutput.forEach { addLog("[ERROR] $it") }
        return false
    }

    /**
     * Mencari file file_contexts di direktori hasil ekstraksi.
     * file_contexts diperlukan untuk menjaga hak akses (SELinux contexts)
     * saat me-repack dan mem-flash ke perangkat.
     */
    private fun findFileContexts(
        contentsDir: File,
        dirName: String,
    ): String? {
        val candidates =
            listOf(
                File(contentsDir, "file_contexts"),
                File(contentsDir, "config/file_contexts"),
                File(contentsDir.parentFile, "file_contexts"),
                File(contentsDir, "$dirName/file_contexts"),
            )
        for (f in candidates) {
            if (f.exists() && f.isFile) {
                addLog("[INFO] Ditemukan file_contexts: ${f.absolutePath}")
                return f.absolutePath
            }
        }
        addLog("[INFO] file_contexts tidak ditemukan, repo tanpa konteks keamanan")
        return null
    }

    suspend fun unpackBoot(
        inputUri: Uri,
        bootType: String,
    ): OperationResult {
        ensureDirs()
        val bootFile = File(getTempDir(), "boot/$bootType")
        if (!copyUriToFile(inputUri, bootFile)) {
            return failResult("Unpack Boot", "Failed to copy input file")
        }
        return unpackBoot(bootFile, bootType)
    }

    suspend fun unpackBoot(
        inputFile: File,
        bootType: String,
    ): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Unpack Boot", "Unpacking $bootType...")) {
            return failResult("Unpack Boot", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Unpack $bootType ===")
        addLog("[INFO] Menggunakan file: ${inputFile.absolutePath}")

        return try {
            val magisk =
                BinaryManager.getBinaryPath(context, "magiskboot")
                    ?: return failResult("Unpack Boot", "magiskboot not found (binary tidak ada)")

            val outDir = File(getTempDir(), "boot_out/${bootType}_out")
            outDir.mkdirs()

            // Copy boot image to outDir (magiskboot unpack works in current directory)
            val bootCopy = File(outDir, bootType)
            inputFile.copyTo(bootCopy, overwrite = true)

            updateProgress("Unpacking $bootType...")
            addLog("Running: magiskboot unpack $bootType (in ${outDir.name}/)")

            // Coba unpack dulu dengan -h (header dump) untuk deteksi tipe
            var result =
                ShellExecutor.executeBinary(
                    binaryPath = magisk,
                    args = listOf("unpack", "-h", bootCopy.absolutePath),
                    workingDir = outDir,
                    onOutput = { line -> addLog(line) },
                )

            // magiskboot exit codes: 0=valid, 1=error, 2=chromeos, 3=vendor_boot
            val successCodes = setOf(0, 2, 3)
            val isSuccess = result.exitCode in successCodes

            if (isSuccess) {
                when (result.exitCode) {
                    0 -> addLog("[OK] $bootType unpacked successfully")
                    2 -> addLog("[OK] $bootType unpacked (ChromeOS format)")
                    3 -> addLog("[OK] $bootType unpacked (vendor_boot format)")
                }
            } else if (result.exitCode == 139) {
                addLog("[WARN] magiskboot crash (exit 139) saat unpack $bootType")
                addLog("[WARN] Mungkin header tidak kompatibel, coba raw mode (-n)...")
                // Fallback: unpack tanpa decompress
                result =
                    ShellExecutor.executeBinary(
                        binaryPath = magisk,
                        args = listOf("unpack", "-n", "-h", bootCopy.absolutePath),
                        workingDir = outDir,
                        onOutput = { line -> addLog(line) },
                    )
                if (result.exitCode in successCodes) {
                    addLog("[OK] $bootType unpacked (raw mode, tanpa dekompresi)")
                } else {
                    addLog("[FAIL] Raw unpack juga gagal (exit ${result.exitCode})")
                    result.errorOutput.forEach { addLog("[ERROR] $it") }
                }
            } else {
                addLog("[FAIL] magiskboot unpack failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
            }

            // List extracted files
            if (outDir.exists()) {
                val files = outDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
                val excludeList = setOf(bootType, "new-boot.img")
                val extracted = files.filter { it !in excludeList }

                if (extracted.isNotEmpty()) {
                    addLog("--- Extracted files ---")
                    extracted.forEach { addLog("  - $it") }
                    addLog("[OK] $bootType unpacked to $outDir")
                    updateProgress("Unpack $bootType selesai!")
                    OperationResult(
                        true,
                        "Unpack $bootType",
                        "Boot unpacked",
                        outDir.absolutePath,
                    )
                } else if (isSuccess) {
                    // Magiskboot sukses tapi gak ada file tambahan (misal header-only)
                    addLog("[INFO] Tidak ada komponen tambahan yang diekstrak")
                    OperationResult(
                        true,
                        "Unpack $bootType",
                        "Boot unpacked (header only)",
                        outDir.absolutePath,
                    )
                } else {
                    addLog("[FAIL] Tidak ada file yang terekstrak")
                    OperationResult(
                        false,
                        "Unpack $bootType",
                        "magiskboot failed (exit ${result.exitCode})",
                    )
                }
            } else {
                addLog("[FAIL] Output directory tidak ditemukan")
                OperationResult(
                    false,
                    "Unpack $bootType",
                    "magiskboot failed (exit ${result.exitCode})",
                )
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Unpack $bootType", e.message ?: "Unknown error")
        } finally {
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    suspend fun repackBoot(
        bootType: String,
        customSourceDir: String? = null,
    ): OperationResult {
        ensureDirs()
        ensureForegroundRunning()
        if (!beginOperation("Repack Boot", "Repacking $bootType...")) {
            return failResult("Repack Boot", "Operasi lain masih berjalan, coba lagi nanti")
        }
        clearLogs()
        addLog("=== Repack $bootType ===")

        return try {
            val magisk =
                BinaryManager.getBinaryPath(context, "magiskboot")
                    ?: return failResult("Repack Boot", "magiskboot not found (binary tidak ada)")

            val outDir =
                if (customSourceDir != null) {
                    File(customSourceDir).also {
                        addLog("[INFO] Source folder: ${it.absolutePath}")
                    }
                } else {
                    File(getTempDir(), "boot_out/${bootType}_out")
                }
            if (!outDir.exists()) {
                return OperationResult(
                    false,
                    "Repack $bootType",
                    "No unpacked boot found at ${outDir.path}/",
                )
            }

            val repackedDir = File(getTempDir(), "repacked")
            repackedDir.mkdirs()
            val outputFile = File(repackedDir, bootType)

            // Copy the original boot image back if it exists
            val bootCopy = File(outDir, bootType)
            if (!bootCopy.exists()) {
                addLog("[FAIL] Original boot image not found in $outDir")
                return OperationResult(
                    false,
                    "Repack $bootType",
                    "Original boot image not found, re-extract first",
                )
            }

            updateProgress("Repacking $bootType...")
            addLog("Running: magiskboot repack $bootType")

            // magiskboot repack creates new-boot.img in the working directory
            val result =
                ShellExecutor.executeBinary(
                    binaryPath = magisk,
                    args = listOf("repack", bootCopy.absolutePath),
                    workingDir = outDir,
                    onOutput = { line -> addLog(line) },
                )

            // Check for new-boot.img in outDir
            val newBoot = File(outDir, "new-boot.img")
            if (result.exitCode == 0 && newBoot.exists()) {
                newBoot.copyTo(outputFile, overwrite = true)
                addLog("[OK] $bootType repacked: ${outputFile.absolutePath}")
                updateProgress("Repack $bootType selesai!")

                // Khusus vbmeta.img: sign AVB setelah repack
                if (bootType == "vbmeta.img") {
                    addLog("[INFO] vbmeta detected, signing with AVB 1.0...")
                    val signResult =
                        ShellExecutor.executeBinary(
                            binaryPath = magisk,
                            args = listOf("sign", outputFile.absolutePath),
                            workingDir = outDir,
                            onOutput = { line -> addLog(line) },
                        )
                    if (signResult.exitCode == 0) {
                        addLog("[OK] vbmeta signed successfully")
                    } else {
                        addLog("[WARN] vbmeta signing failed (exit ${signResult.exitCode})")
                        addLog("[WARN] Image tetap tersimpan tapi tanpa AVB signature")
                    }
                }

                copyResultToDownload(outputFile.absolutePath, bootType)
                OperationResult(
                    true,
                    "Repack $bootType",
                    "Repack selesai: $bootType",
                    outputFile.absolutePath,
                )
            } else {
                addLog("[FAIL] magiskboot repack failed (exit ${result.exitCode})")
                result.errorOutput.forEach { addLog("[ERROR] $it") }
                if (newBoot.exists()) {
                    // new-boot.img exists but exit code != 0 — coba paksa pakai itu
                    newBoot.copyTo(outputFile, overwrite = true)
                    addLog("[WARN] new-boot.img ditemukan meski exit code ${result.exitCode}, mencoba menyimpan...")
                    copyResultToDownload(outputFile.absolutePath, "${bootType}_forced")
                    OperationResult(
                        true,
                        "Repack $bootType",
                        "Repack selesai (forced): $bootType",
                        outputFile.absolutePath,
                    )
                } else {
                    OperationResult(
                        false,
                        "Repack $bootType",
                        "magiskboot repack failed (exit ${result.exitCode})",
                    )
                }
            }
        } catch (e: Exception) {
            addLog("[ERROR] ${e.message}")
            failResult("Repack $bootType", e.message ?: "Unknown error")
        } finally {
            flushLogs()
            _isRunning.value = false
            ensureForegroundStopped()
        }
    }

    fun cleanSelected(selectedDirs: List<String>) {
        val tempDir = getTempDir()
        // Safety: gunakan canonical path untuk cegah symlink traversal
        // Base untuk validasi adalah parent dari tempDir (yaitu workDir)
        val workDir = tempDir.parentFile ?: return
        val canonWork =
            try {
                workDir.canonicalPath
            } catch (e: Exception) {
                workDir.absolutePath
            }
        val canonTemp =
            try {
                tempDir.canonicalPath
            } catch (e: Exception) {
                tempDir.absolutePath
            }
        if (!canonTemp.startsWith(canonWork)) {
            addLog("[ERROR] Safety abort: temp dir is outside work directory!")
            return
        }
        if (!tempDir.exists()) {
            addLog("[WARN] Temp dir does not exist, nothing to clean")
            return
        }
        clearLogs()
        addLog("=== Clean Selected ===")

        for (dirName in selectedDirs) {
            val dir = File(tempDir, dirName)
            if (dir.exists()) {
                // Double-check path with canonical
                val canonDir =
                    try {
                        dir.canonicalPath
                    } catch (e: Exception) {
                        dir.absolutePath
                    }
                val canonTempSep =
                    try {
                        tempDir.canonicalPath + File.separator
                    } catch (e: Exception) {
                        tempDir.absolutePath +
                            File.separator
                    }
                if (!canonDir.startsWith(canonTempSep)) {
                    addLog("[ERROR] Safety abort: $dirName/ is outside temp dir!")
                    continue
                }
                dir.deleteRecursively()
                addLog("Cleaned: $dirName/")
            } else {
                addLog("[INFO] $dirName/ does not exist, skipping")
            }
        }
        ensureDirs()

        addLog("[OK] Clean selesai untuk ${selectedDirs.size} folder")
    }

    suspend fun listContentsDirs(): List<String> {
        val contentsDir = File(getTempDir(), "contents")
        if (!contentsDir.exists()) return emptyList()
        return contentsDir
            .listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }

    suspend fun exportSelectedToDownloads(selectedFolders: List<String>): OperationResult {
        return withContext(Dispatchers.IO) {
            _isRunning.value = true
            clearLogs()
            addLog("=== Export Selected to Downloads ===")
            var result: OperationResult = OperationResult(true, "Export Selected", "")
            try {
                updateProgress("Mengekspor folder terpilih ke Downloads/AFFT/...")
                val tempDir = getTempDir()
                if (!tempDir.exists()) {
                    return@withContext failResult("Export Selected", "Folder temp belum ada")
                }

                var copiedCount = 0
                for (subdir in selectedFolders) {
                    when (subdir) {
                        "input" -> {
                            val inputDir = getInputDir()
                            if (inputDir.exists()) {
                                val inputFiles = inputDir.listFiles()
                                if (!inputFiles.isNullOrEmpty()) {
                                    if (moveDirectoryToDownloads(inputDir, "input")) {
                                        copiedCount++
                                    }
                                } else {
                                    addLog("  [INFO] input/ is empty, skipping")
                                }
                            }
                        }
                        else -> {
                            val src = File(tempDir, subdir)
                            if (src.exists() && src.isDirectory) {
                                val files = src.listFiles()
                                if (!files.isNullOrEmpty()) {
                                    if (moveDirectoryToDownloads(src, subdir)) {
                                        copiedCount++
                                    }
                                } else {
                                    addLog("  [INFO] $subdir/ is empty, skipping")
                                }
                            } else {
                                addLog("  [INFO] $subdir/ does not exist, skipping")
                            }
                        }
                    }
                }

                if (copiedCount > 0) {
                    addLog("[OK] $copiedCount folder(s) diekspor ke Downloads/AFFT/")
                    updateProgress("Ekspor selesai! $copiedCount folder(s) exported")
                    result =
                        OperationResult(
                            true,
                            "Export Selected",
                            "Diekspor ke Downloads/AFFT/ ($copiedCount folders)",
                            "/storage/emulated/0/Download/AFFT",
                        )
                } else {
                    addLog("[INFO] Tidak ada data untuk diekspor")
                    updateProgress("Tidak ada data untuk diekspor")
                    result = OperationResult(true, "Export Selected", "Tidak ada data untuk diekspor")
                }
            } catch (e: Exception) {
                addLog("[ERROR] Export gagal: ${e.message}")
                result = failResult("Export Selected", e.message ?: "Unknown error")
            } finally {
                // Flush remaining logs
                flushLogs()
                _isRunning.value = false
                ensureForegroundStopped()
            }
            result
        }
    }

    suspend fun copyResultToDownload(
        resultPath: String,
        destName: String,
    ): Boolean {
        return try {
            val sourceFile = File(resultPath)
            if (!sourceFile.exists()) {
                if (logManager.debugMode) addLog("[DEBUG] Source file not found: $resultPath")
                return false
            }

            val downloadsDir = getExportDir()
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val destFile = File(downloadsDir, destName)
            withContext(Dispatchers.IO) {
                val total = sourceFile.length()
                val bufSize = 8192
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(bufSize)
                        var bytesCopied = 0L
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            if (total > 0 && bytesCopied % (total / 20 + 1) < bufSize) {
                                updateProgress("Copying to Downloads... ${(bytesCopied * 100 / total)}%")
                            }
                        }
                    }
                }
            }
            addLog("[OK] Copied to Downloads/AFFT/$destName (${formatFileSize(destFile.length())})")
            true
        } catch (e: Exception) {
            addLog("[ERROR] Copy failed: ${e.message}")
            false
        }
    }

    /**
     * List files in the input/ directory.
     */

    private suspend fun moveDirectoryToDownloads(
        srcDir: File,
        subdirName: String,
    ): Boolean =
        try {
            // Gunakan direct path (app punya MANAGE_EXTERNAL_STORAGE)
            val baseDir = getExportDir()
            if (!baseDir.exists()) baseDir.mkdirs()
            val destDir = File(baseDir, subdirName)
            // Hapus dulu jika sudah ada
            if (destDir.exists()) destDir.deleteRecursively()

            // Coba rename dulu (instan kalo satu filesystem)
            var moved = srcDir.renameTo(destDir)

            if (!moved) {
                // rename gagal (beda partisi), fallback copy (lebih lambat tapi aman)
                addLog("  [INFO] rename gagal (beda partisi?), fallback copy...")
                srcDir.copyRecursively(destDir, overwrite = true)
                if (destDir.exists()) {
                    // Hapus sumber, tapi jangan gagalkan export kalau hapus gagal
                    try {
                        srcDir.deleteRecursively()
                    } catch (e: Exception) {
                        addLog("  [WARN] Gagal menghapus sumber: ${e.message}")
                    }
                    moved = true
                }
            }

            if (moved && destDir.exists()) {
                val fileCount = destDir.walkTopDown().count() - 1
                addLog("  Dipindah: $subdirName/ ($fileCount items)")
                true
            } else {
                addLog("  [ERROR] Gagal memindah $subdirName/")
                false
            }
        } catch (e: Exception) {
            addLog("  [ERROR] Export $subdirName gagal: ${e.message}")
            false
        }

    suspend fun listInputFiles(): List<File> {
        val inputDir = getInputDir()
        if (!inputDir.exists()) return emptyList()
        return withContext(Dispatchers.IO) {
            inputDir.listFiles()?.sortedBy { it.name } ?: emptyList()
        }
    }

    /**
     * Get the most recently modified file from input/ directory.
     * Used to auto-select files after app restart.
     */
    suspend fun getLatestInputFile(): File? {
        val files = listInputFiles()
        return withContext(Dispatchers.IO) {
            files.maxByOrNull { it.lastModified() }
        }
    }
}
