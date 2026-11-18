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
import com.afft.app.util.deleteTreeNoFollow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
    private val filesystemContentsDir: (() -> File)? = null,
) {
    private fun allowedRoots(): List<Path> =
        listOfNotNull(
            workDir().canonicalFile.toPath(),
            exportDir().canonicalFile.toPath(),
            filesystemContentsDir?.invoke()?.canonicalFile?.toPath(),
        )

    private fun resolvedEntry(file: File): Path {
        val parent = file.absoluteFile.parentFile?.canonicalFile
            ?: throw IOException("Direktori induk tidak tersedia")
        return File(parent, file.name).toPath().normalize()
    }

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
                val target = resolvedEntry(file)
                val roots = allowedRoots()

                val allowed = roots.none { target == it } && roots.any { target.startsWith(it) }
                if (!allowed) {
                    val reason =
                        if (roots.any { target == it }) {
                            "direktori utama dilindungi"
                        } else {
                            "di luar folder yang diizinkan"
                        }
                    onLog("[ERROR] Safety abort: $target ($reason)")
                    return@withContext false
                }

                onLiveActivity("Menghapus ${file.name}...")
                if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("File tidak ditemukan: $target")
                }
                deleteTreeNoFollow(target.toFile())

                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    onLog("[ERROR] Gagal menghapus: ${file.name} (masih ada)")
                    false
                } else {
                    onLog("[OK] Dihapus: $target")
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
                if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Tidak dapat membuat tujuan: $destDir")
                if (!destDir.isDirectory) throw IOException("Tujuan bukan folder: $destDir")
                if (Files.isDirectory(src.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    destDir.canonicalFile.toPath().startsWith(src.canonicalFile.toPath())
                ) throw IOException("Tujuan berada di dalam sumber")
                val dest = resolveDestFile(src, destDir)
                onLog("[INFO] Menyalin: ${src.name} → ${dest.parent}")
                onLiveActivity("Menyalin ${src.name} → ${destDir.name}...")
                try {
                    copyTreeNoFollow(src.toPath(), dest.toPath())
                } catch (e: Exception) {
                    onLog("[ERROR] Salinan mungkin parsial: ${dest.absolutePath}; sumber tidak dihapus")
                    throw e
                }
                onLog("[OK] Disalin: ${dest.absolutePath}")
                onLog("[WARN] Metadata firmware belum diverifikasi; salinan bukan hasil siap repack atau flash")
                true
            } catch (e: CancellationException) {
                throw e
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
                val target = resolvedEntry(src)
                val roots = allowedRoots()
                if (roots.any { target == it } || roots.none { target.startsWith(it) }) {
                    onLog("[ERROR] Safety abort: sumber pindah di luar folder yang diizinkan: $target")
                    return@withContext false
                }
                if (Files.notExists(src.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    onLog("[ERROR] Sumber tidak ditemukan: ${src.absolutePath}")
                    return@withContext false
                }
                if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Tidak dapat membuat tujuan: $destDir")
                if (!destDir.isDirectory) throw IOException("Tujuan bukan folder: $destDir")
                if (Files.isDirectory(src.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    destDir.canonicalFile.toPath().startsWith(src.canonicalFile.toPath())
                ) throw IOException("Tujuan berada di dalam sumber")
                val dest = resolveDestFile(src, destDir)
                onLog("[INFO] Memindah: ${src.name} → ${dest.parent}")
                onLiveActivity("Memindah ${src.name} → ${destDir.name}...")

                if (src.renameTo(dest)) {
                    onLog("[OK] Dipindah: ${src.name} → ${destDir.name}/")
                    true
                } else {
                    onLog("[ERROR] Pemindahan langsung gagal; sumber tidak dihapus. Gunakan Salin jika diperlukan.")
                    false
                }
            } catch (e: CancellationException) {
                throw e
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
        while (Files.exists(dest.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val name = src.nameWithoutExtension
            val ext = src.extension
            val newName = if (ext.isNotEmpty()) "${name}_$counter.$ext" else "${name}_$counter"
            dest = File(destDir, newName)
            counter++
        }
        return dest
    }

    private suspend fun copyTreeNoFollow(source: Path, dest: Path) {
        coroutineContext.ensureActive()
        val attrs = Files.readAttributes(source, java.nio.file.attribute.BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        when {
            attrs.isSymbolicLink -> Files.createSymbolicLink(dest, Files.readSymbolicLink(source))
            attrs.isDirectory -> {
                Files.createDirectory(dest)
                Files.newDirectoryStream(source).use { entries ->
                    for (entry in entries) {
                        copyTreeNoFollow(entry, dest.resolve(entry.fileName))
                    }
                }
            }
            attrs.isRegularFile -> {
                Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                    Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (Files.size(dest) != attrs.size()) throw IOException("Ukuran salinan tidak cocok: $source")
            }
            else -> throw IOException("Jenis file tidak didukung: $source")
        }
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
