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

package com.afft.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pengatur preferensi tema aplikasi.
 *
 * Nilai tersimpan di [SharedPreferences] agar bertahan setelah app
 * di-restart / device di-reboot. [StateFlow] dipakai agar UI (MainActivity
 * & dialog pengaturan) langsung bereaksi tanpa reload.
 */
object ThemeController {
    private const val PREFS_NAME = "afft_theme_prefs"
    private const val KEY_PRESET = "theme_preset"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_DYNAMIC = "theme_dynamic"
    private const val COLOR_UNSET = Int.MIN_VALUE
    private const val KEY_ACCENT = "custom_accent"
    private const val KEY_ICON_TINT = "custom_icon_tint"

    private lateinit var prefs: SharedPreferences

    private val _preset = MutableStateFlow(ThemePreset.AFFT_GREEN)
    val preset: StateFlow<ThemePreset> = _preset

    private val _mode = MutableStateFlow(ThemeMode.DARK)
    val mode: StateFlow<ThemeMode> = _mode

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _accent = MutableStateFlow<Color?>(null)
    val accent: StateFlow<Color?> = _accent

    private val _iconTint = MutableStateFlow<Color?>(null)
    val iconTint: StateFlow<Color?> = _iconTint

    /** Panggil sekali saat aplikasi dimulai (AFFTApplication.onCreate). */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _preset.value =
            ThemePreset.fromId(prefs.getString(KEY_PRESET, null)) ?: ThemePreset.AFFT_GREEN
        _mode.value = ThemeMode.fromId(prefs.getString(KEY_MODE, null)) ?: ThemeMode.DARK
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC, false)
        _accent.value = prefs.getInt(KEY_ACCENT, COLOR_UNSET).takeIf { it != COLOR_UNSET }?.let { Color(it) }
        _iconTint.value =
            prefs.getInt(KEY_ICON_TINT, COLOR_UNSET).takeIf { it != COLOR_UNSET }?.let { Color(it) }
    }

    fun setPreset(value: ThemePreset) {
        _preset.value = value
        prefs.edit().putString(KEY_PRESET, value.id).apply()
    }

    fun setMode(value: ThemeMode) {
        _mode.value = value
        prefs.edit().putString(KEY_MODE, value.id).apply()
    }

    fun setDynamicColor(value: Boolean) {
        _dynamicColor.value = value
        prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()
    }

    /** Set accent custom; null akan kembali ke accent preset/tema. */
    fun setAccent(value: Color?) {
        _accent.value = value
        prefs.edit().apply {
            if (value == null) {
                remove(KEY_ACCENT)
            } else {
                putInt(KEY_ACCENT, value.toArgb())
            }
        }.apply()
    }

    /** Set custom icon tint; null kembali ke warna default (abu tema). */
    fun setIconTint(value: Color?) {
        _iconTint.value = value
        prefs.edit().apply {
            if (value == null) {
                remove(KEY_ICON_TINT)
            } else {
                putInt(KEY_ICON_TINT, value.toArgb())
            }
        }.apply()
    }
}
