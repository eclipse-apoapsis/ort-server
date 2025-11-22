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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
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
import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization
import org.eclipse.apoapsis.ortserver.api.v1.model.OrganizationVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity as ApiSeverity
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups as ApiUserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.components.authorization.api.OrganizationRole as ApiOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.routes.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService
import org.eclipse.apoapsis.ortserver.components.authorization.service.DbAuthorizationService
import org.eclipse.apoapsis.ortserver.core.SUPERUSER
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
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
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.shared.apimodel.ErrorResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedSearchResponse
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
        authorizationService = DbAuthorizationService(dbExtension.db)

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

                authorizationService.assignRole(
                    TEST_USER.username.value,
                    OrganizationRole.READER,
                    CompoundHierarchyId.forOrganization(OrganizationId(org2.id))
                )
                authorizationService.assignRole(
                    TEST_USER.username.value,
                    OrganizationRole.READER,
                    CompoundHierarchyId.forOrganization(OrganizationId(org4.id))
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

        "support filter query parameters" {
            integrationTestApplication {
                createOrganization(name = "name1", description = "description1")
                val org2 = createOrganization(name = "test", description = "description2")

                val response = superuserClient.get("/api/v1/organizations?filter=test&sort=-name")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 1,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING)),
                    )
                )
            }
        }

        "support regex filter ending with pattern" {
            integrationTestApplication {
                val org1 = createOrganization(name = "org-auth-api", description = "Authentication API")
                val org2 = createOrganization(name = "org-user-api", description = "API service")
                createOrganization(name = "api-gateway", description = "Gateway service") // Should not match
                createOrganization(name = "core-service", description = "Core service") // Should not match

                val response = superuserClient.get("/api/v1/organizations?filter=api$")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org1.mapToApi(), org2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)), // default sort
                    )
                )
            }
        }

        "support regex filter starting with pattern" {
            integrationTestApplication {
                val org1 = createOrganization(name = "core-auth", description = "Core authentication")
                val org2 = createOrganization(name = "core-service", description = "Core service")
                createOrganization(name = "user-core", description = "User core") // Should not match
                createOrganization(name = "api-service", description = "API service") // Should not match

                val response = superuserClient.get("/api/v1/organizations?filter=^core&sort=name")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(org1.mapToApi(), org2.mapToApi()),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)), // default sort
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
            requestShouldRequireRole(OrganizationRole.READER, createdOrg.hierarchyId) {
                get("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations" should {
        "create an organization in the database" {
            integrationTestApplication {
                val org = PostOrganization(name = "name", description = "description")

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
                val org = PostOrganization(name = " org_name!", description = "description")

                val response = superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for PostOrganization"

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
                        "org.eclipse.apoapsis.ortserver.api.v1.model.PostOrganization"
                body.cause shouldContain "Caused by: JsonDecodingException: Unexpected JSON token at offset 53"

                organizationService.getOrganization(1)?.mapToApi().shouldBeNull()
            }
        }

        "respond with 'Conflict' if the organization already exists" {
            integrationTestApplication {
                createOrganization()

                val org = PostOrganization(name = organizationName, description = organizationDescription)

                superuserClient.post("/api/v1/organizations") {
                    setBody(org)
                } shouldHaveStatus HttpStatusCode.Conflict
            }
        }

        "require the superuser role" {
            requestShouldRequireSuperuser(HttpStatusCode.Created) {
                val org = PostOrganization(name = "name", description = "description")
                post("/api/v1/organizations") { setBody(org) }
            }
        }
    }

    "PATCH /organizations/{organizationId}" should {
        "update an organization" {
            integrationTestApplication {
                val createdOrg = createOrganization()

                val updatedOrganization = PatchOrganization(
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

                val updatedOrganization = PatchOrganization(
                    " !!updated @382 ".asPresent(),
                    "updated description of testOrg".asPresent()
                )
                val response = superuserClient.patch("/api/v1/organizations/${createdOrg.id}") {
                    setBody(updatedOrganization)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for PatchOrganization"

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

                val organizationUpdateRequest = PatchOrganization(
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
            requestShouldRequireRole(OrganizationRole.WRITER, createdOrg.hierarchyId) {
                val updateOrg = PatchOrganization("updated".asPresent(), "updated".asPresent())
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

                organizationService.listOrganizations().data shouldBe emptyList()
            }
        }

        "require OrganizationPermission.DELETE" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationRole.ADMIN,
                createdOrg.hierarchyId,
                HttpStatusCode.NoContent
            ) {
                delete("/api/v1/organizations/${createdOrg.id}")
            }
        }
    }

    "POST /organizations/{orgId}/products" should {
        "create a product" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val product = PostProduct("product", "description")
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

                val product = PostProduct(" product!", "description")
                val response = superuserClient.post("/api/v1/organizations/$orgId/products") {
                    setBody(product)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for PostProduct"

                productService.getProduct(1)?.mapToApi().shouldBeNull()
            }
        }

        "require OrganizationPermission.CREATE_PRODUCT" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(
                OrganizationRole.WRITER,
                createdOrg.hierarchyId,
                HttpStatusCode.Created
            ) {
                val createProduct = PostProduct(name = "product", description = "description")
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

        "return only products for which the user has ProductPermission.READ" {
            integrationTestApplication {
                val createdOrganization = createOrganization()
                val orgId = createdOrganization.id
                val otherOrg = createOrganization("otherOrg")

                val name1 = "name1"
                val name2 = "name2"
                val description = "description"

                val createdProduct1 =
                    organizationService.createProduct(name = name1, description = description, organizationId = orgId)
                val createdProduct2 =
                    organizationService.createProduct(name = name2, description = description, organizationId = orgId)
                organizationService.createProduct(name = "name3", description = description, organizationId = orgId)
                val createdRepo = productService.createRepository(
                    RepositoryType.GIT,
                    "https://example.com/repo.git",
                    createdProduct2.id,
                    null
                )
                val productInOtherOrg = organizationService.createProduct(
                    name = "otherOrgProduct",
                    description = description,
                    organizationId = otherOrg.id
                )

                authorizationService.assignRole(
                    TEST_USER.username.value,
                    ProductRole.READER,
                    CompoundHierarchyId.forProduct(
                        OrganizationId(createdOrganization.id),
                        ProductId(createdProduct1.id)
                    )
                )
                authorizationService.assignRole(
                    TEST_USER.username.value,
                    ProductRole.READER,
                    CompoundHierarchyId.forProduct(
                        OrganizationId(otherOrg.id),
                        ProductId(productInOtherOrg.id)
                    )
                )
                authorizationService.assignRole(
                    TEST_USER.username.value,
                    RepositoryRole.WRITER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(createdOrganization.id),
                        ProductId(createdProduct2.id),
                        RepositoryId(createdRepo.id)
                    )
                )

                val response = testUserClient.get("/api/v1/organizations/$orgId/products")

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

        "support filter parameter" {
            integrationTestApplication {
                val orgId = createOrganization(name = "org1").id
                val otherOrgId = createOrganization(name = "org2").id

                val name2 = "name2"
                val description = "description"

                val product1 = organizationService.createProduct(
                    name = "product1-testing",
                    description = description,
                    organizationId = orgId
                )
                organizationService.createProduct(name = name2, description = description, organizationId = orgId)
                organizationService.createProduct(
                    name = "product2-testing",
                    description = description,
                    organizationId = otherOrgId
                )

                val response =
                    superuserClient.get("/api/v1/organizations/$orgId/products?sort=-name&limit=2&filter=testing$")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(Product(product1.id, orgId, product1.name, description)),
                    PagingData(
                        limit = 2,
                        offset = 0,
                        totalCount = 1,
                        sortProperties = listOf(SortProperty("name", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "require OrganizationPermission.READ_PRODUCTS" {
            val createdOrg = createOrganization()
            requestShouldRequireRole(OrganizationRole.WRITER, createdOrg.hierarchyId) {
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
                    OrganizationRole.ADMIN,
                    createdOrg.hierarchyId,
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

                    response shouldHaveStatus HttpStatusCode.NotFound

                    val body = response.body<ErrorResponse>()
                    body.message shouldContain "Could not find user"
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
                    val org = PostOrganization(name = "name", description = "description") // Wrong request body

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
                    val orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg.id))
                    val user = Username(TEST_USER.username.value)

                    val response = superuserClient.put(
                        "/api/v1/organizations/${createdOrg.id}/roles/${role.name}"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val members = authorizationService.listUsersWithRole(role.mapToModel(), orgHierarchyId)
                    members shouldHaveSize 1
                    members shouldContain TEST_USER.username.value
                }
            }
        }
    }

    "DELETE /organizations/{orgId}/roles/{role}" should {
        enumValues<ApiOrganizationRole>().forAll { role ->
            "remove the '$role' role from the user" {
                integrationTestApplication {
                    val createdOrg = createOrganization()
                    val orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(createdOrg.id))
                    val user = Username(TEST_USER.username.value)

                    authorizationService.assignRole(user.username, role.mapToModel(), orgHierarchyId)

                    val response = superuserClient.delete(
                        "/api/v1/organizations/${createdOrg.id}/roles/${role.name}?username=${user.username}"
                    )

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val membersAfter = authorizationService.listUsersWithRole(role.mapToModel(), orgHierarchyId)
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

                val pkg1 = dbExtension.fixtures.generatePackage(identifier1)
                val pkg2 = dbExtension.fixtures.generatePackage(identifier2)
                val pkg3 = dbExtension.fixtures.generatePackage(identifier3)

                val run1Id = dbExtension.fixtures.createOrtRun(repo11Id).id
                val analyzerJob1Id = dbExtension.fixtures.createAnalyzerJob(run1Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob1Id, packages = setOf(pkg1))
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
                val analyzerJob2Id = dbExtension.fixtures.createAnalyzerJob(run2Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob2Id, packages = setOf(pkg1, pkg2))
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob2Id,
                    status = JobStatus.FAILED.asPresent2()
                )

                val run3Id = dbExtension.fixtures.createOrtRun(repo12Id).id
                val analyzerJob3Id = dbExtension.fixtures.createAnalyzerJob(run3Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob3Id, packages = setOf(pkg1, pkg2))
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
                val analyzerJob4Id = dbExtension.fixtures.createAnalyzerJob(run4Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob4Id, packages = setOf(pkg1))
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
                val analyzerJob5Id = dbExtension.fixtures.createAnalyzerJob(run5Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob5Id, packages = setOf(pkg1))
                val advisorJob5Id = dbExtension.fixtures.createAdvisorJob(run5Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob5Id,
                    status = JobStatus.FAILED.asPresent2()
                )

                val run6Id = dbExtension.fixtures.createOrtRun(repo22Id).id
                val analyzerJob6Id = dbExtension.fixtures.createAnalyzerJob(run6Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob6Id, packages = setOf(pkg1, pkg2, pkg3))
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
                response shouldHaveBody PagedSearchResponse(
                    listOf(
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability1.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run1Id, run3Id, run4Id, run6Id),
                            repositoriesCount = 4
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability2.mapToApi(),
                            identifier = identifier2.mapToApi(),
                            purl = pkg2.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run3Id, run6Id),
                            repositoriesCount = 2
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability2.mapToApi(),
                            identifier = identifier3.mapToApi(),
                            purl = pkg3.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run6Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = run1Vulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.LOW,
                            ortRunIds = listOf(run1Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = run4Vulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            purl = pkg1.purl,
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
                    ),
                    VulnerabilityForRunsFilters()
                )
            }
        }

        "allow filtering by identifier and rating" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val prod1Id = dbExtension.fixtures.createProduct(organizationId = orgId).id
                val prod2Id = dbExtension.fixtures.createProduct("Prod2", organizationId = orgId).id

                val repo1Id = dbExtension.fixtures.createRepository(productId = prod1Id).id
                val repo2Id = dbExtension.fixtures.createRepository(productId = prod2Id).id

                val commonVulnerability1 = Vulnerability(
                    externalId = "CVE-2020-01232",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "High",
                            score = 6.2f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                val commonVulnerability2 = Vulnerability(
                    externalId = "CVE-2021-12346",
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

                val pkg1 = dbExtension.fixtures.generatePackage(
                    Identifier("Maven", "org.apache.logging.log4j", "log4j-core", "2.14.0")
                )
                val pkg2 = dbExtension.fixtures.generatePackage(
                    Identifier("Maven", "org.apache.logging.log4j", "log4j-api", "2.14.0")
                )

                val run1Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val analyzerJob1Id = dbExtension.fixtures.createAnalyzerJob(run1Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob1Id, packages = setOf(pkg1, pkg2))
                val advisorJob1Id = dbExtension.fixtures.createAdvisorJob(run1Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob1Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                val run1Vulnerability = Vulnerability(
                    externalId = "CVE-2022-23458",
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
                        pkg1.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability1, run1Vulnerability))
                        ),
                        pkg2.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability2))
                        )
                    )
                )

                val run2Id = dbExtension.fixtures.createOrtRun(repo2Id).id
                val analyzerJob2Id = dbExtension.fixtures.createAnalyzerJob(run2Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob2Id, packages = setOf(pkg1, pkg2))
                val advisorJob2Id = dbExtension.fixtures.createAdvisorJob(run2Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob2Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                val run2Vulnerability = Vulnerability(
                    externalId = "CVE-2023-34560",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "CRITICAL",
                            score = 9.9f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob2Id,
                    mapOf(
                        pkg1.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability1, run2Vulnerability))
                        ),
                        pkg2.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability2))
                        )
                    )
                )

                val response =
                    superuserClient.get(
                        "/api/v1/organizations/$orgId/vulnerabilities?identifier=core&rating=high,critical"
                    )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedSearchResponse(
                    listOf(
                        OrganizationVulnerability(
                            vulnerability = run2Vulnerability.mapToApi(),
                            identifier = pkg1.identifier.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.CRITICAL,
                            ortRunIds = listOf(run2Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability1.mapToApi(),
                            identifier = pkg1.identifier.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.HIGH,
                            ortRunIds = listOf(run1Id, run2Id),
                            repositoriesCount = 2
                        ),
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(
                            SortProperty("rating", SortDirection.DESCENDING)
                        )
                    ),
                    VulnerabilityForRunsFilters(
                        rating = FilterOperatorAndValue(
                            ComparisonOperator.IN,
                            setOf(VulnerabilityRating.HIGH, VulnerabilityRating.CRITICAL)
                        ),
                        identifier = FilterOperatorAndValue(
                            ComparisonOperator.ILIKE,
                            "core"
                        )
                    )
                )
            }
        }

        "allow filtering by purl and external ID" {
            integrationTestApplication {
                val orgId = createOrganization().id

                val prod1Id = dbExtension.fixtures.createProduct(organizationId = orgId).id
                val prod2Id = dbExtension.fixtures.createProduct("Prod2", organizationId = orgId).id

                val repo1Id = dbExtension.fixtures.createRepository(productId = prod1Id).id
                val repo2Id = dbExtension.fixtures.createRepository(productId = prod2Id).id

                val commonVulnerability1 = Vulnerability(
                    externalId = "CVE-2020-0123",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "High",
                            score = 6.2f,
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

                val pkg1 = dbExtension.fixtures.generatePackage(
                    Identifier(
                        "Maven",
                        "org.apache.logging.log4j",
                        "log4j-core",
                        "2.14.0"
                    )
                )
                val pkg2 = dbExtension.fixtures.generatePackage(
                    Identifier(
                        "Maven",
                        "org.apache.logging.log4j",
                        "log4j-api",
                        "2.14.0"
                    )
                )

                val run1Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val analyzerJob1Id = dbExtension.fixtures.createAnalyzerJob(run1Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob1Id, packages = setOf(pkg1, pkg2))
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
                        pkg1.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability1))
                        ),
                        pkg2.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability2, run1Vulnerability))
                        )
                    )
                )

                val run2Id = dbExtension.fixtures.createOrtRun(repo2Id).id
                val analyzerJob2Id = dbExtension.fixtures.createAnalyzerJob(run2Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob2Id, packages = setOf(pkg1, pkg2))
                val advisorJob2Id = dbExtension.fixtures.createAdvisorJob(run2Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob2Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                val run2Vulnerability = Vulnerability(
                    externalId = "CVE-2021-3456",
                    summary = "A vulnerability",
                    description = "A description",
                    references = listOf(
                        VulnerabilityReference(
                            url = "https://example.com",
                            scoringSystem = "CVSS",
                            severity = "CRITICAL",
                            score = 9.9f,
                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                        )
                    )
                )
                dbExtension.fixtures.createAdvisorRun(
                    advisorJob2Id,
                    mapOf(
                        pkg1.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability1))
                        ),
                        pkg2.identifier to listOf(
                            generateAdvisorResult(listOf(commonVulnerability2, run2Vulnerability))
                        )
                    )
                )

                val response =
                    superuserClient.get(
                        "/api/v1/organizations/$orgId/vulnerabilities?purl=api&externalId=2021"
                    )

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedSearchResponse(
                    listOf(
                        OrganizationVulnerability(
                            vulnerability = run2Vulnerability.mapToApi(),
                            identifier = pkg2.identifier.mapToApi(),
                            purl = pkg2.purl,
                            rating = VulnerabilityRating.CRITICAL,
                            ortRunIds = listOf(run2Id),
                            repositoriesCount = 1
                        ),
                        OrganizationVulnerability(
                            vulnerability = commonVulnerability2.mapToApi(),
                            identifier = pkg2.identifier.mapToApi(),
                            purl = pkg2.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run1Id, run2Id),
                            repositoriesCount = 2
                        ),
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(
                            SortProperty("rating", SortDirection.DESCENDING)
                        )
                    ),
                    VulnerabilityForRunsFilters(
                        purl = FilterOperatorAndValue(
                            ComparisonOperator.ILIKE,
                            "api"
                        ),
                        externalId = FilterOperatorAndValue(
                            ComparisonOperator.ILIKE,
                            "2021"
                        )
                    )
                )
            }
        }

        "require OrganizationPermission.READ" {
            val createdOrganization = createOrganization()
            requestShouldRequireRole(OrganizationRole.READER, createdOrganization.hierarchyId) {
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
            requestShouldRequireRole(OrganizationRole.READER, createdOrganization.hierarchyId) {
                get("/api/v1/organizations/${createdOrganization.id}/statistics/runs")
            }
        }
    }

    "GET /organizations/{productId}/users" should {
        "return list of users that have rights for organization" {
            integrationTestApplication {
                val orgId = createOrganization().id
                val orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))

                authorizationService.assignRole(
                    TEST_USER.username.value,
                    OrganizationRole.READER,
                    orgHierarchyId
                )
                authorizationService.assignRole(
                    SUPERUSER.username.value,
                    OrganizationRole.ADMIN,
                    orgHierarchyId
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

        "handle unknown users gracefully" {
            integrationTestApplication {
                val orgId = createOrganization().id
                val orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))

                authorizationService.assignRole(
                    TEST_USER.username.value,
                    OrganizationRole.READER,
                    orgHierarchyId
                )
                authorizationService.assignRole(
                    "non-existing-user",
                    OrganizationRole.ADMIN,
                    orgHierarchyId
                )

                val response = superuserClient.get("/api/v1/organizations/$orgId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        ApiUserWithGroups(
                            ApiUser(TEST_USER.username.value, TEST_USER.firstName, TEST_USER.lastName, TEST_USER.email),
                            listOf(ApiUserGroup.READERS)
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 1,
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

        "filter out users with inherited roles" {
            integrationTestApplication {
                val orgId = createOrganization().id
                val orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))
                val productId = dbExtension.fixtures.createProduct(organizationId = orgId).id
                val repositoryId = dbExtension.fixtures.createRepository(productId = productId).id

                authorizationService.assignRole(
                    SUPERUSER.username.value,
                    OrganizationRole.WRITER,
                    orgHierarchyId
                )
                authorizationService.assignRole(
                    TEST_USER.username.value,
                    RepositoryRole.READER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(orgId),
                        ProductId(productId),
                        RepositoryId(repositoryId)
                    )
                )

                val response = superuserClient.get("/api/v1/organizations/$orgId/users")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        ApiUserWithGroups(
                            ApiUser(SUPERUSER.username.value, SUPERUSER.firstName, SUPERUSER.lastName, SUPERUSER.email),
                            listOf(ApiUserGroup.WRITERS)
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 1,
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
            val createdOrg = createOrganization()

            requestShouldRequireRole(OrganizationRole.READER, createdOrg.hierarchyId) {
                get("/api/v1/organizations/${createdOrg.id}/users")
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

/**
 * Return a [CompoundHierarchyId] for this [Organization].
 */
private val org.eclipse.apoapsis.ortserver.model.Organization.hierarchyId: CompoundHierarchyId
        get() = CompoundHierarchyId.forOrganization(OrganizationId(id))
