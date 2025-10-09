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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices

import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.deleteOrganizationInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.getOrganizationInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.getOrganizationInfrastructureServices
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.patchOrganizationInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.postOrganizationInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.deleteRepositoryInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.getRepositoryInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.getRepositoryInfrastructureServices
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.patchRepositoryInfrastructureService
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.postRepositoryInfrastructureService

fun Route.infrastructureServicesRoutes(infrastructureServiceService: InfrastructureServiceService) {
    // Organization infrastructure services
    deleteOrganizationInfrastructureService(infrastructureServiceService)
    getOrganizationInfrastructureService(infrastructureServiceService)
    getOrganizationInfrastructureServices(infrastructureServiceService)
    patchOrganizationInfrastructureService(infrastructureServiceService)
    postOrganizationInfrastructureService(infrastructureServiceService)

    // Repository infrastructure services
    deleteRepositoryInfrastructureService(infrastructureServiceService)
    getRepositoryInfrastructureService(infrastructureServiceService)
    getRepositoryInfrastructureServices(infrastructureServiceService)
    patchRepositoryInfrastructureService(infrastructureServiceService)
    postRepositoryInfrastructureService(infrastructureServiceService)
}
