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

import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.deleteInfrastructureServiceForOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.getInfrastructureServiceForOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.getInfrastructureServicesByOrganizationId
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.patchInfrastructureServiceForOrganizationIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.organization.postInfrastructureServiceForOrganization
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.deleteInfrastructureServiceForRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.getInfrastructureServiceForRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.getInfrastructureServicesByRepositoryId
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.patchInfrastructureServiceForRepositoryIdAndName
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.routes.repository.postInfrastructureServiceForRepository
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService

fun Route.infrastructureServicesRoutes(infrastructureServiceService: InfrastructureServiceService) {
    // Organization infrastructure services
    deleteInfrastructureServiceForOrganizationIdAndName(infrastructureServiceService)
    getInfrastructureServiceForOrganizationIdAndName(infrastructureServiceService)
    getInfrastructureServicesByOrganizationId(infrastructureServiceService)
    patchInfrastructureServiceForOrganizationIdAndName(infrastructureServiceService)
    postInfrastructureServiceForOrganization(infrastructureServiceService)

    // Repository infrastructure services
    deleteInfrastructureServiceForRepositoryIdAndName(infrastructureServiceService)
    getInfrastructureServiceForRepositoryIdAndName(infrastructureServiceService)
    getInfrastructureServicesByRepositoryId(infrastructureServiceService)
    patchInfrastructureServiceForRepositoryIdAndName(infrastructureServiceService)
    postInfrastructureServiceForRepository(infrastructureServiceService)
}
