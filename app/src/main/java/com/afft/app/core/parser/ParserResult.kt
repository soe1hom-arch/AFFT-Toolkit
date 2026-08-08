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

package com.afft.app.core.parser

/**
 * Hasil parsing generik.
 * - [Success] membawa data + optional warnings.
 * - [Failure] membawa status + alasan terstruktur.
 */
sealed class ParserResult<out T> {
    data class Success<T>(
        val data: T,
        val warnings: List<String> = emptyList(),
    ) : ParserResult<T>()

    data class Failure(
        val status: ParserStatus,
        val reason: String,
    ) : ParserResult<Nothing>()
}

fun <T> ParserResult<T>.getOrNull(): T? =
    when (this) {
        is ParserResult.Success -> data
        is ParserResult.Failure -> null
    }
