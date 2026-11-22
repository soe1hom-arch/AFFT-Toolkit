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

package com.afft.app.ui.i18n

import com.afft.app.ui.theme.AppLanguage
import com.afft.app.ui.theme.LanguageController
import java.util.concurrent.ConcurrentHashMap

/** Awal karakter yang boleh muncul setelah awalan kalimat dinamis. */
private const val PREFIX_BOUNDARY = " :-(（："

/**
 * Kunci kamus yang dipakai untuk pencocokan awalan kalimat dinamis, misalnya
 * "Extract Payload: 42%". Diurutkan dari yang terpanjang agar awalan paling
 * spesifik menang.
 */
private val PREFIX_KEYS: List<String> by lazy {
    ZH_STRINGS.keys.filter { it.length >= 10 }.sortedByDescending { it.length }
}

/** Cache hasil terjemahan kalimat dinamis agar tidak dihitung ulang. */
private val DYNAMIC_CACHE = ConcurrentHashMap<String, String>()

/**
 * Menerjemahkan teks antarmuka ke bahasa yang sedang aktif.
 *
 * - English/Indonesia: teks dikembalikan apa adanya.
 * - Mandarin: kamus [ZH_STRINGS] dicari lebih dulu; bila tidak ada padanan
 *   persis, dicoba pencocokan awalan sehingga kalimat dinamis seperti
 *   "Gagal menyimpan: <pesan>" tetap diterjemahkan bagian depannya.
 *
 * Aman dipanggil di dalam maupun di luar komposisi Compose. Di dalam
 * komposisi, pembacaan bahasa aktif ikut terekam sehingga UI otomatis
 * digambar ulang saat bahasa diganti.
 */
fun tr(text: String): String {
    if (LanguageController.current != AppLanguage.CHINESE) return text
    ZH_STRINGS[text]?.let { return it }
    DYNAMIC_CACHE[text]?.let { return it }
    val translated = translateByPrefix(text) ?: text
    DYNAMIC_CACHE[text] = translated
    return translated
}

/**
 * Versi [tr] untuk teks berpola `String.format` (mis. "Exporting %d folder(s)").
 * Format tetap diterapkan pada bahasa apa pun agar tidak muncul placeholder.
 */
fun tr(
    template: String,
    vararg args: Any?,
): String {
    val text = tr(template)
    if (args.isEmpty()) return text
    return runCatching { String.format(text, *args) }.getOrDefault(text)
}

private fun translateByPrefix(text: String): String? {
    if (text.length <= 10) return null
    for (key in PREFIX_KEYS) {
        if (key.length >= text.length) continue
        if (!text.startsWith(key)) continue
        if (text[key.length] !in PREFIX_BOUNDARY) continue
        val translated = ZH_STRINGS[key] ?: continue
        return translated + text.substring(key.length)
    }
    return null
}
