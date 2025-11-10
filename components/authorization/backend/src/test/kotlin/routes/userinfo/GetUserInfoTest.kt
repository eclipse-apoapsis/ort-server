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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.authorization.api.UserInfo
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.authorizationRoutes
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.DbAuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

class GetUserInfoTest : AbstractAuthorizationTest({
    lateinit var authorizationService: AuthorizationService

    beforeEach {
        authorizationService = DbAuthorizationService(dbExtension.db)
    }

    "GET /authorization/userinfo" should {
        "require an authenticated user" {
            requestShouldRequireAuthentication({ authorizationRoutes() }) {
                get("/authorization/userinfo")
            }
        }

        "return a UserInfo object with the correct username and full name" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                val response = client.get("/authorization/userinfo")
                response shouldHaveStatus HttpStatusCode.OK

                val userInfo = response.body<UserInfo>()
                userInfo.username shouldBe TEST_USER
                userInfo.fullName shouldBe "Test User"
            }
        }

        "return permissions for a user on organization level" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                val orgId = OrganizationId(dbExtension.fixtures.organization.id)
                val orgHierarchyId = CompoundHierarchyId.forOrganization(orgId)

                OrganizationRole.entries.forAll { role ->
                    authorizationService.assignRole(TEST_USER, role, orgHierarchyId)

                    val response = client.get("/authorization/userinfo?organizationId=${orgId.value}")
                    val userInfo = response.body<UserInfo>()
                    userInfo.isSuperuser shouldBe false
                    role.organizationPermissions.forAll { permission ->
                        userInfo.permissions shouldContain permission.name
                    }
                }
            }
        }

        "return permissions for a user on product level" {
            val productId = ProductId(dbExtension.fixtures.product.id)
            val productHierarchyId = CompoundHierarchyId.forProduct(
                OrganizationId(dbExtension.fixtures.organization.id),
                productId
            )

            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                ProductRole.entries.forAll { role ->
                    authorizationService.assignRole(TEST_USER, role, productHierarchyId)

                    val response = client.get("/authorization/userinfo?productId=${productId.value}")
                    val userInfo = response.body<UserInfo>()
                    userInfo.isSuperuser shouldBe false
                    role.productPermissions.forAll { permission ->
                        userInfo.permissions shouldContain permission.name
                    }
                }
            }
        }

        "return permissions for a user on repository level" {
            val repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
            val repositoryHierarchyId = CompoundHierarchyId.forRepository(
                OrganizationId(dbExtension.fixtures.organization.id),
                ProductId(dbExtension.fixtures.product.id),
                repositoryId
            )

            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                RepositoryRole.entries.forAll { role ->
                    authorizationService.assignRole(TEST_USER, role, repositoryHierarchyId)

                    val response = client.get("/authorization/userinfo?repositoryId=${repositoryId.value}")
                    val userInfo = response.body<UserInfo>()
                    userInfo.isSuperuser shouldBe false
                    role.repositoryPermissions.forAll { permission ->
                        userInfo.permissions shouldContain permission.name
                    }
                }
            }
        }

        "return a UserInfo object for a superuser on wildcard level" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                authorizationService.assignRole(TEST_USER, OrganizationRole.ADMIN, CompoundHierarchyId.WILDCARD)

                val response = client.get("/authorization/userinfo")
                response shouldHaveStatus HttpStatusCode.OK
                val userInfo = response.body<UserInfo>()

                userInfo.isSuperuser shouldBe true
                OrganizationRole.ADMIN.organizationPermissions.forAll { permission ->
                    userInfo.permissions shouldContain permission.name
                }
            }
        }

        "return a UserInfo object for a superuser on organization level" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                authorizationService.assignRole(TEST_USER, OrganizationRole.ADMIN, CompoundHierarchyId.WILDCARD)

                val response = client.get(
                    "/authorization/userinfo?organizationId=${dbExtension.fixtures.organization.id}"
                )
                response shouldHaveStatus HttpStatusCode.OK
                val userInfo = response.body<UserInfo>()

                userInfo.isSuperuser shouldBe true
                OrganizationRole.ADMIN.organizationPermissions.forAll { permission ->
                    userInfo.permissions shouldContain permission.name
                }
            }
        }

        "return a UserInfo object for a superuser on product level" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                authorizationService.assignRole(TEST_USER, OrganizationRole.ADMIN, CompoundHierarchyId.WILDCARD)

                val response = client.get("/authorization/userinfo?productId=${dbExtension.fixtures.product.id}")
                response shouldHaveStatus HttpStatusCode.OK
                val userInfo = response.body<UserInfo>()

                userInfo.isSuperuser shouldBe true
                ProductRole.ADMIN.productPermissions.forAll { permission ->
                    userInfo.permissions shouldContain permission.name
                }
            }
        }

        "return a UserInfo object for a superuser on repository level" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                authorizationService.assignRole(TEST_USER, OrganizationRole.ADMIN, CompoundHierarchyId.WILDCARD)

                val response = client.get("/authorization/userinfo?repositoryId=${dbExtension.fixtures.repository.id}")
                response shouldHaveStatus HttpStatusCode.OK
                val userInfo = response.body<UserInfo>()

                userInfo.isSuperuser shouldBe true
                RepositoryRole.ADMIN.repositoryPermissions.forAll { permission ->
                    userInfo.permissions shouldContain permission.name
                }
            }
        }

        "return 400 if multiple hierarchy IDs are provided" {
            authorizationTestApplication(routes = { authorizationRoutes() }) { _, client ->
                val response = client.get(
                    "/authorization/userinfo?organizationId=${dbExtension.fixtures.organization.id}" +
                        "&productId=${dbExtension.fixtures.product.id}"
                )

                response shouldHaveStatus HttpStatusCode.BadRequest
            }
        }
    }
})

private const val TEST_USER = "test-user"
