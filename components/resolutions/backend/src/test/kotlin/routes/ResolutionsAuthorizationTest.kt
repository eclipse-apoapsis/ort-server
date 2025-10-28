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

package org.eclipse.apoapsis.ortserver.components.resolutions.routes

import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.resolutions.PatchVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.PostVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.components.resolutions.VulnerabilityResolutionDefinitionService
import org.eclipse.apoapsis.ortserver.components.resolutions.resolutionsRoutes
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel
import org.eclipse.apoapsis.ortserver.shared.apimodel.VulnerabilityResolutionReason
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import org.jetbrains.exposed.sql.Database

class ResolutionsAuthorizationTest : AbstractAuthorizationTest({
    var repositoryId = 0L
    var runId = 0L

    val nonExistentRunId = 999L

    lateinit var createBody: PostVulnerabilityResolution

    lateinit var ortRunService: OrtRunService
    lateinit var definitionService: VulnerabilityResolutionDefinitionService

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures

        repositoryId = fixtures.repository.id
        runId = fixtures.ortRun.id

        authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()

        createBody = PostVulnerabilityResolution(
            runId,
            listOf("CVE-2020-15250"),
            VulnerabilityResolutionReason.INEFFECTIVE_VULNERABILITY,
            "Comment."
        )

        ortRunService = OrtRunService(
            db,
            fixtures.advisorJobRepository,
            fixtures.advisorRunRepository,
            fixtures.analyzerJobRepository,
            fixtures.analyzerRunRepository,
            fixtures.evaluatorJobRepository,
            fixtures.evaluatorRunRepository,
            fixtures.ortRunRepository,
            fixtures.reporterJobRepository,
            fixtures.reporterRunRepository,
            fixtures.notifierJobRepository,
            fixtures.notifierRunRepository,
            fixtures.repositoryConfigurationRepository,
            fixtures.repositoryRepository,
            fixtures.resolvedConfigurationRepository,
            fixtures.scannerJobRepository,
            fixtures.scannerRunRepository,
            mockk(),
            mockk()
        )

        definitionService = VulnerabilityResolutionDefinitionService(db, ortRunService)
    }

    "PostVulnerabilityResolution" should {
        "require role RepositoryPermission.WRITE.roleName(repositoryId)" {
            requestShouldRequireRole(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                role = RepositoryPermission.WRITE.roleName(repositoryId),
                successStatus = HttpStatusCode.Created
            ) {
                post("/resolutions/vulnerabilities") {
                    setBody(createBody)
                }
            }
        }

        "respond with 'Forbidden' when repository ID cannot be resolved" {
            requestShouldRequireAuthentication(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                successStatus = HttpStatusCode.Forbidden
            ) {
                post("/resolutions/vulnerabilities") {
                    setBody(createBody.copy(contextRunId = nonExistentRunId))
                }
            }
        }
    }

    "DeleteVulnerabilityResolution" should {
        "require role RepositoryPermission.WRITE.roleName(repositoryId)" {
            val definitionId = definitionService.create(
                RepositoryId(repositoryId),
                runId,
                UserDisplayName("abc", "Test"),
                createBody.idMatchers,
                createBody.reason.mapToModel(),
                createBody.comment
            ).id

            requestShouldRequireRole(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                role = RepositoryPermission.WRITE.roleName(repositoryId)
            ) {
                delete("/resolutions/vulnerabilities/$definitionId")
            }
        }

        "respond with 'Forbidden' when repository id cannot be resolved" {
            requestShouldRequireAuthentication(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                successStatus = HttpStatusCode.Forbidden
            ) {
                delete("/resolutions/vulnerabilities/9999")
            }
        }
    }

    "RestoreVulnerabilityResolution" should {
        "require role RepositoryPermission.WRITE.roleName(repositoryId)" {
            val userDisplayName = UserDisplayName("abc", "Test")

            val definitionId = definitionService.create(
                RepositoryId(repositoryId),
                runId,
                userDisplayName,
                createBody.idMatchers,
                createBody.reason.mapToModel(),
                createBody.comment
            ).id

            definitionService.archive(definitionId, userDisplayName)

            requestShouldRequireRole(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                role = RepositoryPermission.WRITE.roleName(repositoryId)
            ) {
                post("/resolutions/vulnerabilities/$definitionId/restore")
            }
        }

        "respond with 'Forbidden' when repository ID cannot be resolved" {
            requestShouldRequireAuthentication(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                successStatus = HttpStatusCode.Forbidden
            ) {
                post("/resolutions/vulnerabilities/9999/restore")
            }
        }
    }

    "PatchVulnerabilityResolution" should {
        "require role RepositoryPermission.WRITE.roleName(repositoryId)" {
            val definitionId = definitionService.create(
                RepositoryId(repositoryId),
                runId,
                UserDisplayName("abc", "Test"),
                createBody.idMatchers,
                createBody.reason.mapToModel(),
                createBody.comment
            ).id

            requestShouldRequireRole(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                role = RepositoryPermission.WRITE.roleName(repositoryId)
            ) {
                patch("/resolutions/vulnerabilities/$definitionId") {
                    setBody(
                        PatchVulnerabilityResolution(
                            comment = "Updated comment.".asPresent()
                        )
                    )
                }
            }
        }

        "respond with 'Forbidden' when repository ID cannot be resolved" {
            requestShouldRequireAuthentication(
                routes = { resolutionsRoutes(ortRunService, definitionService) },
                successStatus = HttpStatusCode.Forbidden
            ) {
                patch("/resolutions/vulnerabilities/9999") {
                    setBody(
                        PatchVulnerabilityResolution(
                            comment = "Updated comment.".asPresent()
                        )
                    )
                }
            }
        }
    }
})
