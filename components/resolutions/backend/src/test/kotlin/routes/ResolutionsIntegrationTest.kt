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

import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionEventStore
import org.eclipse.apoapsis.ortserver.components.resolutions.vulnerabilities.VulnerabilityResolutionService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

@Suppress("AbstractClassCanBeConcreteClass")
abstract class ResolutionsIntegrationTest(body: ResolutionsIntegrationTest.() -> Unit) : AbstractIntegrationTest({}) {
    lateinit var vulnerabilityResolutionService: VulnerabilityResolutionService

    init {
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
        }

        body()
    }

    fun resolutionsTestApplication(
        block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit
    ) = integrationTestApplication(
        routes = { resolutionRoutes(vulnerabilityResolutionService) },
        block = block
    )
}
