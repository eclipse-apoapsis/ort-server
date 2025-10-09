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

import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.getOrganizationSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.getOrganizationSecrets
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.patchOrganizationSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.organization.postOrganizationSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.getProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.getProductSecrets
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.patchProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.product.postProductSecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getAvailableRepositorySecrets
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getRepositorySecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.getRepositorySecrets
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.patchRepositorySecret
import org.eclipse.apoapsis.ortserver.components.secrets.routes.repository.postRepositorySecret
import org.eclipse.apoapsis.ortserver.services.RepositoryService

fun Route.secretsRoutes(repositoryService: RepositoryService, secretService: SecretService) {
    // Organization secrets
    getOrganizationSecret(secretService)
    getOrganizationSecrets(secretService)
    patchOrganizationSecret(secretService)
    postOrganizationSecret(secretService)

    // Product secrets
    getProductSecret(secretService)
    getProductSecrets(secretService)
    patchProductSecret(secretService)
    postProductSecret(secretService)

    // Repository secrets
    getAvailableRepositorySecrets(repositoryService, secretService)
    getRepositorySecret(secretService)
    getRepositorySecrets(secretService)
    patchRepositorySecret(secretService)
    postRepositorySecret(secretService)
}
