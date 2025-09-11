/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.api

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.containAnyOf
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization
import org.eclipse.apoapsis.ortserver.api.v1.model.OrganizationVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity as ApiSeverity
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups as ApiUserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.addUserRole
import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationRole as ApiOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser
import org.eclipse.apoapsis.ortserver.core.SUPERUSER
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters.Companion.DEFAULT_LIMIT
import org.eclipse.apoapsis.ortserver.model.util.asPresent as asPresent2
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.KeycloakAuthorizationService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.apimodel.valueOrThrow
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody
import org.eclipse.apoapsis.ortserver.utils.test.Integration

@Suppress("LargeClass")
class OrganizationsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var authorizationService: AuthorizationService
    lateinit var organizationService: OrganizationService
    lateinit var productService: ProductService

    beforeEach {
        authorizationService = KeycloakAuthorizationService(
            keycloakClient,
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            keycloakGroupPrefix = ""
        )

        organizationService = OrganizationService(
            dbExtension.db,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.productRepository,
            authorizationService
        )

        productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.ortRunRepository,
            authorizationService
        )
    }

    val organizationName = "name"
    val organizationDescription = "description"

    suspend fun createOrganization(name: String = organizationName, description: String = organizationDescription) =
        organizationService.createOrganization(name, description)

    "GET /organizations" should {
        "return all existing organizations for the superuser" {
            integrationTestApplication {
                val org1 = createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org1.mapToApi(), org2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return only organizations for which the user has OrganizationPermission.READ" {
            integrationTestApplication {
                createOrganization(name = "org1")
                val org2 = createOrganization(name = "org2")
                createOrganization(name = "org3")
                val org4 = createOrganization(name = "org4")
                createOrganization(name = "org5")

                keycloak.keycloakAdminClient.addUserRole(
                    TEST_USER.username.value,
                    OrganizationPermission.READ.roleName(org2.id)
                )
                keycloak.keycloakAdminClient.addUserRole(
                    TEST_USER.username.value,
                    OrganizationPermission.READ.roleName(org4.id)
                )

                val response = testUserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org2.mapToApi(), org4.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)),
                    )
                )
            }
        }

        "return an empty result if the user has no OrganizationPermission.READ" {
            integrationTestApplication {
                createOrganization(name = "org1")
                createOrganization(name = "org2")
                createOrganization(name = "org3")

                val response = testUserClient.get("/api/v1/organizations")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse<Organization>(
                    emptyList(),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)),
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "name2", description = "description2")

                val response = superuserClient.get("/api/v1/organizations?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org2.mapToApi()),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING)),
                    )
                )
            }
        }

        "require authentication" {
            requestShouldRequireAuthentication {
                get("/api/v1/organizations")
            }
        }
    }

    "GET /organizations/{organizationId}" should {
        "return a single organization" {
            integrationTestApplication {
                val createdOrganization = createOrganization()

                val response = superuserClient.get("/api/v1/organizations/${createdOrganization.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(createdOrganization.id, organizationName, organizationDescription)
            }
        }

        "respond with 'NotFound' if no organization exists" {
            integrationTestApplication {
                superuserClient.get("/api/v1/organizations/999999") shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations" should {
        "create an organization in the database" {
            integrationTestApplication {
                val org = CreateOrganization(name = "name", description = "description")

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Organization(1, org.name, org.description)

                organizationService.getOrganization(1)?.mapToApi().shouldBe(
                    Organization(1, org.name, org.description)
                )
            }
        }

        "respond with 'Bad Request' if the organization's name is invalid" {
            integrationTestApplication {
                val org = CreateOrganization(name = " org_name!", description = "description")

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateOrganization"

                organizationService.getOrganization(1)?.mapToApi().shouldBeNull()
            }
        }

        "respond with 'Bad Request' if the request body is invalid" {
            integrationTestApplication {
                val invalidJson = """
                    {
                      "name": "Example Organization",
                      "description": This description is missing double quotes.,
                    }
                """.trimIndent()

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(invalidJson)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid request body."
                body.cause shouldStartWith "BadRequestException: Failed to convert request body to class " +
                        "org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization"
                body.cause shouldContain "Caused by: JsonDecodingException: Unexpected JSON token at offset 53"

                organizationService.getOrganization(1)?.mapToApi().shouldBeNull()
            }
        }

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val org = CreateOrganization(name = "name", description = "description")

                val createdOrg = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }.body<Organization>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id) +
                            OrganizationRole.getRolesForOrganization(createdOrg.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    OrganizationRole.getGroupsForOrganization(createdOrg.id)
                )
            }
        }

        "respond with 'Conflict' if the organization already exists" {
            integrationTestApplication {
                createOrganization()

                val org = CreateOrganization(name = organizationName, description = organizationDescription)

                superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "require the superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.Created) {
                val org = CreateOrganization(name = "name", description = "description")
                post("/api/v1/organizations") { setBody(org) }
            }
        }
    }

    "PATCH /organizations/{organizationId}" should {
        "update an organization" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val updatedOrganization = UpdateOrganization(
                    "updated".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(updatedOrganization)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(
                    createdOrg.id,
                    updatedOrganization.name.valueOrThrow,
                    updatedOrganization.description.valueOrThrow
                )

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    updatedOrganization.name.valueOrThrow,
                    updatedOrganization.description.valueOrThrow
                )
            }
        }

        "respond with 'Bad Request' if the organization's name is invalid" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val updatedOrganization = UpdateOrganization(
                    " !!updated @382 ".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(updatedOrganization)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateOrganization"

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    createdOrg.id,
                    createdOrg.name,
                    createdOrg.description
                )
            }
        }

        "be able to delete a value and ignore absent values" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val organizationUpdateRequest = UpdateOrganization(
                    name = OptionalValue.Absent,
                    description = null.asPresent()
                )

                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(organizationUpdateRequest)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Organization(
                    id = createdOrg.id,
                    name = organizationName,
                    description = null
                )

                organizationService.getOrganization(createdOrg.id)?.mapToApi() shouldBe Organization(
                    id = createdOrg.id,
                    name = organizationName,
                    description = null
                )
            }
        }

        "require OrganizationPermission.WRITE" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.WRITE.roleName(createdOrg.id)) {
                val updateOrg = UpdateOrganization("updated".asPresent(), "updated".asPresent())
                patch("/api/v1/organizations/${createdOrg.id}") { setBody(updateOrg) }
            }
        }
    }

    "DELETE /organizations/{organizationId}" should {
        "delete an organization" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                superuserClient.delete("/api/v1/organizations/${createdOrg.id}") shouldHaveStatus
                        HttpStatusCode.NoContent

                organizationService.listOrganizations() shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                superuserClient.delete("/api/v1/organizations/${createdOrg.id}")

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    OrganizationPermission.getRolesForOrganization(createdOrg.id) +
                            OrganizationRole.getRolesForOrganization(createdOrg.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    OrganizationRole.getGroupsForOrganization(createdOrg.id)
                )
            }
        }

        "require OrganizationPermission.DELETE" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.DELETE.roleName(createdOrg.id), HttpStatusCode.NoContent) {
                delete("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations/{orgId}/products" should {
        "create a product" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct("product", "description")
                val response = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody Product(1, orgId, product.name, product.description)

                productService.getProduct(1)?.mapToApi() shouldBe Product(1, orgId, product.name, product.description)
            }
        }

        "respond with 'Bad Request' if the product's name is invalid" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct(" product!", "description")
                val response = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateProduct"

                productService.getProduct(1)?.mapToApi().shouldBeNull()
            }
        }

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = CreateProduct(name = "product", description = "description")
                val createdProduct = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }.body<Product>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    ProductPermission.getRolesForProduct(createdProduct.id) +
                            ProductRole.getRolesForProduct(createdProduct.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    ProductRole.getGroupsForProduct(createdProduct.id)
                )
            }
        }

        "require OrganizationPermission.CREATE_PRODUCT" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationPermission.CREATE_PRODUCT.roleName(createdOrg.id),
                HttpStatusCode.Created
            ) {
                val createProduct = CreateProduct(name = "product", description = "description")
                post("/api/v1/organizations/${createdOrg.id}/products") { setBody(createProduct) }
            }
        }
    }

    "GET /organizations/{orgId}/products" should {
        "return all products of an organization" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                val createdProduct1 =
                    organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = superuserClient.get("/api/v1/organizations/$orgId/products")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        Product(createdProduct1.id, orgId, name1, description),
                        Product(createdProduct2.id, orgId, name2, description)
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)

                val response = superuserClient.get("/api/v1/organizations/$orgId/products?sort=-name&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(Product(createdProduct2.id, orgId, name2, description)),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "require OrganizationPermission.READ_PRODUCTS" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ_PRODUCTS.roleName(createdOrg.id)) {
                get("/api/v1/organizations/${createdOrg.id}/products")
            }
        }
    }

    "PUT/DELETE /organizations/{orgId}/roles/{role}" should {
        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "require OrganizationPermission.MANAGE_GROUPS for method '${method.value}'" {
                val createdOrg = createOrganization()
                val user = Username(TEST_USER.username.value)

                requestShouldRequireRole(
                    OrganizationPermission.MANAGE_GROUPS.roleName(createdOrg.id),
                    HttpStatusCode.NoContent
                ) {
                    when (method) {
                        HttpMethod.Put -> put("/api/v1/organizations/${createdOrg.id}/roles/READER") {
                            setBody(user)
                        }

                        HttpMethod.Delete -> delete(
                            "/api/v1/organizations/${createdOrg.id}/roles/READER?username=${user.username}"
                        )

                        else -> error("Unsupported method: $method")
                    }
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'NotFound' if the user does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username("non-existing-username")

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/roles/READER"
                        ) {
                            setBody(user)
                        }

                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/${createdOrg.id}/roles/READER?username=${user.username}"
                        )

                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.InternalServerError

                    val body = response.body<ErrorResponse>()
                    body.cause shouldContain "Could not find user"
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'NotFound' if the organization does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/999999/roles/READER"
                        ) {
                            setBody(user)
                        }

                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/999999/roles/READER?username=${user.username}"
                        )

                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldContain "not found"
                }
            }
        }

        forAll(
            row(HttpMethod.Put)
        ) { method ->
            "respond with 'BadRequest' if the request body is invalid for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val org = CreateOrganization(name = "name", description = "description") // Wrong request body

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/roles/READER"
                        ) {
                            setBody(org)
                        }

                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.BadRequest
                }
            }
        }

        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "respond with 'BadRequest' if the role does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/organizations/${createdOrg.id}/roles/non-existing-role"
                        ) {
                            setBody(user)
                        }

                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/organizations/${createdOrg.id}/roles/non-existing-role?username=${user.username}"
                        )

                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.BadRequest
                }
            }
        }
    }

    "PUT /organizations/{orgId}/roles/{role}" should {
        enumValues<ApiOrganizationRole>().forAll { role ->
            "assign the '$role' role to the user" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)

                    val response = superuserClient.put(
                        "/api/v1/organizations/${createdOrg.id}/roles/${role.name}"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupName = role.mapToModel().groupName(createdOrg.id)
                    val group = keycloakClient.getGroup(GroupName(groupName))
                    group.shouldNotBeNull()

                    val members = keycloakClient.getGroupMembers(group.name)
                    members shouldHaveSize 1
                    members.map { it.username } shouldContain TEST_USER.username
                }
            }
        }
    }

    "DELETE /organizations/{orgId}/roles/{role}" should {
        enumValues<ApiOrganizationRole>().forAll { role ->
            "remove the '$role' role from the user" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val user = Username(TEST_USER.username.value)

                    authorizationService.addUserRole(user.username, OrganizationId(createdOrg.id), role.mapToModel())

                    // Check pre-condition
                    val groupName = role.mapToModel().groupName(createdOrg.id)
                    val groupBefore = keycloakClient.getGroup(GroupName(groupName))
                    val membersBefore = keycloakClient.getGroupMembers(groupBefore.name)
                    membersBefore shouldHaveSize 1
                    membersBefore.map { it.username } shouldContain TEST_USER.username

                    val response = superuserClient.delete(
                        "/api/v1/organizations/${createdOrg.id}/roles/${role.name}?username=${user.username}"
                    )

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupAfter = keycloakClient.getGroup(GroupName(groupName))
                    groupAfter.shouldNotBeNull()

                    val membersAfter = keycloakClient.getGroupMembers(groupAfter.name)
                    membersAfter.shouldBeEmpty()
                }
            }
        }
    }

    "GET /organizations/{organizationId}/vulnerabilities" should {
        "return vulnerabilities across repositories in the organization found in latest successful advisor jobs" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val prod1Id = dbExtension.fixtures.createProduct(organizationId = orgId).id
                val prod2Id = dbExtension.fixtures.createProduct("Prod2", organizationId = orgId).id

                val repo11Id = dbExtension.fixtures.createRepository(productId = prod1Id).id
                val repo12Id = dbExtension.fixtures.createRepository(
                    url = "https://example.com/repo12.git",
                    productId = prod1Id
                ).id
                val repo21Id = dbExtension.fixtures.createRepository(productId = prod2Id).id
                val repo22Id = dbExtension.fixtures.createRepository(
                    url = "https://example.com/repo22.git",
                    productId = prod2Id
                ).id

                val commonVulnerability1 = Vulnerability(
                    externalId = "CVE-2020-0123",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "Medium",
                            score = 4.2f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                val commonVulnerability2 = Vulnerability(
                    externalId = "CVE-2021-1234",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "Medium",
                            score = 4.2f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                val identifier1 = Identifier("Maven", "org.apache.logging.log4j", "log4j-core", "2.14.0")
                val identifier2 = Identifier("Maven", "org.apache.logging.log4j", "log4j-api", "2.14.0")
                val identifier3 = Identifier("Maven", "org.apache.logging.log4j", "log4j-slf4j-impl", "2.14.0")

                val run1Id = dbExtension.fixtures.createOrtRun(repo11Id).id
                val advisorJob1Id = dbExtension.fixtures.createAdvisorJob(run1Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob1Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                val run1Vulnerability = Vulnerability(
                    externalId = "CVE-2022-2345",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "LOW",
                            score = 1.1f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob1Id,
                    mapOf(
                        identifier1 to listOf(
                            generateAdvisorResult(
                                listOf(
                                    commonVulnerability1,
                                    run1Vulnerability
                                )
                            )
                        )
                    )
                )

                val run2Id = dbExtension.fixtures.createOrtRun(repo11Id).id
                val advisorJob2Id = dbExtension.fixtures.createAdvisorJob(run2Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob2Id,
                    status = JobStatus.FAILED.asPresent2()
                )

                val run3Id = dbExtension.fixtures.createOrtRun(repo12Id).id
                val advisorJob3Id = dbExtension.fixtures.createAdvisorJob(run3Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob3Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob3Id,
                    mapOf(
                        identifier1 to listOf(generateAdvisorResult(listOf(commonVulnerability1))),
                        identifier2 to listOf(generateAdvisorResult(listOf(commonVulnerability2)))
                    )
                )

                val run4Id = dbExtension.fixtures.createOrtRun(repo21Id).id
                val advisorJob4Id = dbExtension.fixtures.createAdvisorJob(run4Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob4Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                val run4Vulnerability = Vulnerability(
                    externalId = "CVE-2023-3456",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "LOW",
                            score = 1.1f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob4Id,
                    mapOf(
                        identifier1 to listOf(
                            generateAdvisorResult(
                                listOf(
                                    commonVulnerability1,
                                    run4Vulnerability
                                )
                            )
                        )
                    )
                )

                val run5Id = dbExtension.fixtures.createOrtRun(repo21Id).id
                val advisorJob5Id = dbExtension.fixtures.createAdvisorJob(run5Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob5Id,
                    status = JobStatus.FAILED.asPresent2()
                )

                val run6Id = dbExtension.fixtures.createOrtRun(repo22Id).id
                val advisorJob6Id = dbExtension.fixtures.createAdvisorJob(run6Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob6Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob6Id,
                    mapOf(
                        identifier1 to listOf(generateAdvisorResult(listOf(commonVulnerability1))),
                        identifier2 to listOf(generateAdvisorResult(listOf(commonVulnerability2))),
                        identifier3 to listOf(generateAdvisorResult(listOf(commonVulnerability2)))
                    )
                )

                val response =
                    superuserClient.get(
                        "/api/v1/organizations/$orgId/vulnerabilities?sort=-rating,-repositoriesCount"
                    )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability1.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run1Id, run3Id, run4Id, run6Id),
                            repositoriesCount = 4
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability2.mapToApi(),
                            identifier = identifier2.mapToApi(),
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run3Id, run6Id),
                            repositoriesCount = 2
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability2.mapToApi(),
                            identifier = identifier3.mapToApi(),
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run6Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = run1Vulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            rating = VulnerabilityRating.LOW,
                            ortRunIds = listOf(run1Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = run4Vulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            rating = VulnerabilityRating.LOW,
                            ortRunIds = listOf(run4Id),
                            repositoriesCount = 1
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 5,
                        sortProperties = listOf(
                            SortProperty("rating", SortDirection.DESCENDING),
                            SortProperty("repositoriesCount", SortDirection.DESCENDING)
                        )
                    )
                )
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrganization = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrganization.id)) {
                get("/api/v1/organizations/${createdOrganization.id}/vulnerabilities")
            }
        }
    }

    "GET /organizations/{organizationId}/statistics/runs" should {
        "return statistics for runs in repositories of an organization" {
            integrationTestApplication {
                val orgId = createOrganization().id
                val prod1Id = dbExtension.fixtures.createProduct(organizationId = orgId).id
                val prodId2 = dbExtension.fixtures.createProduct("Prod2", organizationId = orgId).id
                val repo1Id = dbExtension.fixtures.createRepository(productId = prod1Id).id
                val repo2Id = dbExtension.fixtures.createRepository(
                    url = "https://example.com/repo2.git",
                    productId = prodId2
                ).id

                val pkg = Package(
                    identifier = Identifier("Maven", "com.example", "example", "1.0"),
                    purl = "pkg:maven/com.example/example@1.0",
                    cpe = null,
                    authors = emptySet(),
                    declaredLicenses = emptySet(),
                    processedDeclaredLicense = ProcessedDeclaredLicense(
                        spdxExpression = null,
                        mappedLicenses = emptyMap(),
                        unmappedLicenses = emptySet(),
                    ),
                    description = "An example package",
                    homepageUrl = "https://example.com",
                    binaryArtifact = RemoteArtifact(
                        "https://example.com/example-1.0.jar",
                        "0123456789abcdef0123456789abcdef01234567",
                        "SHA-1"
                    ),
                    sourceArtifact = RemoteArtifact(
                        "https://example.com/example-1.0-sources.jar",
                        "0123456789abcdef0123456789abcdef01234567",
                        "SHA-1"
                    ),
                    vcs = VcsInfo(
                        RepositoryType("GIT"),
                        "https://example.com/git",
                        "revision",
                        "path"
                    ),
                    vcsProcessed = VcsInfo(
                        RepositoryType("GIT"),
                        "https://example.com/git",
                        "revision",
                        "path"
                    ),
                    isMetadataOnly = false,
                    isModified = false
                )

                val repo1RunId = dbExtension.fixtures.createOrtRun(repo1Id).id

                val anJob1Id = dbExtension.fixtures.createAnalyzerJob(repo1RunId).id
                dbExtension.fixtures.createAnalyzerRun(
                    anJob1Id,
                    packages = setOf(pkg)
                )
                dbExtension.fixtures.analyzerJobRepository.update(
                    anJob1Id,
                    status = JobStatus.FINISHED.asPresent2()
                )

                val evJobId = dbExtension.fixtures.createEvaluatorJob(repo1RunId).id
                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evJobId,
                    startTime = Clock.System.now(),
                    endTime = Clock.System.now(),
                    violations = listOf(
                        RuleViolation(
                            "rule1",
                            null,
                            null,
                            null,
                            Severity.HINT,
                            "message",
                            "how-to-fix"
                        )
                    )
                )
                dbExtension.fixtures.evaluatorJobRepository.update(
                    evJobId,
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent2()
                )

                val repo2RunId = dbExtension.fixtures.createOrtRun(repo2Id).id

                val anJob2Id = dbExtension.fixtures.createAnalyzerJob(repo2RunId).id
                dbExtension.fixtures.createAnalyzerRun(
                    anJob2Id,
                    packages = setOf(pkg),
                    issues = listOf(
                        Issue(
                            timestamp = Clock.System.now(),
                            source = "Analyzer",
                            message = "Issue",
                            severity = Severity.WARNING,
                            affectedPath = "path"
                        ),
                    )
                )
                dbExtension.fixtures.analyzerJobRepository.update(
                    anJob2Id,
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent2()
                )

                val advJobId = dbExtension.fixtures.createAdvisorJob(repo2RunId).id
                dbExtension.fixtures.createAdvisorRun(
                    advJobId,
                    mapOf(
                        Identifier("Maven", "com.example", "example", "1.0") to
                                listOf(
                                    AdvisorResult(
                                        advisorName = "advisor",
                                        capabilities = listOf("vulnerabilities"),
                                        startTime = Clock.System.now(),
                                        endTime = Clock.System.now(),
                                        issues = emptyList(),
                                        defects = emptyList(),
                                        vulnerabilities = listOf(
                                            Vulnerability(
                                                externalId = "CVE-2020-2346",
                                                summary = "A vulnerability",
                                                description = "A description",
                                                references = listOf(
                                                    VulnerabilityReference(
                                                        url = "https://example.com",
                                                        scoringSystem = "CVSS",
                                                        severity = "MEDIUM",
                                                        score = 5.1f,
                                                        vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                                                    )
                                                )
                                            )
                                        )
                                    )
                                ),
                    )
                )
                dbExtension.fixtures.advisorJobRepository.update(advJobId, status = JobStatus.FINISHED.asPresent2())

                val response = superuserClient.get("/api/v1/organizations/$orgId/statistics/runs")

                response shouldHaveStatus HttpStatusCode.OK

                val statistics = response.body<OrtRunStatistics>()

                with(statistics) {
                    issuesCount shouldBe 1
                    issuesCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 0,
                            ApiSeverity.WARNING to 1,
                            ApiSeverity.ERROR to 0
                        )
                    )
                    packagesCount shouldBe 1
                    ecosystems?.shouldContainExactly(
                        listOf(
                            EcosystemStats("Maven", 1)
                        )
                    )
                    vulnerabilitiesCount shouldBe 1
                    vulnerabilitiesCountByRating?.shouldContainExactly(
                        mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 0,
                            VulnerabilityRating.MEDIUM to 1,
                            VulnerabilityRating.HIGH to 0,
                            VulnerabilityRating.CRITICAL to 0
                        )
                    )
                    ruleViolationsCount shouldBe 1
                    ruleViolationsCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 1,
                            ApiSeverity.WARNING to 0,
                            ApiSeverity.ERROR to 0
                        )
                    )
                }
            }
        }

        "return nulls for counts if no valid runs are found" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val response = superuserClient.get("/api/v1/organizations/$orgId/statistics/runs")

                response shouldHaveStatus HttpStatusCode.OK

                val statistics = response.body<OrtRunStatistics>()

                with(statistics) {
                    issuesCount should beNull()
                    issuesCountBySeverity should beNull()
                    packagesCount should beNull()
                    ecosystems should beNull()
                    vulnerabilitiesCount should beNull()
                    vulnerabilitiesCountByRating should beNull()
                    ruleViolationsCount should beNull()
                    ruleViolationsCountBySeverity should beNull()
                }
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrganization = createOrganization()
            requestShouldRequireRole(OrganizationPermission.READ.roleName(createdOrganization.id)) {
                get("/api/v1/organizations/${createdOrganization.id}/statistics/runs")
            }
        }
    }

    "GET /organizations/{productId}/users" should {
        "return list of users that have rights for organization" {
            integrationTestApplication {
                val orgId = createOrganization().id

                authorizationService.addUserRole(
                    TEST_USER.username.value,
                    OrganizationId(orgId),
                    OrganizationRole.READER
                )
                authorizationService.addUserRole(
                    SUPERUSER.username.value,
                    OrganizationId(orgId),
                    OrganizationRole.WRITER
                )
                authorizationService.addUserRole(
                    SUPERUSER.username.value,
                    OrganizationId(orgId),
                    OrganizationRole.ADMIN
                )

                val response = superuserClient.get("/api/v1/organizations/$orgId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        ApiUserWithGroups(
                            ApiUser(SUPERUSER.username.value, SUPERUSER.firstName, SUPERUSER.lastName, SUPERUSER.email),
                            listOf(ApiUserGroup.ADMINS)
                        ),
                        ApiUserWithGroups(
                            ApiUser(TEST_USER.username.value, TEST_USER.firstName, TEST_USER.lastName, TEST_USER.email),
                            listOf(ApiUserGroup.READERS)
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "return empty list if no user has rights for organizations" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val response = superuserClient.get("/api/v1/organizations/$orgId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse<ApiUserWithGroups>(
                    emptyList(),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 0,
                        sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "respond with 'Bad Request' if there is more than one sort field" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val response = superuserClient.get("/api/v1/organizations/$orgId/users?sort=username,firstName")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Exactly one sort field must be defined."
            }
        }

        "respond with 'Bad Request' if there is no sort field" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val response = superuserClient.get("/api/v1/organizations/$orgId/users?sort=")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Empty sort field."
            }
        }

        "require OrganizationPermission.READ" {
            val orgId = createOrganization().id

            requestShouldRequireRole(OrganizationPermission.READ.roleName(orgId)) {
                get("/api/v1/organizations/$orgId/users")
            }
        }
    }
})

private fun generateAdvisorResult(vulnerabilities: List<Vulnerability>) = AdvisorResult(
    advisorName = "advisor",
    capabilities = listOf("vulnerabilities"),
    startTime = Clock.System.now(),
    endTime = Clock.System.now(),
    issues = emptyList(),
    defects = emptyList(),
    vulnerabilities = vulnerabilities
)
