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

package org.eclipse.apoapsis.ortserver.components.licensefindings.routes

import io.ktor.client.request.get

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.licensefindings.LicenseFindingService
import org.eclipse.apoapsis.ortserver.components.licensefindings.licenseFindingRoutes
import org.eclipse.apoapsis.ortserver.components.licensefindings.seedData
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class LicenseFindingAuthorizationTest : AbstractAuthorizationTest({
    var ortRunId = -1L
    lateinit var service: LicenseFindingService
    lateinit var hierarchyId: CompoundHierarchyId
    lateinit var ortRunRepository: OrtRunRepository
    lateinit var artifactIdentifier: String

    beforeEach {
        val seeded = seedData(dbExtension.fixtures, dbExtension.db)

        ortRunId = seeded.ortRunId
        artifactIdentifier =
            "${seeded.artifactIdentifier.type}:${seeded.artifactIdentifier.namespace}:" +
                    "${seeded.artifactIdentifier.name}:${seeded.artifactIdentifier.version}"
        service = LicenseFindingService(dbExtension.db)
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        hierarchyId = CompoundHierarchyId.forRepository(
            OrganizationId(dbExtension.fixtures.organization.id),
            ProductId(dbExtension.fixtures.product.id),
            RepositoryId(dbExtension.fixtures.repository.id)
        )
    }

    "GetRunDetectedLicenses" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            requestShouldRequireRole(
                routes = {
                    licenseFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/detected-licenses")
            }
        }
    }

    "GetRunPackagesWithDetectedLicense" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            requestShouldRequireRole(
                routes = {
                    licenseFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/detected-licenses/Apache-2.0/packages")
            }
        }
    }

    "GetRunDetectedLicenseFindings" should {
        "require RepositoryPermission.READ_ORT_RUNS" {
            val encodedIdentifier = URLEncoder.encode(artifactIdentifier, StandardCharsets.UTF_8)

            requestShouldRequireRole(
                routes = {
                    licenseFindingRoutes(service, ortRunRepository)
                },
                role = RepositoryRole.READER,
                hierarchyId = hierarchyId
            ) {
                get("/runs/$ortRunId/detected-licenses/Apache-2.0/packages/$encodedIdentifier/findings")
            }
        }
    }
})
