/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.compositions.secretsroutes.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.routes.delete
import org.eclipse.apoapsis.ortserver.components.authorization.routes.requirePermission
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter
import org.eclipse.apoapsis.ortserver.shared.ktorutils.respondError

internal fun Route.deleteProductSecret(
    infrastructureServiceService: InfrastructureServiceService,
    secretService: SecretService
) = delete("/products/{productId}/secrets/{secretName}", {
    operationId = "deleteProductSecret"
    summary = "Delete a secret from a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}, requirePermission(ProductPermission.WRITE_SECRETS)) {
    val productId = ProductId(call.requireIdParameter("productId"))
    val secretName = call.requireParameter("secretName")

    val secret = secretService.getSecret(productId, secretName)

    if (secret == null) {
        call.respond(HttpStatusCode.NotFound)
        return@delete
    }

    val infrastructureServices = infrastructureServiceService.listForSecret(secret.name)
    if (infrastructureServices.isNotEmpty()) {
        call.respondError(
            HttpStatusCode.Conflict,
            "The secret is still in use.",
            "The secret '$secretName' is still referenced by the following infrastructure services: " +
                    infrastructureServices.joinToString { it.name }
        )
        return@delete
    }

    secretService.deleteSecret(productId, secretName)

    call.respond(HttpStatusCode.NoContent)
}
