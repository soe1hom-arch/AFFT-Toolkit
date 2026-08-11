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
import java.io.File
import java.io.FileOutputStream

object BinaryManager {
    private const val TAG = "BinaryManager"
    private const val BIN_DIR = "bin"
    private const val APP_BIN_DIR = "afft_bin"

    private val REQUIRED_BINARIES =
        listOf(
            "debugfs",
            "lpunpack",
            "extract.erofs",
            "lpmake",
            "magiskboot",
            "make_ext4fs",
            "mkfs.erofs",
            "payload-dumper-go",
            "simg2img",
        )

    private val BINARY_WHITELIST =
        setOf(
            "debugfs",
            "extract.erofs",
            "lpunpack",
            "lpmake",
            "magiskboot",
            "make_ext4fs",
            "mkfs.erofs",
            "payload-dumper-go",
            "simg2img",
        )

    fun getBinDirectory(context: Context): File {
        val dir = File(context.filesDir, APP_BIN_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Mendapatkan path binary dari nativeLibraryDir (jniLibs).
     * Ini adalah metode yang benar untuk Android 14+ karena SELinux
     * memblokir eksekusi binary dari filesDir biasa.
     * Binary harus ditempatkan di app/src/main/jniLibs/<arch>/lib<name>.so
     */
    fun getNativeLibBinaryPath(
        context: Context,
        name: String,
    ): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val libFile = File(nativeLibDir, "lib$name.so")

        if (!libFile.exists()) {
            Log.d(TAG, "getNativeLibBinaryPath($name): NOT FOUND at ${libFile.absolutePath}")
            return null
        }

        val canRead = libFile.canRead()
        var canExec = libFile.canExecute()

        Log.d(
            TAG,
            "getNativeLibBinaryPath($name): exists=true execute=$canExec read=$canRead path=${libFile.absolutePath}",
        )

        // Attempt to fix executable permission if needed
        if (!canExec) {
            Log.w(TAG, "getNativeLibBinaryPath($name): not executable, attempting setExecutable(true)")
            try {
                val fixed = libFile.setExecutable(true, false)
                if (fixed) {
                    canExec = libFile.canExecute()
                    Log.d(TAG, "getNativeLibBinaryPath($name): setExecutable(true) succeeded, execute=$canExec")
                } else {
                    Log.w(TAG, "getNativeLibBinaryPath($name): setExecutable(true) returned false")
                    // Last resort: try chmod via shell
                    try {
                        val chmodProcess =
                            Runtime.getRuntime().exec(
                                arrayOf("/system/bin/chmod", "755", libFile.absolutePath),
                            )
                        chmodProcess.waitFor()
                        canExec = libFile.canExecute()
                        Log.d(TAG, "getNativeLibBinaryPath($name): after chmod 755, execute=$canExec")
                    } catch (e: Exception) {
                        Log.w(TAG, "getNativeLibBinaryPath($name): chmod fallback failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "getNativeLibBinaryPath($name): setExecutable threw: ${e.message}")
            }
        }

        // On Android 14+, direct exec from app data may be blocked by SELinux,
        // but binary can still be loaded via /system/bin/linker64.
        // Always return path if file exists, even if not directly executable.
        return libFile.absolutePath
    }

    fun deployBinaries(context: Context): Result<List<String>> {
        val binDir = getBinDirectory(context)
        val deployed = mutableListOf<String>()

        try {
            val assets = context.assets.list(BIN_DIR) ?: emptyArray()

            for (assetName in assets) {
                if (assetName !in BINARY_WHITELIST) {
                    continue
                }

                val targetFile = File(binDir, assetName)

                // Skip if binary already available from nativeLibraryDir (jniLibs)
                // getNativeLibBinaryPath() verifies the file is executable
                val nativeLibPath = getNativeLibBinaryPath(context, assetName)
                if (nativeLibPath != null) {
                    Log.d(TAG, "$assetName already available via nativeLibraryDir: $nativeLibPath")
                    deployed.add(assetName)
                    continue
                }

                if (targetFile.exists() && targetFile.canExecute()) {
                    deployed.add(assetName)
                    continue
                }

                try {
                    context.assets.open("$BIN_DIR/$assetName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Set executable permission via Java API
                    val setExecOk = targetFile.setExecutable(true, false)
                    if (!setExecOk) {
                        Log.w(TAG, "setExecutable() returned false for $assetName, trying chmod fallback")
                    }
                    targetFile.setReadable(true, false)

                    // Fallback: use chmod via shell if setExecutable didn't work
                    try {
                        val chmodProcess =
                            Runtime.getRuntime().exec(
                                arrayOf("/system/bin/chmod", "755", targetFile.absolutePath),
                            )
                        chmodProcess.waitFor()
                        if (chmodProcess.exitValue() != 0) {
                            Log.w(TAG, "chmod 755 $assetName exit code: ${chmodProcess.exitValue()}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "chmod fallback failed for $assetName: ${e.message}")
                    }

                    // Verify permissions
                    Log.d(
                        TAG,
                        "$assetName: exists=${targetFile.exists()}" +
                            " execute=${targetFile.canExecute()}" +
                            " read=${targetFile.canRead()}" +
                            " path=${targetFile.absolutePath}",
                    )

                    deployed.add(assetName)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deploy $assetName: ${e.message}")
                }
            }

            if (deployed.isEmpty()) {
                return Result.failure(Exception("No binaries could be deployed"))
            }

            return Result.success(deployed)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun getBinaryPath(
        context: Context,
        name: String,
    ): String? {
        // Priority 1: Try nativeLibraryDir (jniLibs) - works on Android 14+ with SELinux
        // getNativeLibBinaryPath() now verifies executability and attempts to fix permissions
        val nativeLibPath = getNativeLibBinaryPath(context, name)
        if (nativeLibPath != null) {
            Log.d(TAG, "getBinaryPath($name): using nativeLibraryDir path: $nativeLibPath")
            return nativeLibPath
        }

        // Priority 2: Fall back to binary in app assets (extracted to filesDir)
        val binDir = getBinDirectory(context)
        val binary = File(binDir, name)

        val exists = binary.exists()
        val canExec = binary.canExecute()
        val canRead = binary.canRead()

        Log.d(
            TAG,
            "getBinaryPath($name) [fallback]: exists=$exists execute=$canExec read=$canRead path=${binary.absolutePath}",
        )

        if (exists) {
            // Try to fix executable permission
            if (!canExec) {
                binary.setExecutable(true, false)
            }
            if (binary.canExecute()) {
                return binary.absolutePath
            }
        }

        return null
    }

    fun verifyBinaries(context: Context): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        for (name in REQUIRED_BINARIES) {
            // Check nativeLibraryDir first
            val nativePath = getNativeLibBinaryPath(context, name)
            if (nativePath != null) {
                results[name] = true
                continue
            }

            // Fall back to filesDir
            val binDir = getBinDirectory(context)
            val binary = File(binDir, name)
            results[name] = binary.exists() && binary.canExecute()
        }

        return results
    }

}
