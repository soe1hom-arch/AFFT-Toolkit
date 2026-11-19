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

import com.afft.app.util.deleteTreeNoFollow
import com.afft.app.util.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.SimpleFileVisitor
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Operasi file & penyimpanan: cek ruang, hapus/rename/copy/move dengan
 * pembatalan coroutine, pemeliharaan symlink, serta ekspor yang divalidasi.
 * Dipakai [AFFTService].
 */
class StorageManager(
    private val workDir: () -> File,
    private val exportDir: () -> File,
    private val onLog: (String) -> Unit,
    private val onLiveActivity: (String) -> Unit,
    private val filesystemContentsDir: (() -> File)? = null,
    private val filesystemWorkspaceDir: (() -> File)? = null,
) {
    private data class TreeInspection(
        val size: Long,
        val hasSymlinks: Boolean,
        val posixPermissions: Set<Set<PosixFilePermission>>,
    )

    private fun allowedRoots(): List<Path> =
        listOfNotNull(
            workDir().canonicalFile.toPath(),
            exportDir().canonicalFile.toPath(),
            filesystemContentsDir?.invoke()?.canonicalFile?.toPath(),
            filesystemWorkspaceDir?.invoke()?.canonicalFile?.toPath(),
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
    ): Boolean =
        if (destDir.absoluteFile.toPath().normalize() == exportDir().absoluteFile.toPath().normalize()) {
            exportToDownloads(src)
        } else {
            withContext(Dispatchers.IO) {
                try {
                    copyToDirectoryInternal(src, destDir)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onLog("[ERROR] Gagal menyalin ${src.name}: ${e.message}")
                    false
                }
            }
        }

    suspend fun exportToDownloads(
        src: File,
        destName: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                exportToDownloadsInternal(src, destName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLog("[WARN] Ekspor ${src.name} dibatalkan; sumber asli tetap utuh: ${e.message}")
                false
            }
        }

    private suspend fun copyToDirectoryInternal(
        src: File,
        destDir: File,
    ): Boolean {
        requireSourceExists(src)
        if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Tidak dapat membuat tujuan: $destDir")
        if (!destDir.isDirectory) throw IOException("Tujuan bukan folder: $destDir")
        val sourcePath = src.absoluteFile.toPath().normalize()
        val destinationPath = destDir.canonicalFile.toPath()
        if (Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS) && destinationPath.startsWith(sourcePath)) {
            throw IOException("Tujuan berada di dalam sumber")
        }
        val dest = resolveDestFile(src, destDir)
        onLog("[INFO] Menyalin: ${src.name} → ${dest.parent}")
        onLiveActivity("Menyalin ${src.name} → ${destDir.name}...")
        try {
            copyTreeNoFollow(sourcePath, dest.toPath())
        } catch (e: Exception) {
            if (Files.exists(dest.toPath(), LinkOption.NOFOLLOW_LINKS)) deleteTreeNoFollow(dest)
            throw e
        }
        onLog("[OK] Disalin: ${dest.absolutePath}")
        onLog("[WARN] Metadata firmware belum diverifikasi; salinan bukan hasil siap repack atau flash")
        true
    }

    private suspend fun exportToDownloadsInternal(
        src: File,
        destName: String?,
    ): Boolean {
        requireSourceExists(src)
        validateFilesystemWorkspaceExport(src)

        val sourcePath = src.absoluteFile.toPath().normalize()
        val destinationDir = exportDir()
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw IOException("Tidak dapat membuat folder Downloads/AFFT")
        }
        if (!destinationDir.isDirectory) throw IOException("Downloads/AFFT bukan folder")
        val destinationPath = destinationDir.canonicalFile.toPath()
        if (Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS) && destinationPath.startsWith(sourcePath)) {
            throw IOException("Folder tujuan berada di dalam sumber")
        }

        val inspection = inspectTree(sourcePath)
        val available = destinationDir.usableSpace
        if (available <= 0L) throw IOException("Ruang penyimpanan tujuan tidak dapat diperiksa")
        if (inspection.size > available) {
            throw IOException(
                "Ruang penyimpanan tidak cukup: butuh ${formatFileSize(inspection.size)}, " +
                    "tersedia ${formatFileSize(available)}",
            )
        }
        verifyDestinationCapabilities(destinationDir.toPath(), inspection)

        val name = validateExportName(destName ?: src.name)
        val finalDest = resolveDestFile(File(name), destinationDir)
        val stagingDir = Files.createTempDirectory(destinationPath, ".afft-export-${UUID.randomUUID()}").toFile()
        val staged = File(stagingDir, name)
        try {
            onLiveActivity("Mengekspor ${src.name} ke Downloads/AFFT...")
            copyTreeNoFollow(sourcePath, staged.toPath())
            verifyCopiedTree(sourcePath, staged.toPath())
            Files.move(staged.toPath(), finalDest.toPath())
            onLog("[OK] Diekspor: ${finalDest.absolutePath}; sumber asli tetap utuh")
            true
        } finally {
            if (Files.exists(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) deleteTreeNoFollow(stagingDir)
        }
    }

    private fun validateFilesystemWorkspaceExport(src: File) {
        val workspaceRoot = filesystemWorkspaceDir?.invoke()?.absoluteFile?.toPath()?.normalize() ?: return
        val source = src.absoluteFile.toPath().normalize()
        if (!source.startsWith(workspaceRoot)) return
        if (source != workspaceRoot) {
            throw IOException("Ekspor filesystem harus mencakup seluruh workspace agar fs_config ikut tersimpan")
        }

        val metadataDir = workspaceRoot.resolve("fs_config")
        if (!Files.exists(metadataDir, LinkOption.NOFOLLOW_LINKS)) return
        Files.newDirectoryStream(metadataDir, "*.required").use { markers ->
            for (marker in markers) {
                val config = metadataDir.resolve(marker.fileName.toString().removeSuffix(".required") + ".txt")
                if (String(Files.readAllBytes(marker), StandardCharsets.UTF_8).trim() != "complete" ||
                    !Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS) ||
                    Files.size(config) == 0L
                ) {
                    throw IOException("Metadata filesystem ${marker.fileName} belum lengkap; ekspor dibatalkan")
                }
            }
        }
    }

    private fun requireSourceExists(src: File) {
        if (Files.notExists(src.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Sumber tidak ditemukan: ${src.absolutePath}")
        }
    }

    private fun validateExportName(name: String): String {
        val safeName = name.trim()
        if (safeName.isEmpty() || safeName == "." || safeName == ".." ||
            safeName.contains('/') || safeName.contains('\\')
        ) {
            throw IOException("Nama ekspor tidak valid: $name")
        }
        return safeName
    }

    private fun inspectTree(source: Path): TreeInspection {
        var size = 0L
        var hasSymlinks = false
        val permissions = mutableSetOf<Set<PosixFilePermission>>()
        Files.walkFileTree(
            source,
            object : SimpleFileVisitor<Path>() {
                private fun inspect(path: Path, attrs: BasicFileAttributes) {
                    when {
                        attrs.isSymbolicLink -> hasSymlinks = true
                        attrs.isDirectory -> Unit
                        attrs.isRegularFile -> size = Math.addExact(size, attrs.size())
                        else -> throw IOException("Jenis file tidak didukung: $path")
                    }
                    if (!attrs.isSymbolicLink) {
                        readPosixPermissions(path)?.let(permissions::add)
                    }
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    inspect(dir, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    inspect(file, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    throw exc
                }
            },
        )
        return TreeInspection(size, hasSymlinks, permissions)
    }

    private fun verifyDestinationCapabilities(
        destinationDir: Path,
        inspection: TreeInspection,
    ) {
        val probe = Files.createTempFile(destinationDir, ".afft-capability-", ".tmp")
        val probeLink = probe.resolveSibling("${probe.fileName}.link")
        try {
            val probeTime = FileTime.fromMillis(1_600_000_000_000L)
            Files.setLastModifiedTime(probe, probeTime)
            if (Files.getLastModifiedTime(probe) != probeTime) {
                throw IOException("Tujuan tidak dapat mempertahankan waktu modifikasi file")
            }

            if (inspection.posixPermissions.isNotEmpty()) {
                if (Files.getFileAttributeView(probe, PosixFileAttributeView::class.java) == null) {
                    throw IOException("Tujuan tidak mendukung metadata izin POSIX")
                }
                inspection.posixPermissions.forEach { mode ->
                    Files.setPosixFilePermissions(probe, mode)
                    if (Files.getPosixFilePermissions(probe) != mode) {
                        throw IOException("Tujuan tidak dapat mempertahankan metadata izin POSIX")
                    }
                }
            }

            if (inspection.hasSymlinks) {
                Files.createSymbolicLink(probeLink, probe.fileName)
                if (!Files.isSymbolicLink(probeLink) || Files.readSymbolicLink(probeLink) != probe.fileName) {
                    throw IOException("Tujuan tidak dapat mempertahankan symbolic link")
                }
                val linkView =
                    Files.getFileAttributeView(
                        probeLink,
                        BasicFileAttributeView::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    ?: throw IOException("Tujuan tidak dapat menyimpan metadata symbolic link")
                linkView.setTimes(probeTime, null, null)
                if (Files.getLastModifiedTime(probeLink, LinkOption.NOFOLLOW_LINKS) != probeTime) {
                    throw IOException("Tujuan tidak dapat mempertahankan waktu modifikasi symbolic link")
                }
            }
        } catch (e: UnsupportedOperationException) {
            throw IOException("Tujuan tidak mendukung metadata atau symbolic link yang dibutuhkan", e)
        } finally {
            try {
                Files.deleteIfExists(probeLink)
            } finally {
                Files.deleteIfExists(probe)
            }
        }
    }

    private fun verifyCopiedTree(source: Path, destination: Path) {
        val sourceAttrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val destinationAttrs =
            Files.readAttributes(destination, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        when {
            sourceAttrs.isSymbolicLink -> {
                if (!destinationAttrs.isSymbolicLink ||
                    Files.readSymbolicLink(source) != Files.readSymbolicLink(destination)
                ) {
                    throw IOException("Target symbolic link tidak cocok: $source")
                }
                if (sourceAttrs.lastModifiedTime() != destinationAttrs.lastModifiedTime()) {
                    throw IOException("Waktu modifikasi symbolic link tidak cocok: $source")
                }
            }
            sourceAttrs.isDirectory -> {
                if (!destinationAttrs.isDirectory) throw IOException("Struktur folder tidak cocok: $source")
                Files.newDirectoryStream(source).use { entries ->
                    for (entry in entries) {
                        verifyCopiedTree(entry, destination.resolve(entry.fileName))
                    }
                }
                verifyMetadata(source, destination, sourceAttrs, destinationAttrs)
            }
            sourceAttrs.isRegularFile -> {
                if (!destinationAttrs.isRegularFile || sourceAttrs.size() != destinationAttrs.size()) {
                    throw IOException("Ukuran file ekspor tidak cocok: $source")
                }
                if (!sameFileContents(source, destination)) {
                    throw IOException("Isi file ekspor tidak cocok: $source")
                }
                verifyMetadata(source, destination, sourceAttrs, destinationAttrs)
            }
        }
    }

    private fun sameFileContents(
        source: Path,
        destination: Path,
    ): Boolean {
        Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { sourceInput ->
            Files.newInputStream(
                destination,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS,
            ).use { destinationInput ->
                val sourceBuffer = ByteArray(1 shl 16)
                val destinationBuffer = ByteArray(sourceBuffer.size)
                while (true) {
                    val sourceRead = sourceInput.read(sourceBuffer)
                    val destinationRead = destinationInput.read(destinationBuffer)
                    if (sourceRead != destinationRead) return false
                    if (sourceRead < 0) return true
                    for (index in 0 until sourceRead) {
                        if (sourceBuffer[index] != destinationBuffer[index]) return false
                    }
                }
            }
        }
    }

    private fun verifyMetadata(
        source: Path,
        destination: Path,
        sourceAttrs: BasicFileAttributes,
        destinationAttrs: BasicFileAttributes,
    ) {
        if (sourceAttrs.lastModifiedTime() != destinationAttrs.lastModifiedTime()) {
            throw IOException("Waktu modifikasi tidak cocok: $source")
        }
        val sourcePermissions = readPosixPermissions(source)
        if (sourcePermissions != null && readPosixPermissions(destination) != sourcePermissions) {
            throw IOException("Metadata izin POSIX tidak cocok: $source")
        }
    }

    suspend fun moveFileTo(
        src: File,
        destDir: File,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destination = destDir.canonicalFile.toPath()
                val internalRoots =
                    listOf(workDir().canonicalFile.toPath(), filesystemWorkspaceDir?.invoke()?.canonicalFile?.toPath())
                        .filterNotNull()
                val target = resolvedEntry(src)
                val sourceRoot = internalRoots.firstOrNull { target != it && target.startsWith(it) }
                val destinationRoot = internalRoots.firstOrNull { destination.startsWith(it) }
                if (sourceRoot == null || sourceRoot != destinationRoot) {
                    onLog(
                        "[ERROR] Pemindahan hanya diizinkan dalam workspace yang sama; " +
                            "gunakan Salin untuk memindahkan antar-workspace",
                    )
                    return@withContext false
                }
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

    private fun readPosixPermissions(path: Path): Set<PosixFilePermission>? {
        val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            ?: return null
        return view.readAttributes().permissions()
    }

    private suspend fun copyTreeNoFollow(source: Path, dest: Path) {
        coroutineContext.ensureActive()
        val attrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        when {
            attrs.isSymbolicLink -> {
                Files.createSymbolicLink(dest, Files.readSymbolicLink(source))
                val view =
                    Files.getFileAttributeView(
                        dest,
                        BasicFileAttributeView::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    ?: throw IOException("Tujuan tidak dapat menyimpan metadata symbolic link: $dest")
                view.setTimes(attrs.lastModifiedTime(), null, null)
            }
            attrs.isDirectory -> {
                Files.createDirectory(dest)
                Files.newDirectoryStream(source).use { entries ->
                    for (entry in entries) {
                        copyTreeNoFollow(entry, dest.resolve(entry.fileName))
                    }
                }
                applyFileMetadata(source, dest, attrs)
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
                applyFileMetadata(source, dest, attrs)
            }
            else -> throw IOException("Jenis file tidak didukung: $source")
        }
    }

    private fun applyFileMetadata(
        source: Path,
        destination: Path,
        attrs: BasicFileAttributes,
    ) {
        readPosixPermissions(source)?.let { permissions ->
            Files.setPosixFilePermissions(destination, permissions)
        }
        val view = Files.getFileAttributeView(destination, BasicFileAttributeView::class.java)
            ?: throw IOException("Tujuan tidak dapat menyimpan waktu modifikasi: $destination")
        view.setTimes(attrs.lastModifiedTime(), null, null)
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
