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

package com.afft.app.model

import android.net.Uri

data class OperationResult(
    val ok: Boolean,
    val title: String,
    val message: String,
    val outputPath: String = "",
)

data class FileItem(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
    val isDirectory: Boolean = false,
)

data class AppSettings(
    val debugMode: Boolean = false,
    val inputPath: String = "",
    val outputPath: String = "",
)

enum class OperationType {
    EXTRACT_PAYLOAD,
    UNPACK_SUPER,
    REPACK_SUPER,
    EXTRACT_FILESYSTEM,
    REPACK_FILESYSTEM,
    UNPACK_BOOT,
    REPACK_BOOT,
    CLEAN_OUTPUT,
    WIZARD,
}

data class OperationLog(
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val isError: Boolean = false,
    val isInfo: Boolean = false,
)

enum class BootImageType(
    val fileName: String,
    val displayName: String,
) {
    BOOT("boot.img", "Boot"),
    VENDOR_BOOT("vendor_boot.img", "Vendor Boot"),
    INIT_BOOT("init_boot.img", "Init Boot"),
    DTBO("dtbo.img", "DTBO"),
    RECOVERY("recovery.img", "Recovery"),
    VBMETA("vbmeta.img", "VBMeta"),
    VENDOR_KERNEL_BOOT("vendor_kernel_boot.img", "Vendor Kernel Boot"),
}
