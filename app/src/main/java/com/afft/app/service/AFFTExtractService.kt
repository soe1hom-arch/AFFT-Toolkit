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

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.afft.app.R

class AFFTExtractService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        // startForeground() HARUS dipanggil secepat mungkin, bahkan sebelum super.onCreate()
        // untuk menghindari ForegroundServiceDidNotStartInTimeException pada Android 14+
        // Notification channel sudah dibuat di Application.onCreate()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            android.util.Log.w("AFFTExtractService", "onCreate startForeground: ${e.message}")
        }
        super.onCreate()
        acquireWakeLock() // WakeLock di-release di onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Safety net: pastikan startForeground terpanggil (Android 14+ strict mode)
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            android.util.Log.w("AFFTExtractService", "onStartCommand startForeground: ${e.message}")
        }
        // Jangan restart otomatis oleh sistem: service hanya diperlukan saat
        // operasi ekstraksi berjalan dan di-stop eksplisit setelah selesai.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    // Notification channel sudah dibuat di Application.onCreate()
    private fun buildNotification(): Notification {
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
            }

        return builder
            .setContentTitle("AFFT")
            .setContentText("Sedang mengekstrak...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AFFT:ExtractWakeLock",
                )
            // Timeout 30 menit sebagai pengaman: jika ada proses yang menggantung,
            // WakeLock tidak menahan perangkat terjaga selamanya.
            wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
            android.util.Log.d("AFFTExtractService", "WakeLock acquired (${WAKELOCK_TIMEOUT_MS / 60000} min timeout)")
        } catch (e: Exception) {
            android.util.Log.e("AFFTExtractService", "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
            wakeLock = null
            android.util.Log.d("AFFTExtractService", "WakeLock released")
        } catch (e: Exception) {
            android.util.Log.e("AFFTExtractService", "Failed to release WakeLock: ${e.message}")
        }
    }

    companion object {
        const val CHANNEL_ID = "afft_extract"
        const val NOTIFICATION_ID = 1001
        private const val WAKELOCK_TIMEOUT_MS = 30 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, AFFTExtractService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AFFTExtractService::class.java))
        }
    }
}
