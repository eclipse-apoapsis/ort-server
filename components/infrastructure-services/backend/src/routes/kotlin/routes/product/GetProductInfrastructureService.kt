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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.product

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

internal fun Route.getProductInfrastructureService(
    infrastructureServiceService: InfrastructureServiceService
) = get("/products/{productId}/infrastructure-services/{serviceName}", {
    operationId = "getProductInfrastructureService"
    summary = "Get an infrastructure service for a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("serviceName") {
            description = "The name of the infrastructure service."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example("Get an infrastructure service") {
                    value = InfrastructureService(
                        name = "Artifactory",
                        url = "https://artifactory.example.org/releases",
                        description = "Artifactory repository",
                        usernameSecretRef = "artifactoryUsername",
                        passwordSecretRef = "artifactoryPassword"
                    )
                }
            }
        }
    }
}) {
    requirePermission(ProductPermission.READ)

    val productId = call.requireIdParameter("productId")
    val serviceName = call.requireParameter("serviceName")

    infrastructureServiceService
        .getForId(ProductId(productId), serviceName)
        ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
        ?: call.respond(HttpStatusCode.NotFound)
}
