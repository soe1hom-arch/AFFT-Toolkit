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
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Bahasa antarmuka aplikasi. */
enum class AppLanguage(
    val id: String,
    val displayName: String,
    val nativeName: String,
) {
    ENGLISH("en", "English", "English"),
    INDONESIAN("id", "Bahasa Indonesia", "Bahasa Indonesia"),
    CHINESE("zh", "Chinese (Simplified)", "简体中文");

    val isEnglish: Boolean
        get() = this == ENGLISH

    val isChinese: Boolean
        get() = this == CHINESE

    companion object {
        fun fromId(id: String?): AppLanguage? = entries.firstOrNull { it.id == id }

        /** Bahasa awal bila pengguna belum pernah memilih. */
        fun defaultFor(locale: Locale): AppLanguage =
            when (locale.language) {
                "zh" -> CHINESE
                "id", "in" -> INDONESIAN
                else -> ENGLISH
            }
    }
}

/**
 * Pengatur bahasa aplikasi.
 *
 * Tersimpan di [SharedPreferences]; bila belum ada pilihan, bahasa mengikuti
 * locale perangkat (Mandarin untuk perangkat berbahasa Tiongkok). UI membaca
 * [language] atau [current] sehingga perubahan langsung berlaku tanpa restart.
 */
object LanguageController {
    private const val PREFS_NAME = "afft_settings_prefs"
    private const val KEY_LANGUAGE = "app_language"

    private lateinit var prefs: SharedPreferences

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language

    /** State snapshot agar penerjemah teks ikut memicu recomposition. */
    private val currentState = mutableStateOf(AppLanguage.ENGLISH)

    /** Bahasa aktif; aman dibaca di dalam maupun di luar komposisi Compose. */
    val current: AppLanguage
        get() = currentState.value

    /** Panggil sekali saat aplikasi dimulai (AFFTApplication.onCreate). */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = AppLanguage.fromId(prefs.getString(KEY_LANGUAGE, null))
        val resolved = stored ?: AppLanguage.defaultFor(Locale.getDefault())
        if (stored == null) {
            prefs.edit().putString(KEY_LANGUAGE, resolved.id).apply()
        }
        currentState.value = resolved
        _language.value = resolved
    }

    fun setLanguage(value: AppLanguage) {
        currentState.value = value
        _language.value = value
        prefs.edit().putString(KEY_LANGUAGE, value.id).apply()
    }
}
