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

package org.eclipse.apoapsis.ortserver.components.dependencygraph.routes

import io.kotest.assertions.ktor.client.shouldHaveStatus

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.dependencygraph.backend.DependencyGraphService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

class DependencyGraphAuthorizationTest : AbstractAuthorizationTest({
    lateinit var service: DependencyGraphService
    lateinit var checker: AuthorizationChecker
    lateinit var hierarchyId: CompoundHierarchyId
    var ortRunId = -1L

    beforeEach {
        service = DependencyGraphService(dbExtension.db)
        checker = requireRunPermission(dbExtension.fixtures.ortRunRepository)
        ortRunId = dbExtension.fixtures.ortRun.id

        hierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(dbExtension.fixtures.organization.id),
            ProductId(dbExtension.fixtures.product.id),
            RepositoryId(dbExtension.fixtures.repository.id)
        )
    }

    "GetDependencyGraph" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            authorizationTestApplication(
                routes = {
                    route("runs/{runId}") {
                        dependencyGraphRoutes(service, checker)
                    }
                }
            ) { unauthenticatedClient, testUserClient ->
                unauthenticatedClient.get("/runs/$ortRunId/dependency-graph") shouldHaveStatus
                        HttpStatusCode.Unauthorized
                testUserClient.get("/runs/$ortRunId/dependency-graph") shouldHaveStatus HttpStatusCode.Forbidden
            }
        }

        "require the reader role on the run's repository" {
            requestShouldRequireRole(
                routes = {
                    route("runs/{runId}") {
                        dependencyGraphRoutes(service, checker)
                    }
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId,
                successStatus = HttpStatusCode.OK
            ) {
                get("/runs/$ortRunId/dependency-graph")
            }
        }
    }
})

private fun requireRunPermission(
    ortRunRepository: OrtRunRepository,
    permission: RepositoryPermission = RepositoryPermission.READ_ORT_RUNS
): AuthorizationChecker =
    object : AuthorizationChecker {
        override suspend fun loadEffectiveRole(
            service: AuthorizationService,
            userId: String,
            call: ApplicationCall
        ): EffectiveRole? {
            val runId = call.requireIdParameter("runId")
            val ortRun = ortRunRepository.get(runId) ?: return null

            return service.checkPermissions(
                userId,
                RepositoryId(ortRun.repositoryId),
                HierarchyPermissions.permissions(permission)
            )
        }
    }
