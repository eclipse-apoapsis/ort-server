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

package org.eclipse.apoapsis.ortserver.components.authorization.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.util.Date

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

class AuthorizedRoutesTest : WordSpec() {
    /**
     * Run a test with an authorized route using the given mock [service]. Set up routes using the provided
     * [routeBuilder]. Then execute the given [test] function with a properly configured HTTP client.
     */
    private fun runAuthorizationTest(
        service: AuthorizationService,
        routeBuilder: Route.() -> Unit,
        test: suspend (HttpClient) -> Unit
    ) {
        testApplication {
            application {
                install(Authentication) {
                    jwt(AuthenticationProviders.TOKEN_PROVIDER) {
                        realm = JWT_REALM
                        verifier(
                            JWT
                            .require(Algorithm.HMAC256(JWT_SECRET))
                            .withAudience(JWT_AUDIENCE)
                            .withIssuer(JWT_ISSUER)
                            .build()
                        )

                        validate { credential ->
                            createAuthorizedPrincipal(service, credential.payload)
                        }
                    }
                }

                install(StatusPages) {
                    exception<AuthorizationException> { call, _ ->
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }

                routing {
                    authenticate(AuthenticationProviders.TOKEN_PROVIDER, build = routeBuilder)
                }
            }

            val token = createToken()
            client.config {
                defaultRequest {
                    header("Authorization", "Bearer $token")
                }
            }.use { test(it) }
        }
    }

    init {
        "authorized routes" should {
            "support a route without permission requirements" {
                runAuthorizationTest(
                    mockk(),
                    routeBuilder = {
                        route("test") {
                            get(testDocs) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                    effectiveRole.elementId shouldBe CompoundHierarchyId.WILDCARD
                                    OrganizationPermission.entries.forAll { permission ->
                                        effectiveRole.hasOrganizationPermission(permission) shouldBe false
                                    }
                                    ProductPermission.entries.forAll { permission ->
                                        effectiveRole.hasProductPermission(permission) shouldBe false
                                    }
                                    RepositoryPermission.entries.forAll { permission ->
                                        effectiveRole.hasRepositoryPermission(permission) shouldBe false
                                    }
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support GET with an organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(OrganizationPermission.WRITE_SECRETS) } returns true
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            get(testDocs, requirePermission(OrganizationPermission.WRITE_SECRETS)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support POST with an organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(OrganizationPermission.MANAGE_GROUPS) } returns true
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            post(testDocs, requirePermission(OrganizationPermission.MANAGE_GROUPS)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.post("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support PATCH with an organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(OrganizationPermission.CREATE_PRODUCT) } returns true
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            patch(testDocs, requirePermission(OrganizationPermission.CREATE_PRODUCT)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.patch("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support PUT with an organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(OrganizationPermission.READ_PRODUCTS) } returns true
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            put(testDocs, requirePermission(OrganizationPermission.READ_PRODUCTS)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.put("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support DELETE with an organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(OrganizationPermission.WRITE) } returns true
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            delete(testDocs, requirePermission(OrganizationPermission.WRITE)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.delete("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK
                }
            }

            "support requests on product level" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasProductPermission(ProductPermission.DELETE) } returns true
                }
                val service = mockk<AuthorizationService> {
                    coEvery {
                        getEffectiveRole(USERNAME, ProductId(ID_PARAMETER))
                    } returns effectiveRole
                }

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{productId}") {
                            get(testDocs, requirePermission(ProductPermission.DELETE)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK

                    verify {
                        effectiveRole.hasProductPermission(ProductPermission.DELETE)
                    }
                }
            }

            "support requests on repository level" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasRepositoryPermission(RepositoryPermission.READ) } returns true
                }
                val service = mockk<AuthorizationService> {
                    coEvery {
                        getEffectiveRole(USERNAME, RepositoryId(ID_PARAMETER))
                    } returns effectiveRole
                }

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{repositoryId}") {
                            get(testDocs, requirePermission(RepositoryPermission.READ)) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK

                    verify {
                        effectiveRole.hasRepositoryPermission(RepositoryPermission.READ)
                    }
                }
            }

            "support requests that require superuser rights" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { isSuperuser } returns true
                }
                val service = mockk<AuthorizationService> {
                    coEvery {
                        getEffectiveRole(USERNAME, CompoundHierarchyId.WILDCARD)
                    } returns effectiveRole
                }

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            get(testDocs, requireSuperuser()) {
                                call.principal<OrtServerPrincipal>().shouldNotBeNull {
                                    username shouldBe USERNAME
                                }

                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.OK

                    verify {
                        effectiveRole.isSuperuser
                    }
                }
            }
        }

        "failed authorization checks" should {
            "return a 403 response for GET with insufficient organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(any()) } returns false
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            get(testDocs, requirePermission(OrganizationPermission.READ)) {
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.get("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "return a 403 response for POST with insufficient organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(any()) } returns false
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            post(testDocs, requirePermission(OrganizationPermission.CREATE_PRODUCT)) {
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.post("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "return a 403 response for PATCH with insufficient organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(any()) } returns false
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            patch(testDocs, requirePermission(OrganizationPermission.MANAGE_GROUPS)) {
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.patch("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "return a 403 response for PUT with insufficient organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(any()) } returns false
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            put(testDocs, requirePermission(OrganizationPermission.WRITE_SECRETS)) {
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.put("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }

            "return a 403 response for DELETE with insufficient organization permission" {
                val effectiveRole = mockk<EffectiveRole> {
                    every { hasOrganizationPermission(any()) } returns false
                }
                val service = createServiceForOrganizationRole(effectiveRole)

                runAuthorizationTest(
                    service,
                    routeBuilder = {
                        route("test/{organizationId}") {
                            delete(testDocs, requirePermission(OrganizationPermission.DELETE)) {
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }
                ) { client ->
                    val response = client.delete("test/$ID_PARAMETER")
                    response.status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }
}

/** A documented route for the test endpoint. */
private val testDocs: RouteConfig.() -> Unit = {
    operationId = "test"
}

private const val ID_PARAMETER = 42L
private const val USERNAME = "test-user"

private const val JWT_SECRET = "secret"
private const val JWT_ISSUER = "http://0.0.0.0:8080/"
private const val JWT_AUDIENCE = "test-audience"
private const val JWT_REALM = "Access to 'test'"

/**
 * Create a token for the test user.
 */
private fun createToken(): String =
    JWT.create()
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE)
        .withSubject("$USERNAME-ID")
        .withClaim("preferred_username", USERNAME)
        .withClaim("name", "$USERNAME-full-name")
        .withExpiresAt(Date(System.currentTimeMillis() + 60000))
        .sign(Algorithm.HMAC256(JWT_SECRET))

/**
 * Create a mock [AuthorizationService] that returns the given [effectiveRole] when asked for permissions of the test
 * user in the test organization.
 */
private fun createServiceForOrganizationRole(effectiveRole: EffectiveRole): AuthorizationService =
    mockk<AuthorizationService> {
    coEvery {
        getEffectiveRole(USERNAME, OrganizationId(ID_PARAMETER))
    } returns effectiveRole
}
