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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class WorkspaceTextEditorTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun savesUtf8BomAndCrLfWithoutChangingPermissions() = runBlocking {
        val workspace = temporary.newFolder("filesystem")
        val file = File(workspace, "build.prop")
        val bom = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
        file.writeBytes(bom + "key=value\r\n".toByteArray(StandardCharsets.UTF_8))
        val permissions = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ)
        Files.setPosixFilePermissions(file.toPath(), permissions)
        val editor = WorkspaceTextEditor { workspace }
        val document = editor.open(file).getOrThrow()

        assertEquals("key=value\r\n", document.text)
        assertTrue(editor.save(document, "key=baru\r\n").isSuccess)
        assertArrayEquals(bom + "key=baru\r\n".toByteArray(StandardCharsets.UTF_8), file.readBytes())
        assertEquals(permissions, Files.getPosixFilePermissions(file.toPath()))
    }

    @Test
    fun externalModificationPreventsSaveAndPreservesCurrentFile() = runBlocking {
        val workspace = temporary.newFolder("filesystem")
        val file = File(workspace, "init.rc").apply { writeText("old\n") }
        val editor = WorkspaceTextEditor { workspace }
        val document = editor.open(file).getOrThrow()
        file.writeText("other change\n")

        assertTrue(editor.save(document, "my change\n").isFailure)
        assertEquals("other change\n", file.readText())
    }

    @Test
    fun invalidEditsDoNotChangeOriginalFile() = runBlocking {
        val workspace = temporary.newFolder("filesystem")
        val file = File(workspace, "config.xml").apply { writeText("<config/>\n") }
        val editor = WorkspaceTextEditor { workspace }
        val document = editor.open(file).getOrThrow()

        assertTrue(editor.save(document, "binary\u0000data").isFailure)
        assertTrue(editor.save(document, "x".repeat(WorkspaceTextEditor.MAX_FILE_SIZE + 1)).isFailure)
        assertEquals("<config/>\n", file.readText())
    }

    @Test
    fun rejectsBinaryInvalidUtf8OversizeAndPathsOutsideWorkspace() = runBlocking {
        val workspace = temporary.newFolder("filesystem")
        val editor = WorkspaceTextEditor { workspace }
        val binary = File(workspace, "binary").apply { writeBytes(byteArrayOf(65, 0, 66)) }
        val invalid = File(workspace, "invalid").apply { writeBytes(byteArrayOf(0xc3.toByte(), 0x28)) }
        val large =
            File(workspace, "large").apply { writeBytes(ByteArray(WorkspaceTextEditor.MAX_FILE_SIZE + 1) { 65 }) }
        val outside = File(temporary.root, "outside").apply { writeText("keep") }

        assertTrue(editor.open(binary).isFailure)
        assertTrue(editor.open(invalid).isFailure)
        assertTrue(editor.open(large).isFailure)
        assertTrue(editor.open(outside).isFailure)
        assertEquals("keep", outside.readText())
    }

    @Test
    fun rejectsSymlinksAndHardLinksWithoutChangingTheirTargets() = runBlocking {
        val workspace = temporary.newFolder("filesystem")
        val target = File(workspace, "target").apply { writeText("keep") }
        val link = File(workspace, "link")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        val directoryLink = File(workspace, "dirlink")
        Files.createSymbolicLink(directoryLink.toPath(), workspace.toPath())
        val hardLink = File(workspace, "hardlink")
        Files.createLink(hardLink.toPath(), target.toPath())
        val editor = WorkspaceTextEditor { workspace }

        assertTrue(editor.open(link).isFailure)
        assertTrue(editor.open(File(directoryLink, "target")).isFailure)
        assertTrue(editor.open(hardLink).isFailure)
        assertEquals("keep", target.readText())
    }
}
