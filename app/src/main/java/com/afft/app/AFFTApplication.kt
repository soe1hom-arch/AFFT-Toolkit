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

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.afft.app.ui.theme.FontController
import com.afft.app.ui.theme.LanguageController
import com.afft.app.ui.theme.ThemeController

class AFFTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Buat notification channel sejak awal agar service tidak delay saat startForeground
        createNotificationChannel()
        // Foreground service TIDAK di-start di sini lagi: service hanya berjalan
        // saat operasi ekstraksi aktif (dikelola AFFTService.ensureForeground*),
        // untuk menghemat baterai dan tidak menahan WakeLock seumur hidup aplikasi.
        // Muat preferensi tema (preset, mode, dynamic color) dari SharedPreferences.
        ThemeController.init(this)
        LanguageController.init(this)
        FontController.init(this)
        Log.d("AFFT", "AFFT Application initialized")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    "afft_extract",
                    "AFFT Extraction",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Menjaga proses ekstraksi tetap berjalan"
                    setShowBadge(false)
                }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("AFFT", "Notification channel created")
        }
    }

    companion object {
        lateinit var instance: AFFTApplication
            private set
    }
}
