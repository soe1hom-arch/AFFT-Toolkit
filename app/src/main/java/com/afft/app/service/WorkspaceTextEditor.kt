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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.UserDefinedFileAttributeView
import kotlin.coroutines.coroutineContext

class WorkspaceTextDocument internal constructor(
    val file: File,
    val text: String,
    internal val originalBytes: ByteArray,
    internal val fileKey: Any?,
    internal val hadBom: Boolean,
)

class WorkspaceTextEditor(private val workspaceRoot: () -> File) {
    companion object {
        const val MAX_FILE_SIZE = 1024 * 1024
        private val BOM = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
    }

    suspend fun open(file: File): Result<WorkspaceTextDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = checkedFile(file)
                val attributes = attributes(path)
                rejectHardLinks(path)
                val bytes = readLimited(path)
                val hadBom = bytes.size >= BOM.size && bytes.copyOfRange(0, BOM.size).contentEquals(BOM)
                val bomSize = if (hadBom) BOM.size else 0
                val text =
                    try {
                        StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, bomSize, bytes.size - bomSize))
                            .toString()
                    } catch (e: CharacterCodingException) {
                        throw IOException("File bukan teks UTF-8 yang valid", e)
                    }
                requireText(text)
                WorkspaceTextDocument(file, text, bytes, attributes.fileKey(), hadBom)
            }
        }

    suspend fun save(document: WorkspaceTextDocument, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireText(text)
                val encoded =
                    StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(text))
                val bomSize = if (document.hadBom) BOM.size else 0
                if (encoded.remaining() + bomSize > MAX_FILE_SIZE) {
                    throw IOException("File melebihi batas editor 1 MiB")
                }
                val bytes = ByteArray(encoded.remaining() + bomSize)
                var offset = 0
                if (document.hadBom) {
                    BOM.copyInto(bytes)
                    offset = BOM.size
                }
                encoded.get(bytes, offset, encoded.remaining())

                val path = checkedFile(document.file)
                verifyUnchanged(path, document)
                if (bytes.contentEquals(document.originalBytes)) return@runCatching
                val sourcePosix = posixAttributes(path)
                val sourceMode = unixMode(path)
                val temporary = Files.createTempFile(path.parent, ".afft-edit-", ".tmp")
                try {
                    Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    Files.setAttribute(temporary, "unix:mode", sourceMode or 0x80, LinkOption.NOFOLLOW_LINKS)
                    Files.newOutputStream(
                        temporary,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    ).use {
                        it.write(bytes)
                    }
                    Files.setAttribute(temporary, "unix:mode", sourceMode, LinkOption.NOFOLLOW_LINKS)
                    if (!readLimited(temporary).contentEquals(bytes)) {
                        throw IOException("Isi file sementara tidak cocok; file asli tidak diubah")
                    }
                    val copiedPosix = posixAttributes(temporary)
                    if (sourcePosix.permissions() != copiedPosix.permissions() ||
                        sourcePosix.owner() != copiedPosix.owner() ||
                        sourcePosix.group() != copiedPosix.group() ||
                        sourceMode != unixMode(temporary)
                    ) {
                        throw IOException("Metadata izin, pemilik, atau mode tidak dapat dipertahankan")
                    }
                    verifyUserAttributes(path, temporary)
                    checkedFile(document.file)
                    verifyUnchanged(path, document)
                    val currentPosix = posixAttributes(path)
                    if (sourcePosix.permissions() != currentPosix.permissions() ||
                        sourcePosix.owner() != currentPosix.owner() ||
                        sourcePosix.group() != currentPosix.group() ||
                        sourceMode != unixMode(path)
                    ) {
                        throw IOException("Metadata file berubah sejak dibuka untuk penyimpanan")
                    }
                    coroutineContext.ensureActive()
                    try {
                        Files.move(
                            temporary,
                            path,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (e: AtomicMoveNotSupportedException) {
                        throw IOException("Workspace tidak mendukung penyimpanan atomik; file asli tidak diubah", e)
                    }
                } finally {
                    Files.deleteIfExists(temporary)
                }
                Unit
            }
        }

    private fun checkedFile(file: File): Path {
        val root = workspaceRoot().absoluteFile.toPath().normalize()
        val path = file.absoluteFile.toPath().normalize()
        if (path == root || !path.startsWith(root)) {
            throw IOException("Editor hanya dapat mengubah file di workspace filesystem internal")
        }
        var current = root
        for (part in root.relativize(path)) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) {
                throw IOException("Editor tidak mengikuti symbolic link: $current")
            }
        }
        if (!attributes(path).isRegularFile) throw IOException("Bukan file biasa: $path")
        return path
    }

    private fun attributes(path: Path): BasicFileAttributes =
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

    private fun posixAttributes(path: Path): PosixFileAttributes =
        Files.readAttributes(path, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

    private fun unixMode(path: Path): Int =
        (Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS) as Number).toInt()

    private fun verifyUserAttributes(source: Path, destination: Path) {
        val sourceView =
            Files.getFileAttributeView(source, UserDefinedFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                ?: return
        val destinationView =
            Files.getFileAttributeView(destination, UserDefinedFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                ?: throw IOException("Tujuan tidak mendukung extended attributes")
        val names = sourceView.list().sorted()
        if (names != destinationView.list().sorted()) throw IOException("Extended attributes tidak dapat dipertahankan")
        for (name in names) {
            val sourceBuffer = ByteBuffer.allocate(sourceView.size(name))
            val destinationBuffer = ByteBuffer.allocate(destinationView.size(name))
            sourceView.read(name, sourceBuffer)
            destinationView.read(name, destinationBuffer)
            if (!sourceBuffer.array().contentEquals(destinationBuffer.array())) {
                throw IOException("Extended attribute $name tidak dapat dipertahankan")
            }
        }
    }

    private fun rejectHardLinks(path: Path) {
        val links = (Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS) as Number).toLong()
        if (links != 1L) throw IOException("File memiliki hard link; penyimpanan atomik akan mengubah strukturnya")
    }

    private fun verifyUnchanged(path: Path, document: WorkspaceTextDocument) {
        val current = attributes(path)
        rejectHardLinks(path)
        if (current.fileKey() != document.fileKey || !readLimited(path).contentEquals(document.originalBytes)) {
            throw IOException("File telah berubah sejak dibuka; buka ulang sebelum menyimpan")
        }
    }

    private fun readLimited(path: Path): ByteArray {
        val output = ByteArrayOutputStream()
        Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_FILE_SIZE) throw IOException("File melebihi batas editor 1 MiB")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun requireText(text: String) {
        if (text.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' || it == '\u007f' }) {
            throw IOException("File berisi karakter kontrol atau data biner yang tidak didukung")
        }
    }
}
