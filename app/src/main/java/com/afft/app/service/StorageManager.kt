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

import com.afft.app.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Operasi file & penyimpanan: cek ruang, hapus/rename/copy/move dengan
 * keamanan (hanya di dalam work dir & Downloads/AFFT), dan pembatalan
 * coroutine saat menyalin. Dipakai [AFFTService].
 */
class StorageManager(
    private val workDir: () -> File,
    private val exportDir: () -> File,
    private val onLog: (String) -> Unit,
    private val onLiveActivity: (String) -> Unit,
) {
    fun getFreeSpace(path: String): Long =
        try {
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()
            dir.freeSpace
        } catch (e: Exception) {
            -1L
        }

    fun checkStorageSpace(
        fileSize: Long,
        destPath: String,
    ): Boolean {
        val free = getFreeSpace(destPath)
        if (free <= 0) return true // can't check, allow
        if (fileSize > free) {
            onLog(
                "[ERROR] Ruang penyimpanan tidak cukup! Butuh ${formatFileSize(
                    fileSize,
                )}, tersedia ${formatFileSize(free)}",
            )
            return false
        }
        return true
    }

    suspend fun deleteFileWithSafety(file: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val workDir = workDir()
                val canonWork = workDir.canonicalPath
                val canonFile = file.canonicalPath
                val downloadAFFT = exportDir()
                val canonDl = downloadAFFT.canonicalPath

                // Izinkan hapus di workDir ATAU di Downloads/AFFT
                val allowed =
                    canonFile.startsWith(canonWork + File.separator) ||
                        canonFile.startsWith(canonDl + File.separator)
                if (!allowed) {
                    onLog("[ERROR] Safety abort: ${file.name} di luar work dir & Downloads/AFFT!")
                    return@withContext false
                }

                onLiveActivity("Menghapus ${file.name}...")
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }

                if (file.exists()) {
                    onLog("[ERROR] Gagal menghapus: ${file.name} (masih ada)")
                    false
                } else {
                    onLog("[OK] Dihapus: ${file.name}")
                    true
                }
            } catch (e: Exception) {
                onLog("[ERROR] Gagal menghapus ${file.name}: ${e.message}")
                false
            }
        }
    }

    suspend fun createFolder(
        parentDir: File,
        name: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val safeName = name.trim()
            if (safeName.isEmpty() || safeName.contains('/') || safeName.contains('\\')) {
                onLog("[ERROR] Nama folder tidak valid: $name")
                return@withContext false
            }
            val target = File(parentDir, safeName)
            if (target.exists()) {
                onLog("[ERROR] Sudah ada: ${target.absolutePath}")
                return@withContext false
            }
            val ok =
                try {
                    target.mkdirs()
                } catch (e: Exception) {
                    onLog("[ERROR] Gagal membuat folder: ${e.message}")
                    false
                }
            if (ok) {
                onLog("[OK] Folder dibuat: ${target.absolutePath}")
            } else {
                onLog("[ERROR] Gagal membuat folder: ${target.absolutePath}")
            }
            ok
        }
    }

    suspend fun renameFile(
        file: File,
        newName: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val safeName = newName.trim()
            if (safeName.isEmpty() || safeName.contains('/') || safeName.contains('\\')) {
                onLog("[ERROR] Nama tidak valid: $newName")
                return@withContext false
            }
            val dest = File(file.parentFile, safeName)
            if (dest.exists()) {
                onLog("[ERROR] Sudah ada: ${dest.absolutePath}")
                return@withContext false
            }
            onLiveActivity("Mengubah nama ${file.name}...")
            val ok =
                try {
                    file.renameTo(dest)
                } catch (e: Exception) {
                    onLog("[ERROR] Gagal rename: ${e.message}")
                    false
                }
            if (ok) {
                onLog("[OK] Diubah nama: ${file.name} → $safeName")
            } else {
                onLog("[ERROR] Gagal mengubah nama: ${file.name}")
            }
            ok
        }
    }

    suspend fun copyFileTo(
        src: File,
        destDir: File,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Validasi sumber
                if (!src.exists()) {
                    onLog("[ERROR] Sumber tidak ditemukan: ${src.absolutePath}")
                    return@withContext false
                }
                if (!src.canRead()) {
                    onLog("[ERROR] Tidak bisa membaca: ${src.absolutePath} (izin?)")
                    return@withContext false
                }

                val size =
                    if (src.isDirectory) {
                        src.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        src.length()
                    }
                onLog("[INFO] Ukuran: ${formatFileSize(size)}")

                if (!checkStorageSpace(size, destDir.absolutePath)) {
                    return@withContext false
                }
                if (!destDir.exists()) destDir.mkdirs()
                if (!destDir.exists()) {
                    onLog("[ERROR] Gagal membuat folder tujuan: ${destDir.absolutePath}")
                    return@withContext false
                }

                val dest = resolveDestFile(src, destDir)
                onLog("[INFO] Menyalin: ${src.name} → ${dest.parent}")
                onLiveActivity("Menyalin ${src.name} → ${destDir.name}...")

                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = false)
                } else {
                    // Gunakan stream untuk memastikan file benar-benar tersalin
                    src.inputStream().use { input ->
                        dest.outputStream().use { output ->
                            copyStreamCancellable(input, output)
                        }
                    }
                }

                // Verifikasi hasil
                if (dest.exists() && (dest.isDirectory || dest.length() == src.length())) {
                    onLog("[OK] Disalin: ${src.name} → ${destDir.name}/")
                    true
                } else {
                    val destSize = if (dest.exists()) formatFileSize(dest.length()) else "0"
                    onLog("[ERROR] Hasil copy tidak valid! Dest size: $destSize")
                    false
                }
            } catch (e: Exception) {
                onLog("[ERROR] Gagal menyalin ${src.name}: ${e.message}")
                false
            }
        }
    }

    suspend fun moveFileTo(
        src: File,
        destDir: File,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!src.exists()) {
                    onLog("[ERROR] Sumber tidak ditemukan: ${src.absolutePath}")
                    return@withContext false
                }
                val size =
                    if (src.isDirectory) {
                        src.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        src.length()
                    }
                if (!checkStorageSpace(size, destDir.absolutePath)) {
                    return@withContext false
                }
                if (!destDir.exists()) destDir.mkdirs()
                val dest = resolveDestFile(src, destDir)
                onLog("[INFO] Memindah: ${src.name} → ${dest.parent}")
                onLiveActivity("Memindah ${src.name} → ${destDir.name}...")

                var moved = src.renameTo(dest)
                if (!moved) {
                    onLog("[INFO] rename gagal, fallback copy+delete...")
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                        src.deleteRecursively()
                    } else {
                        src.inputStream().use { input ->
                            dest.outputStream().use { output ->
                                copyStreamCancellable(input, output)
                            }
                        }
                        src.delete()
                    }
                    moved = true
                }

                if (dest.exists()) {
                    onLog("[OK] Dipindah: ${src.name} → ${destDir.name}/")
                    true
                } else {
                    onLog("[ERROR] Gagal memindah: ${src.name}")
                    false
                }
            } catch (e: Exception) {
                onLog("[ERROR] Gagal memindah ${src.name}: ${e.message}")
                false
            }
        }
    }

    internal fun resolveDestFile(
        src: File,
        destDir: File,
    ): File {
        var dest = File(destDir, src.name)
        var counter = 1
        while (dest.exists()) {
            val name = src.nameWithoutExtension
            val ext = src.extension
            val newName = if (ext.isNotEmpty()) "${name}_$counter.$ext" else "${name}_$counter"
            dest = File(destDir, newName)
            counter++
        }
        return dest
    }

    internal suspend fun copyStreamCancellable(
        input: java.io.InputStream,
        output: FileOutputStream,
    ) {
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            coroutineContext.ensureActive()
        }
    }

    internal fun calculateDirSize(dir: File): Long =
        try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            16777216L // 16MB default
        }
}
