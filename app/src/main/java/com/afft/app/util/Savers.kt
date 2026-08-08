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

import androidx.compose.runtime.saveable.Saver
import java.io.File

/**
 * Custom Savers untuk tipe yang tidak bisa disimpan otomatis oleh rememberSaveable
 * (File dan koleksinya). Path absolut file tetap valid setelah process death
 * selama file-nya masih ada di disk.
 */
object FileSavers {
    val file: Saver<File, String> =
        Saver(
            save = { file: File -> file.absolutePath },
            restore = { path: String -> File(path) },
        )

    val fileList: Saver<List<File>, List<String>> =
        Saver(
            save = { files: List<File> -> files.map { it.absolutePath } },
            restore = { paths: List<String> -> paths.map { path: String -> File(path) } },
        )

    val fileSet: Saver<Set<File>, List<String>> =
        Saver(
            save = { files: Set<File> -> files.map { it.absolutePath } },
            restore = { paths: List<String> -> paths.map { path: String -> File(path) }.toSet() },
        )
}

/** Simpan Map<String, Boolean> sebagai daftar "key=value". */
val booleanMapSaver: Saver<Map<String, Boolean>, List<String>> =
    Saver(
        save = { map: Map<String, Boolean> -> map.entries.map { "${it.key}=${it.value}" } },
        restore = { entries: List<String> ->
            entries
                .mapNotNull { entry ->
                    entry
                        .split("=", limit = 2)
                        .let { parts -> if (parts.size == 2) parts[0] to parts[1].toBoolean() else null }
                }.toMap()
        },
    )

/** Simpan Set<String> sebagai list. */
val stringSetSaver: Saver<Set<String>, List<String>> =
    Saver(
        save = { set: Set<String> -> set.toList() },
        restore = { list: List<String> -> list.toSet() },
    )

/** Simpan List<String> (default saver tidak menangani List langsung). */
val stringListSaver: Saver<List<String>, List<String>> =
    Saver(
        save = { list: List<String> -> list },
        restore = { list: List<String> -> list },
    )

/** Simpan daftar partisi sebagai "name:size". */
val partitionListSaver: Saver<List<Pair<String, Long>>, List<String>> =
    Saver(
        save = { partitions: List<Pair<String, Long>> -> partitions.map { "${it.first}:${it.second}" } },
        restore = { entries: List<String> ->
            entries.mapNotNull { entry ->
                entry
                    .split(":", limit = 2)
                    .let { parts -> if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null }
            }
        },
    )
