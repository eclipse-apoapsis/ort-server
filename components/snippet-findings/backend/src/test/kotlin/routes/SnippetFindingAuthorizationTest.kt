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

package org.eclipse.apoapsis.ortserver.components.snippetfindings.routes

import io.ktor.client.request.get

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.snippetfindings.SnippetFindingService
import org.eclipse.apoapsis.ortserver.components.snippetfindings.seedData
import org.eclipse.apoapsis.ortserver.components.snippetfindings.snippetFindingRoutes
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class SnippetFindingAuthorizationTest : AbstractAuthorizationTest({
    var ortRunId = -1L
    var provenanceId = -1L
    var snippetFindingId = -1L
    lateinit var service: SnippetFindingService
    lateinit var hierarchyId: CompoundHierarchyId
    lateinit var ortRunRepository: OrtRunRepository

    beforeEach {
        val seeded = seedData(dbExtension.fixtures, dbExtension.db)

        ortRunId = seeded.ortRunId
        provenanceId = seeded.provenanceId
        snippetFindingId = seeded.firstFindingId
        service = SnippetFindingService(dbExtension.db)
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        hierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(dbExtension.fixtures.organization.id),
            ProductId(dbExtension.fixtures.product.id),
            RepositoryId(dbExtension.fixtures.repository.id)
        )
    }

    "GetRunSnippetFindingProvenances" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            requestShouldRequireRole(
                routes = {
                    snippetFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/snippet-findings/provenances")
            }
        }
    }

    "GetRunProvenanceSnippetFindings" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            requestShouldRequireRole(
                routes = {
                    snippetFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/snippet-findings/provenances/$provenanceId/findings")
            }
        }
    }

    "GetRunSnippetFindingSnippets" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            requestShouldRequireRole(
                routes = {
                    snippetFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/snippet-findings/$snippetFindingId/snippets")
            }
        }
    }
})
