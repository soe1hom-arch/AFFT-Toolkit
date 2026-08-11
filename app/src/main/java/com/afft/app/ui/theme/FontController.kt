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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pengatur pilihan font aplikasi.
 *
 * Tersimpan di [SharedPreferences] agar bertahan setelah app di-restart.
 * [StateFlow] dipakai agar UI langsung bereaksi tanpa reload.
 */
object FontController {
    private const val PREFS_NAME = "afft_theme_prefs"
    private const val KEY_FONT = "app_font"

    private lateinit var prefs: SharedPreferences

    private val _font = MutableStateFlow(AppFont.INTER)
    val font: StateFlow<AppFont> = _font

    /** Panggil sekali saat aplikasi dimulai (AFFTApplication.onCreate). */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _font.value = AppFont.fromId(prefs.getString(KEY_FONT, null)) ?: AppFont.INTER
    }

    fun setFont(value: AppFont) {
        _font.value = value
        prefs.edit().putString(KEY_FONT, value.id).apply()
    }
}
