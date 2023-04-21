/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

import org.ossreviewtoolkit.server.core.api.AuthenticationException
import org.ossreviewtoolkit.server.core.api.AuthorizationException
import org.ossreviewtoolkit.server.core.api.ErrorResponse
import org.ossreviewtoolkit.server.dao.QueryParametersException
import org.ossreviewtoolkit.server.dao.UniqueConstraintException

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthenticationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<EntityNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<UniqueConstraintException> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("The entity you tried to create already exists.", e.message)
            )
        }
        exception<QueryParametersException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Invalid query parameters.", e.message)
            )
        }

        // catch all handler
        exception<Throwable> { call, e ->
            logger.error("Internal Server Error", e)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("Error when processing the request.", e.message)
            )
        }
    }
}
