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

package org.eclipse.apoapsis.ortserver.components.resolutions.routes

import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.resolutions.CreateVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.UpdateVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.apimodel.VulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class ResolutionsAuthorizationTest : AbstractAuthorizationTest({
    lateinit var vulnerabilityResolutionService: VulnerabilityResolutionService
    lateinit var hierarchyId: CompoundHierarchyId
    var repositoryId = RepositoryId(-1)

    beforeEach {
        vulnerabilityResolutionService = VulnerabilityResolutionService(
            db = dbExtension.db,
            eventStore = VulnerabilityResolutionEventStore(dbExtension.db),
            repositoryService = RepositoryService(
                db = dbExtension.db,
                ortRunRepository = dbExtension.fixtures.ortRunRepository,
                repositoryRepository = dbExtension.fixtures.repositoryRepository,
                analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository,
                advisorJobRepository = dbExtension.fixtures.advisorJobRepository,
                scannerJobRepository = dbExtension.fixtures.scannerJobRepository,
                evaluatorJobRepository = dbExtension.fixtures.evaluatorJobRepository,
                reporterJobRepository = dbExtension.fixtures.reporterJobRepository,
                notifierJobRepository = dbExtension.fixtures.notifierJobRepository,
                authorizationService = mockk()
            )
        )

        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)

        hierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(dbExtension.fixtures.organization.id),
            ProductId(dbExtension.fixtures.product.id),
            repositoryId
        )
    }

    "CreateVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = { resolutionRoutes(vulnerabilityResolutionService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.Created,
                hierarchyId = hierarchyId
            ) {
                post("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234") {
                    setBody(
                        CreateVulnerabilityResolution(
                            comment = "This is not a vulnerability.",
                            reason = VulnerabilityResolutionReason.NOT_A_VULNERABILITY
                        )
                    )
                }
            }
        }
    }

    "DeleteVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = { resolutionRoutes(vulnerabilityResolutionService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                delete("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234")
            }
        }
    }

    "UpdateVulnerabilityResolution" should {
        "require RepositoryPermission.MANAGE_RESOLUTIONS" {
            requestShouldRequireRole(
                routes = { resolutionRoutes(vulnerabilityResolutionService) },
                role = RepositoryRole.WRITER,
                successStatus = HttpStatusCode.NotFound,
                hierarchyId = hierarchyId
            ) {
                patch("/repositories/${repositoryId.value}/resolutions/vulnerabilities/CVE-2021-1234") {
                    setBody(
                        UpdateVulnerabilityResolution(
                            comment = "This is not a vulnerability.",
                            reason = VulnerabilityResolutionReason.NOT_A_VULNERABILITY
                        )
                    )
                }
            }
        }
    }
})
