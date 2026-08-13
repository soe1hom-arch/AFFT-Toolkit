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

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pipeline log AFFT: buffer in-memory, throttle StateFlow, dan file log
 * opsional. Dipakai [AFFTService]; mutasi buffer diamankan [logLock]
 * karena addLog dipanggil dari thread shell dan coroutine bersamaan.
 */
class LogManager(
    private val prefs: SharedPreferences?,
    private val tempDir: () -> File,
    private val exportDir: () -> File,
    private val onLogsChanged: (List<String>) -> Unit,
) {
    private val logBuffer = mutableListOf<String>()
    private val maxInMemoryLogs = 1500
    private val keyLogToFile = "log_to_file"

    var debugMode: Boolean = false
        private set
    var logToFileEnabled: Boolean = false
        private set
    var currentLogFile: File? = null
        private set

    private var lastStateUpdate = 0L
    private val stateUpdateInterval = 300L
    private val logLock = Any()

    /** Baca preferensi & buka file log bila pencatatan aktif. */
    fun init() {
        logToFileEnabled = prefs?.getBoolean(keyLogToFile, false) ?: false
        if (logToFileEnabled) {
            try {
                initLogFile()
            } catch (_: Exception) {
            }
        }
    }

    fun setLogToFileEnabled(enabled: Boolean) {
        logToFileEnabled = enabled
        try {
            prefs?.edit()?.putBoolean(keyLogToFile, enabled)?.apply()
        } catch (e: Exception) {
            Log.w("AFFTService", "Gagal simpan preferensi log: ${e.message}")
        }
        if (enabled && currentLogFile == null) {
            try {
                initLogFile()
            } catch (_: Exception) {
            }
        } else if (!enabled) {
            currentLogFile = null
        }
    }

    fun toggleDebug() {
        debugMode = !debugMode
        addLog("[INFO] Debug mode: ${if (debugMode) "ON" else "OFF"}")
    }

    fun getLogsDir(): File = File(tempDir(), "logs")

    suspend fun getLogFiles(): List<File> {
        val logsDir = getLogsDir()
        if (!logsDir.exists()) return emptyList()
        return withContext(Dispatchers.IO) {
            logsDir
                .listFiles()
                ?.filter { it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    suspend fun getLogContent(logFile: File): String =
        withContext(Dispatchers.IO) {
            try {
                logFile.readText()
            } catch (e: Exception) {
                "Gagal membaca log: ${e.message}"
            }
        }

    suspend fun clearOldLogs(maxFiles: Int = 20) {
        withContext(Dispatchers.IO) {
            try {
                val logsDir = getLogsDir()
                if (!logsDir.exists()) return@withContext
                val files =
                    logsDir
                        .listFiles()
                        ?.filter { it.name.endsWith(".txt") }
                        ?.sortedByDescending { it.lastModified() } ?: return@withContext
                if (files.size <= maxFiles) return@withContext
                files.drop(maxFiles).forEach { file ->
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun addLog(text: String) {
        if (isProgressBarLine(text)) {
            return
        }
        if (text.contains("skipped inaccessible xattr", ignoreCase = true) ||
            text.contains("posix_acl_default", ignoreCase = true) ||
            text.contains("posix_acl_access", ignoreCase = true) ||
            text.contains("erofs: skipped", ignoreCase = true)
        ) {
            return
        }

        synchronized(logLock) {
            logBuffer.add(text)
            if (logBuffer.size > maxInMemoryLogs + 200) {
                val excess = logBuffer.size - maxInMemoryLogs
                repeat(excess) { logBuffer.removeAt(0) }
            }

            val now = System.currentTimeMillis()
            if (now - lastStateUpdate >= stateUpdateInterval) {
                onLogsChanged(logBuffer.toList())
                lastStateUpdate = now
            }
        }

        Log.d("AFFTService", text)
        val logFile = currentLogFile
        if (logToFileEnabled && logFile != null) {
            try {
                synchronized(logLock) {
                    logFile.appendText("$text\n")
                }
            } catch (e: Exception) {
                Log.w("AFFTService", "Gagal tulis log file: ${e.message}")
            }
        }
    }

    /** Publish snapshot log terbaru secara paksa (dipanggil saat operasi selesai). */
    fun flush() {
        synchronized(logLock) {
            onLogsChanged(logBuffer.toList())
        }
    }

    /** Kosongkan buffer log & buka file log baru. */
    fun clear() {
        synchronized(logLock) {
            logBuffer.clear()
        }
        onLogsChanged(emptyList())
        initLogFile()
    }

    /**
     * Simpan snapshot log ke file di Downloads/AFFT/logs/.
     */
    suspend fun saveCurrentLogToDownloads(logs: List<String>): File? {
        if (logs.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val downloadDir = File(exportDir(), "logs")
                downloadDir.mkdirs()
                val timestamp =
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US,
                    ).format(Date())
                val logFile = File(downloadDir, "afft_log_$timestamp.txt")
                logFile.writeText(logs.joinToString("\n"))
                addLog("[OK] Log tersimpan: ${logFile.absolutePath}")
                logFile
            } catch (e: Exception) {
                addLog("[ERROR] Gagal simpan log: ${e.message}")
                null
            }
        }
    }

    internal fun initLogFile() {
        try {
            val logsDir = getLogsDir()
            logsDir.mkdirs()
            val timestamp =
                SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.US,
                ).format(Date())
            currentLogFile = File(logsDir, "log_$timestamp.txt")
            currentLogFile?.writeText("=== AFFT Log Session: $timestamp ===\n")
            Log.d("AFFTService", "[LOG] Log file: ${currentLogFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("AFFTService", "Gagal init log file: ${e.message}")
        }
    }

    private fun isProgressBarLine(text: String): Boolean {
        if (text.contains("[")) return true
        if (text.isBlank()) return true
        return text.matches(Regex(""".*\[[= >]+\].*\d+%"""))
    }
}
