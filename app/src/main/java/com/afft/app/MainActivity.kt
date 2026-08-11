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

package com.afft.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.afft.app.service.AFFTService
import com.afft.app.ui.theme.FontController
import com.afft.app.ui.theme.ThemeController
import com.afft.app.ui.MainScreen
import com.afft.app.ui.theme.AFFTTheme
import com.afft.app.ui.theme.family
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var afftService: AFFTService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        afftService = AFFTService(this)

        // Request storage permissions
        requestStoragePermissions()
        // Request notification permission (Android 13+)
        requestNotificationPermission()

        setContent {
            AFFTTheme(
                        preset = ThemeController.preset.collectAsState().value,
                        mode = ThemeController.mode.collectAsState().value,
                        dynamicColor = ThemeController.dynamicColor.collectAsState().value,
                        accent = ThemeController.accent.collectAsState().value,
                        iconTint = ThemeController.iconTint.collectAsState().value,
                        fontFamily = FontController.font.collectAsState().value.family(),
                    ) {
                Surface(
                    modifier =
                        androidx.compose.ui.Modifier
                            .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Gradasi lembut (brand glow) di atas background agar latar
                    // terasa lebih dalam & premium, tanpa mengubah brand.
                    val primaryGlow = MaterialTheme.colorScheme.primary
                    val density = LocalDensity.current
                    BoxWithConstraints(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        val glowCenterX = maxWidth / 2f
                        val glowCenterY = maxHeight * 0.18f
                        val glowRadius = maxWidth * 1.15f
                        Box(
                            modifier =
                                androidx.compose.ui.Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    primaryGlow.copy(alpha = 0.055f),
                                                    Color.Transparent,
                                                ),
                                            center =
                                                Offset(
                                                    with(density) { glowCenterX.toPx() },
                                                    with(density) { glowCenterY.toPx() },
                                                ),
                                            radius = with(density) { glowRadius.toPx() },
                                        ),
                                    ),
                        )
                        MainScreen(afftService = afftService)
                    }
                    // Request POST_NOTIFICATIONS for Android 13+
                    val notificationLauncher =
                        rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            if (granted) {
                                android.util.Log.d("MainActivity", "POST_NOTIFICATIONS granted")
                            }
                        }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Will be requested via Compose launcher in MainScreen
                android.util.Log.d("MainActivity", "POST_NOTIFICATIONS not granted, will request via UI")
            }
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ),
                    1001,
                )
            }
        }
    }
}
