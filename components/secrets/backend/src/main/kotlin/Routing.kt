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

package org.eclipse.apoapsis.ortserver.components.secrets

import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.deleteSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.getSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.getSecretsByOrganizationId
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.patchSecretByOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.postSecretForOrganization
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.deleteSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.getSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.getSecretsByProductId
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.patchSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.postSecretForProduct
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.deleteSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getAvailableSecretsByRepositoryId
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getSecretsByRepositoryId
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.patchSecretByRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.postSecretForRepository
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.SecretService

fun Route.secretsRoutes(repositoryService: RepositoryService, secretService: SecretService) {
    // Organization secrets
    deleteSecretByOrganizationIdAndName(secretService)
    getSecretByOrganizationIdAndName(secretService)
    getSecretsByOrganizationId(secretService)
    patchSecretByOrganizationIdAndName(secretService)
    postSecretForOrganization(secretService)

    // Product secrets
    deleteSecretByProductIdAndName(secretService)
    getSecretByProductIdAndName(secretService)
    getSecretsByProductId(secretService)
    patchSecretByProductIdAndName(secretService)
    postSecretForProduct(secretService)

    // Repository secrets
    deleteSecretByRepositoryIdAndName(secretService)
    getAvailableSecretsByRepositoryId(repositoryService, secretService)
    getSecretByRepositoryIdAndName(secretService)
    getSecretsByRepositoryId(secretService)
    patchSecretByRepositoryIdAndName(secretService)
    postSecretForRepository(secretService)
}
