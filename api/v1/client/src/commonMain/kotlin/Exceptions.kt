/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.eclipse.apoapsis.ortserver.client

import io.ktor.http.HttpStatusCode

/** Base exception class for all ORT server exceptions. */
sealed class OrtServerException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Exception thrown when a request is invalid. */
class BadRequestException(message: String, cause: Throwable? = null) : OrtServerException(message, cause)

/** Exception thrown when authentication fails. */
class UnauthorizedException(message: String, cause: Throwable? = null) : OrtServerException(message, cause)

/** Exception thrown when access to a resource is forbidden. */
class ForbiddenException(message: String, cause: Throwable? = null) : OrtServerException(message, cause)

/**
 *  Exception thrown when a requested resource is not found. Can be extended to provide more specific information which
 *  resource was not found.
 */
open class NotFoundException(message: String, cause: Throwable? = null) : OrtServerException(message, cause)

/** Exception thrown when a request cannot be processed due to a server error. */
class InternalServerException(message: String, cause: Throwable? = null) : OrtServerException(message, cause)

/** Generic exception for HTTP response errors. */
class ResponseException(message: String, val status: HttpStatusCode) : OrtServerException(message)

/** Exception thrown when a request fails due to a network error. */
class HttpRequestException(message: String) : OrtServerException(message)
