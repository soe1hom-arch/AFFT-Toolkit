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

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafeDeleteTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun deleteTreeRemovesLinksButNotTheirTargets() {
        val target = tmp.newFolder("outside")
        val sentinel = File(target, "keep.txt").apply { writeText("keep") }
        val root = tmp.newFolder("tree")
        val nested = File(root, "nested").apply { mkdir() }
        File(nested, "ordinary.txt").writeText("delete")
        Files.createSymbolicLink(File(nested, "outside-link").toPath(), target.toPath())
        Files.createSymbolicLink(File(root, "broken-link").toPath(), File(tmp.root, "missing").toPath())

        deleteTreeNoFollow(root)

        assertFalse(root.exists())
        assertEquals("keep", sentinel.readText())
    }

    @Test
    fun deleteRootSymlinkDoesNotDeleteTarget() {
        val target = tmp.newFolder("target")
        val sentinel = File(target, "keep.txt").apply { writeText("keep") }
        val link = File(tmp.root, "link")
        Files.createSymbolicLink(link.toPath(), target.toPath())

        deleteTreeNoFollow(link)

        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(sentinel.isFile)
    }
}
