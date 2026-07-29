/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.licensefindings.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingService
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.getRunDetectedLicensesForIdentifier(
    service: LicenseFindingService,
    ortRunRepository: OrtRunRepository
): Route =
    get("runs/{runId}/detected-licenses/identifiers/{identifier}", {
        operationId = "getRunDetectedLicensesForIdentifier"
        summary = "Get detected licenses for an identifier in an ORT run"
        tags = listOf("Runs")

        request {
            pathParameter<Long>("runId") {
                description = "The ID of the ORT run."
            }
            pathParameter<String>("identifier") {
                description = "The URL-encoded ORT identifier (e.g. Maven%3Acom.example%3Alib%3A1.0)."
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "Success."
                jsonBody<List<String>> {
                    example("Get detected licenses for an identifier") {
                        value = listOf("Apache-2.0", "MIT")
                    }
                }
            }
        }
    }, requireRunReadPermission(ortRunRepository)) {
        val runId = call.requireIdParameter("runId")

        ortRunRepository.get(runId) ?: return@get call.respond(HttpStatusCode.NotFound)

        val licenses = service.getDetectedLicensesForIdentifier(
            ortRunId = runId,
            identifier = call.parameters["identifier"].orEmpty()
        )

        call.respond(HttpStatusCode.OK, licenses)
    }
