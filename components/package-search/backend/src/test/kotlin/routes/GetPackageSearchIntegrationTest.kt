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

package org.eclipse.apoapsis.ortserver.components.packagesearch.backend.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.packagesearch.backend.PackageSearchDataAccessImpl
import org.eclipse.apoapsis.ortserver.components.packagesearch.backend.PackageSearchService
import org.eclipse.apoapsis.ortserver.components.packagesearch.packageSearchRoutes
import org.eclipse.apoapsis.ortserver.dao.test.toCoordinates
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractIntegrationTest

class GetPackageSearchIntegrationTest : AbstractIntegrationTest({

	"GET /search/packages" should {
		"return 200 OK and the run for a valid package identifier" {
            val pkg1 = dbExtension.fixtures.generatePackage(Identifier("Maven", "com.example", "example", "1.0"))
            val pkg2 = dbExtension.fixtures.generatePackage(Identifier("Maven", "com.example", "example2", "1.0"))
            val ortRunId = dbExtension.fixtures.createAnalyzerRunWithPackages(setOf(pkg1, pkg2)).id
            val ortRunRepository = dbExtension.fixtures.ortRunRepository
            val identifier = pkg1.identifier
			val service = PackageSearchService(PackageSearchDataAccessImpl(ortRunRepository))
			integrationTestApplication(
				routes = { packageSearchRoutes(service) }
			) { client ->
				val response = client.get("/search/packages?identifier=${identifier.toCoordinates()}")
				response.status shouldBe HttpStatusCode.OK
				val body = response.bodyAsText()
				body shouldContain ortRunId.toString()
			}
		}
	}
})
