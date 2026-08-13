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
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Memetakan URI tree dari SAF ([android.content.Intent.ACTION_OPEN_DOCUMENT_TREE])
 * ke path [File] nyata di penyimpanan internal.
 *
 * Mendukung provider ExternalStorageProvider dengan doc id "primary:...",
 * mis. "primary:Download/AFFT" → /storage/emulated/0/Download/AFFT.
 *
 * Mengembalikan null jika URI tidak bisa dipetakan (mis. SD card / USB dari
 * provider lain) — UI harus memberi tahu user untuk memakai folder di
 * penyimpanan internal atau Browser Folder bawaan.
 */
fun safTreeToFile(context: Context, uri: Uri): File? {
    if (uri.scheme != "content") return null
    val docId =
        try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        } ?: return null
    if (!docId.startsWith("primary:")) return null

    val relative = docId.removePrefix("primary:").trim('/')
    val file =
        if (relative.isEmpty()) {
            File("/storage/emulated/0")
        } else {
            File("/storage/emulated/0", relative)
        }
    return if (file.exists()) file else null
}
