/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

import org.eclipse.apoapsis.ortserver.core.api.AuthenticationException
import org.eclipse.apoapsis.ortserver.core.api.AuthorizationException
import org.eclipse.apoapsis.ortserver.core.api.ErrorResponse
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.services.InvalidSecretReferenceException
import org.eclipse.apoapsis.ortserver.services.OrganizationNotEmptyException
import org.eclipse.apoapsis.ortserver.services.ReferencedEntityException
import org.eclipse.apoapsis.ortserver.services.ReportNotFoundException
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException
import org.eclipse.apoapsis.ortserver.shared.ktorutils.UrlPathFormatException

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

import org.ossreviewtoolkit.utils.common.collectMessages

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
        exception<BadRequestException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Invalid request body.", e.collectMessages())
            )
        }
        exception<EntityNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<InvalidSecretReferenceException> { call, e ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(message = "Secret reference could not be resolved.", e.message)
            )
        }
        exception<OrganizationNotEmptyException> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    "Organization is not empty. Delete all products before deleting the organization.",
                    e.message
                )
            )
        }
        exception<ReferencedEntityException> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("The entity you tried to delete is in use.", e.message)
            )
        }
        exception<ReportNotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Report could not be resolved.", e.message))
        }
        exception<ResourceNotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Resource not found.", e.message))
        }
        exception<RequestValidationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Request validation has failed.", e.message))
        }
        exception<UniqueConstraintException> { call, e ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse("The entity you tried to create already exists.", e.message)
            )
        }
        exception<UrlPathFormatException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid URL path.", e.message))
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
                ErrorResponse("Error when processing the request.", e.collectMessages())
            )
        }
    }
}
