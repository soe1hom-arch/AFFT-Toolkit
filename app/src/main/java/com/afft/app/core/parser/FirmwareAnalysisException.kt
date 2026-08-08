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

/** Kategori error analisis terstruktur. */
enum class FirmwareAnalysisError {
    UNSUPPORTED_FORMAT,
    CORRUPTED_IMAGE,
    MISSING_METADATA,
    PERMISSION_DENIED,
    PARSER_FAILURE,
    UNKNOWN_FAILURE,
}

/**
 * Exception terstruktur untuk analisis firmware.
 * Engine memetakan seluruh kegagalan ke salah satu subtype ini.
 */
sealed class FirmwareAnalysisException(
    val error: FirmwareAnalysisError,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class UnsupportedFormat(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.UNSUPPORTED_FORMAT, message, cause)

    class CorruptedImage(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.CORRUPTED_IMAGE, message, cause)

    class MissingMetadata(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.MISSING_METADATA, message, cause)

    class PermissionDenied(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.PERMISSION_DENIED, message, cause)

    class ParserFailure(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.PARSER_FAILURE, message, cause)

    class UnknownFailure(message: String, cause: Throwable? = null) :
        FirmwareAnalysisException(FirmwareAnalysisError.UNKNOWN_FAILURE, message, cause)
}
