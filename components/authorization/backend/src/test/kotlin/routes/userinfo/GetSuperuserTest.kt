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

package org.eclipse.apoapsis.ortserver.components.authorization.routes.userinfo

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.authorizationRoutes
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.DbAuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class GetSuperuserTest : AbstractAuthorizationTest({
    lateinit var authorizationService: AuthorizationService

    beforeEach {
        authorizationService = DbAuthorizationService(dbExtension.db)
    }

    "GET /authorization/superuser" should {
        "return 'true' for a superuser" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                authorizationService.assignRole(
                    TEST_USER,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.WILDCARD
                )

                val response = client.get("/authorization/superuser")
                response shouldHaveStatus HttpStatusCode.OK
                response.bodyAsText() shouldBe "true"
            }
        }

        "return 'false' for a normal user" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                val orgId = CompoundHierarchyId.forOrganization(OrganizationId(dbExtension.fixtures.organization.id))
                authorizationService.assignRole(
                    TEST_USER,
                    OrganizationRole.ADMIN,
                    orgId
                )

                val response = client.get("/authorization/superuser")
                response shouldHaveStatus HttpStatusCode.OK
                response.bodyAsText() shouldBe "false"
            }
        }

        "require an authenticated user" {
            requestShouldRequireAuthentication({ authorizationRoutes() }) {
                get("/authorization/superuser")
            }
        }
    }
})

private const val TEST_USER = "test-user"
