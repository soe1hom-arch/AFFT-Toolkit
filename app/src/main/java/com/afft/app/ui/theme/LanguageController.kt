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

/** Bahasa antarmuka aplikasi. */
enum class AppLanguage(
    val id: String,
    val displayName: String,
) {
    ENGLISH("en", "English"),
    INDONESIAN("id", "Bahasa Indonesia");

    val isEnglish: Boolean
        get() = this == ENGLISH

    companion object {
        fun fromId(id: String?): AppLanguage? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Pengatur bahasa aplikasi.
 *
 * Tersimpan di [SharedPreferences], default **English**. UI membaca
 * [StateFlow] sehingga perubahan langsung berlaku tanpa restart.
 */
object LanguageController {
    private const val PREFS_NAME = "afft_settings_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private lateinit var prefs: SharedPreferences

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language

    /** Panggil sekali saat aplikasi dimulai (AFFTApplication.onCreate). */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _language.value =
            AppLanguage.fromId(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.ENGLISH
    }

    fun setLanguage(value: AppLanguage) {
        _language.value = value
        prefs.edit().putString(KEY_LANGUAGE, value.id).apply()
    }
}
