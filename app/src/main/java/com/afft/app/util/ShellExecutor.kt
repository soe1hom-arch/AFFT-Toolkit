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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val output: List<String>,
    val errorOutput: List<String>,
) {
    val isTimeout: Boolean get() = exitCode == -2
}

object ShellExecutor {
    private const val TAG = "ShellExecutor"

    /**
     * Check if an ELF binary is dynamically linked (has PT_INTERP program header).
     * Statically linked binaries cannot use linker64 fallback, so we detect it
     * at runtime to skip the linker64 attempt.
     */
    internal fun isDynamicElf(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x7F.toByte() ||
                    magic[1] != 'E'.code.toByte() ||
                    magic[2] != 'L'.code.toByte() ||
                    magic[3] != 'F'.code.toByte()
                ) {
                    return false
                }

                // Read e_type at offset 16 (2 bytes, little-endian)
                raf.seek(16)
                val typeBytes = ByteArray(2)
                raf.readFully(typeBytes)
                val eType = (typeBytes[0].toInt() and 0xFF) or ((typeBytes[1].toInt() and 0xFF) shl 8)

                // ET_DYN (3) = Shared object / PIE executable = always dynamically linked
                if (eType == 3) return true

                // ET_EXEC (2) = Executable - could be static or dynamic
                // Read e_phoff at offset 32 (8 bytes LE)
                raf.seek(32)
                val phoffBytes = ByteArray(8)
                raf.readFully(phoffBytes)
                var phoff = 0L
                for (i in 0..7) {
                    phoff = phoff or ((phoffBytes[i].toLong() and 0xFF) shl (i * 8))
                }

                // Read e_phentsize at offset 54 (2 bytes LE)
                raf.seek(54)
                val phentBytes = ByteArray(2)
                raf.readFully(phentBytes)
                val phentsize = (phentBytes[0].toInt() and 0xFF) or ((phentBytes[1].toInt() and 0xFF) shl 8)

                // Read e_phnum at offset 56 (2 bytes LE)
                raf.seek(56)
                val phnumBytes = ByteArray(2)
                raf.readFully(phnumBytes)
                val phnum = (phnumBytes[0].toInt() and 0xFF) or ((phnumBytes[1].toInt() and 0xFF) shl 8)

                if (phoff <= 0 || phnum <= 0 || phentsize < 8) return false

                // Scan program headers for PT_INTERP (type 3)
                for (i in 0 until phnum) {
                    val phAddr = phoff + i * phentsize
                    raf.seek(phAddr)
                    val ptypeBytes = ByteArray(4)
                    raf.readFully(ptypeBytes)
                    val ptype =
                        (ptypeBytes[0].toInt() and 0xFF) or
                            ((ptypeBytes[1].toInt() and 0xFF) shl 8) or
                            ((ptypeBytes[2].toInt() and 0xFF) shl 16) or
                            ((ptypeBytes[3].toInt() and 0xFF) shl 24)
                    if (ptype == 3) return true // PT_INTERP
                }
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "isDynamicElf: error reading ELF header: ${e.message}")
            // Assume dynamic so linker64 is attempted as fallback
            return true
        }
    }

    suspend fun execute(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null,
        timeoutMillis: Long = 0L,
    ): CommandResult {
        // Attempt 1: Direct execution
        val result = runCommand(command, workingDir, envVars, onOutput, timeoutMillis)

        // If process couldn't start (IOException, exitCode==-1) and the first arg is a file,
        // try fallback methods for native binary execution on Android 14+ where exec may be blocked.
        // Use isDynamicElf() to detect if the binary is dynamically linked (linker64-capable)
        // or statically linked (skip linker64, try sh -c directly).
        if (!result.isTimeout && result.exitCode == -1 && command.isNotEmpty()) {
            val firstArg = command[0]
            val binaryFile = File(firstArg)

            // Only attempt fallback if first arg is a file (not a shell command like "sh", "chmod")
            if (binaryFile.isFile()) {
                val isDynamic = isDynamicElf(binaryFile)
                Log.w(TAG, "execute: direct exec failed for $firstArg (isDynamic=$isDynamic)")

                // For dynamically linked binaries, try linker64 first.
                // linker64 has exec_type SELinux context which can load libraries from
                // directories that otherwise block direct execution.
                if (isDynamic) {
                    val linkerCommand = listOf("/system/bin/linker64") + command
                    val linkerResult = runCommand(linkerCommand, workingDir, envVars, onOutput, timeoutMillis)
                    if (linkerResult.isTimeout) return linkerResult
                    if (linkerResult.exitCode == 0) {
                        return linkerResult
                    }
                    Log.w(TAG, "execute: linker64 failed (exit ${linkerResult.exitCode}), trying sh -c")
                } else {
                    Log.w(TAG, "execute: binary is statically linked, skipping linker64, trying sh -c")
                }

                // Last resort - try via sh -c wrapper
                // Note: sh -c doesn't bypass SELinux, but may help with ProcessBuilder quirks
                val escapedCommand = command.joinToString(" ") { quoteShellArg(it) }
                val shCommand = listOf("sh", "-c", escapedCommand)
                val shResult = runCommand(shCommand, workingDir, envVars, onOutput, timeoutMillis)
                if (shResult.isTimeout) return shResult
                if (shResult.exitCode == 0) {
                    return shResult
                }
                Log.w(TAG, "execute: sh -c also failed (exit ${shResult.exitCode})")
            }
        }

        return result
    }

    suspend fun executeWithProgress(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onProgress: ((String) -> Unit)? = null,
        timeoutMillis: Long = 0L,
    ): CommandResult =
        execute(command, workingDir, envVars = envVars, onOutput = onProgress, timeoutMillis = timeoutMillis)

    fun buildBinaryCommand(
        context: Context,
        binaryName: String,
        args: List<String>,
    ): List<String> {
        val binaryPath =
            BinaryManager.getBinaryPath(context, binaryName)
                ?: throw IllegalStateException("Binary $binaryName not found")

        val binaryFile = File(binaryPath)
        // For dynamically linked binaries that fail direct execution (selinux/noexec),
        // we try direct first and fall back to linker64 via executeBinary()
        return listOf(binaryPath) + args
    }

    /**
     * Execute a native binary with automatic fallback to linker64 if direct
     * execution fails due to SELinux/noexec restrictions on Android 14+.
     *
     * The linker64 fallback runs the binary through the system linker:
     *   /system/bin/linker64 /path/to/binary args...
     * This works because linker64 has the proper SELinux domain (exec_type)
     * to load ELF binaries even from app data directories.
     */
    suspend fun executeBinary(
        binaryPath: String,
        args: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null,
        timeoutMillis: Long = 0L,
    ): CommandResult {
        val binaryFile = File(binaryPath)
        val isExecutable = binaryFile.canExecute()
        val exists = binaryFile.exists()

        Log.d(TAG, "executeBinary($binaryPath): exists=$exists execute=$isExecutable")

        // Attempt 1: Direct execution
        if (isExecutable) {
            val directCommand = listOf(binaryPath) + args
            Log.d(TAG, "executeBinary: trying direct execution")
            val result = runCommand(directCommand, workingDir, envVars, onOutput, timeoutMillis)
            if (result.exitCode == 0) {
                return result
            }
            if (result.isTimeout) return result
            if (result.exitCode == -1) {
                Log.w(TAG, "executeBinary: direct execution failed with IO error, trying fallbacks")
            } else {
                Log.w(TAG, "executeBinary: direct execution returned exit ${result.exitCode}")
                return result
            }
        } else if (exists) {
            Log.w(TAG, "executeBinary: not directly executable: $binaryPath")
        }

        // Check if dynamically linked (linker64 can help) or static (skip linker64)
        val isDynamic = exists && isDynamicElf(binaryFile)
        Log.d(TAG, "executeBinary: isDynamic=$isDynamic")

        // Attempt 2: For dynamic binaries, try linker64 fallback
        if (isDynamic) {
            val linkerCommand = listOf("/system/bin/linker64", binaryPath) + args
            Log.d(TAG, "executeBinary: trying linker64: ${linkerCommand.joinToString(" ")}")
            val linkerResult = runCommand(linkerCommand, workingDir, envVars, onOutput, timeoutMillis)
            if (linkerResult.isTimeout) return linkerResult
            if (linkerResult.exitCode == 0) {
                return linkerResult
            }
            Log.w(TAG, "executeBinary: linker64 failed (exit ${linkerResult.exitCode})")
        } else if (exists) {
            Log.w(TAG, "executeBinary: statically linked, skipping linker64")
        }

        // Attempt 3: Last resort - sh -c wrapper
        Log.w(TAG, "executeBinary: trying sh -c fallback")
        val escapedArgs = args.joinToString(" ") { quoteShellArg(it) }
        val shCommand = listOf("sh", "-c", "${quoteShellArg(binaryPath)} $escapedArgs")
        val shResult = runCommand(shCommand, workingDir, envVars, onOutput, timeoutMillis)
        if (shResult.isTimeout) return shResult
        if (shResult.exitCode == 0) {
            return shResult
        }
        Log.w(TAG, "executeBinary: sh -c failed (exit ${shResult.exitCode})")
        return shResult
    }

    /**
     * Quote sebuah argumen agar aman dipakai di dalam perintah "sh -c".
     * Membungkus dengan single quote dan mengecilkan single quote di dalamnya.
     */
    private fun quoteShellArg(arg: String): String = "'${arg.replace("'", "'\\''")}'"

    private suspend fun runCommand(
        command: List<String>,
        workingDir: File? = null,
        envVars: Map<String, String>? = null,
        onOutput: ((String) -> Unit)? = null,
        timeoutMillis: Long = 0L,
    ): CommandResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Command: ${command.joinToString(" ")}")

            val processBuilder =
                ProcessBuilder(command)
                    .redirectErrorStream(false)

            if (workingDir != null) {
                processBuilder.directory(workingDir)
            }

            if (envVars != null) {
                processBuilder.environment().putAll(envVars)
            }

            val process: Process
            try {
                process = processBuilder.start()
            } catch (e: Exception) {
                Log.e(TAG, "ProcessBuilder.start() failed for command: ${command.joinToString(" ")}", e)
                return@withContext CommandResult(
                    exitCode = -1,
                    output = emptyList(),
                    errorOutput = listOf("ProcessBuilder.start() failed: ${e.message}"),
                )
            }

            val output = mutableListOf<String>()
            val errorOutput = mutableListOf<String>()

            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val outputThread =
                Thread {
                    // Thread pembaca output TIDAK boleh crash saat proses
                    // dimusnahkan / stream ditutup dari thread lain (mis. batal
                    // karena pindah tab). libcore melempar UncheckedIOException
                    // (InterruptedIOException) saat read ditutup dari thread lain; tangkap
                    // di sini supaya tidak jadi FATAL EXCEPTION.
                    try {
                        outputReader.use { reader ->
                            reader.lines().forEach { line ->
                                synchronized(output) {
                                    output.add(line)
                                }
                                try {
                                    onOutput?.invoke(line)
                                } catch (_: Exception) {
                                    // callback UI dilempar karena layar sudah
                                    // ditutup / state korup — bukan crash.
                                }
                            }
                        }
                    } catch (_: java.io.UncheckedIOException) {
                        // stream ditutup saat proses dimusnahkan — bukan crash.
                    } catch (_: java.io.IOException) {
                        // stream ditutup saat proses dimusnahkan — bukan crash.
                    }
                }

            val errorThread =
                Thread {
                    try {
                        errorReader.use { reader ->
                            reader.lines().forEach { line ->
                                synchronized(errorOutput) {
                                    errorOutput.add(line)
                                }
                                try {
                                    onOutput?.invoke("[ERROR] $line")
                                } catch (_: Exception) {
                                    // callback UI dilempar karena layar sudah
                                    // ditutup / state korup — bukan crash.
                                }
                            }
                        }
                    } catch (_: java.io.UncheckedIOException) {
                        // stream ditutup saat proses dimusnahkan — bukan crash.
                    } catch (_: java.io.IOException) {
                        // stream ditutup saat proses dimusnahkan — bukan crash.
                    }
                }

            outputThread.start()
            errorThread.start()

            val exitCode: Int
            try {
                exitCode = awaitProcessExit(process, timeoutMillis)
            } catch (e: CancellationException) {
                // Coroutine dibatalkan (user meninggalkan screen, dsb):
                // bunuh proses agar tidak terus berjalan di background,
                // tunggu thread pembaca selesai, lalu teruskan pembatalan.
                Log.w(TAG, "runCommand: coroutine cancelled, destroying process")
                process.destroyForcibly()
                process.waitFor(2000, TimeUnit.MILLISECONDS)
                outputThread.join(2000)
                errorThread.join(2000)
                throw e
            }
            outputThread.join()
            errorThread.join()

            CommandResult(
                exitCode = exitCode,
                output = output.toList(),
                errorOutput = errorOutput.toList(),
            )
        }

    /**
     * Menunggu proses selesai secara CANCEL-AWARE (delay poll, bukan
     * blocking waitFor). Jika coroutine dibatalkan, [CancellationException]
     * dilempar dan pemanggil bertanggung jawab memusnahkan proses.
     */
    private suspend fun awaitProcessExit(
        process: Process,
        timeoutMillis: Long,
    ): Int {
        val deadline = if (timeoutMillis > 0) System.currentTimeMillis() + timeoutMillis else 0L
        while (process.isAlive) {
            if (deadline > 0 && System.currentTimeMillis() >= deadline) {
                Log.w(TAG, "runCommand: TIMEOUT after ${timeoutMillis}ms, destroying process")
                process.destroyForcibly()
                process.waitFor()
                return -2
            }
            delay(50)
        }
        return process.exitValue()
    }
}
