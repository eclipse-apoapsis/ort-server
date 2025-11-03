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
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationException
import org.eclipse.apoapsis.ortserver.components.authorization.service.InvalidHierarchyIdException
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InvalidSecretReferenceException
import org.eclipse.apoapsis.ortserver.core.api.AuthenticationException
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.services.OrganizationNotEmptyException
import org.eclipse.apoapsis.ortserver.services.ReportNotFoundException
import org.eclipse.apoapsis.ortserver.services.ResourceNotFoundException
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

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
            call.respondError(HttpStatusCode.BadRequest, "Invalid request body.", e.collectMessages())
        }
        exception<EntityNotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound)
        }
        exception<InvalidHierarchyIdException> { call, e ->
            call.respondError(HttpStatusCode.NotFound, e.message.orEmpty())
        }
        exception<InvalidSecretReferenceException> { call, e ->
            call.respondError(HttpStatusCode.BadRequest, "Secret reference could not be resolved.", e.message)
        }
        exception<MissingRequestParameterException> { call, e ->
            call.respondError(HttpStatusCode.BadRequest, "Missing request parameter.", e.message)
        }
        exception<OrganizationNotEmptyException> { call, e ->
            call.respondError(
                HttpStatusCode.Conflict,
                "Organization is not empty. Delete all products before deleting the organization.",
                e.message
            )
        }
        exception<ParameterConversionException> { call, e ->
            call.respondError(HttpStatusCode.BadRequest, "Parameter conversion failed.", e.message)
        }
        exception<ReportNotFoundException> { call, e ->
            call.respondError(HttpStatusCode.NotFound, "Report could not be resolved.", e.message)
        }
        exception<ResourceNotFoundException> { call, e ->
            call.respondError(HttpStatusCode.NotFound, "Resource not found.", e.message)
        }
        exception<RequestValidationException> { call, e ->
            call.respondError(HttpStatusCode.BadRequest, "Request validation has failed.", e.message)
        }
        exception<UniqueConstraintException> { call, e ->
            call.respondError(HttpStatusCode.Conflict, "The entity you tried to create already exists.", e.message)
        }
        exception<QueryParametersException> { call, e ->
            call.respondError(HttpStatusCode.BadRequest, "Invalid query parameters.", e.message)
        }

        // catch all handler
        exception<Throwable> { call, e ->
            logger.error("Internal Server Error", e)
            call.respondError(
                HttpStatusCode.InternalServerError,
                "Error when processing the request.",
                e.collectMessages()
            )
        }
    }
}
