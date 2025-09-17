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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.ProductVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity as ApiSeverity
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups as ApiUserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.clients.keycloak.GroupName
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.addUserRole
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductRole as ApiProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.mapToModel
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.roles.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.roles.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.core.SUPERUSER
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ProductId
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
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.apimodel.valueOrThrow
import org.eclipse.apoapsis.ortserver.shared.ktorutils.shouldHaveBody
import org.eclipse.apoapsis.ortserver.utils.test.Integration

@Suppress("LargeClass")
class ProductsRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    lateinit var authorizationService: AuthorizationService
    lateinit var organizationService: OrganizationService
    lateinit var pluginService: PluginService
    lateinit var productService: ProductService

    var orgId = -1L

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

        pluginService = PluginService(dbExtension.db)

        productService = ProductService(
            dbExtension.db,
            dbExtension.fixtures.productRepository,
            dbExtension.fixtures.repositoryRepository,
            dbExtension.fixtures.ortRunRepository,
            authorizationService
        )

        orgId = organizationService.createOrganization(name = "name", description = "description").id
    }

    val productName = "name"
    val productDescription = "description"

    suspend fun createProduct(
        name: String = productName,
        description: String = productDescription,
        organizationId: Long = orgId
    ) = organizationService.createProduct(name, description, organizationId)

    "GET /products/{productId}" should {
        "return a single product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val response = superuserClient.get("/api/v1/products/${createdProduct.id}")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Product(createdProduct.id, orgId, productName, productDescription)
            }
        }

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}")
            }
        }
    }

    "PATCH /products/{id}" should {
        "update a product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val updatedProduct = UpdateProduct(
                    "updatedProduct".asPresent(),
                    "updateDescription".asPresent()
                )
                val response = superuserClient.patch("/api/v1/products/${createdProduct.id}") {
                    setBody(updatedProduct)
                }

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody Product(
                    createdProduct.id,
                    orgId,
                    updatedProduct.name.valueOrThrow,
                    updatedProduct.description.valueOrThrow
                )
            }
        }

        "require ProductPermission.WRITE" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.WRITE.roleName(createdProduct.id)) {
                val updatedProduct = UpdateProduct("updatedName".asPresent(), "updatedDescription".asPresent())
                patch("/api/v1/products/${createdProduct.id}") { setBody(updatedProduct) }
            }
        }

        "respond with 'Bad Request' if the product's name is invalid" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val updatedProduct = UpdateProduct(
                    " updatedProduct! ".asPresent(),
                    "updateDescription".asPresent()
                )
                val response = superuserClient.patch("/api/v1/products/${createdProduct.id}") {
                    setBody(updatedProduct)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for UpdateProduct"
            }
        }
    }

    "DELETE /products/{id}" should {
        "delete a product" {
            integrationTestApplication {
                val createdProduct = createProduct()

                superuserClient.delete("/api/v1/products/${createdProduct.id}") shouldHaveStatus
                    HttpStatusCode.NoContent

                organizationService.listProductsForOrganization(orgId).data shouldBe emptyList()
            }
        }

        "delete Keycloak roles and groups" {
            integrationTestApplication {
                val createdProduct = createProduct()

                superuserClient.delete("/api/v1/products/${createdProduct.id}")

                keycloakClient.getRoles().map { it.name.value } shouldNot containAnyOf(
                    ProductPermission.getRolesForProduct(createdProduct.id) +
                        ProductRole.getRolesForProduct(createdProduct.id)
                )

                keycloakClient.getGroups().map { it.name.value } shouldNot containAnyOf(
                    ProductRole.getGroupsForProduct(createdProduct.id)
                )
            }
        }

        "require ProductPermission.DELETE" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.DELETE.roleName(createdProduct.id), HttpStatusCode.NoContent) {
                delete("/api/v1/products/${createdProduct.id}")
            }
        }
    }

    "GET /products/{id}/repositories" should {
        "return all repositories of an organization" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"
                val description = "description"

                val createdRepository1 = productService.createRepository(
                    type = type,
                    url = url1,
                    productId = createdProduct.id,
                    description = description
                )
                val createdRepository2 = productService.createRepository(
                    type = type,
                    url = url2,
                    productId = createdProduct.id,
                    description = description
                )

                val response = superuserClient.get("/api/v1/products/${createdProduct.id}/repositories")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        Repository(createdRepository1.id, orgId, createdProduct.id, type.mapToApi(), url1, description),
                        Repository(createdRepository2.id, orgId, createdProduct.id, type.mapToApi(), url2, description)
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("url", SortDirection.ASCENDING))
                    )
                )
            }
        }

        "support query parameters" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val type = RepositoryType.GIT
                val url1 = "https://example.com/repo1.git"
                val url2 = "https://example.com/repo2.git"
                val description = "description"

                productService.createRepository(
                    type = type,
                    url = url1,
                    productId = createdProduct.id,
                    description = description
                )
                val createdRepository2 = productService.createRepository(
                    type = type,
                    url = url2,
                    productId = createdProduct.id,
                    description = description
                )

                val response =
                    superuserClient.get("/api/v1/products/${createdProduct.id}/repositories?sort=-url&limit=1")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        Repository(
                            createdRepository2.id,
                            orgId,
                            createdProduct.id,
                            type.mapToApi(),
                            url2,
                            description
                        )
                    ),
                    PagingData(
                        limit = 1,
                        offset = 0,
                        totalCount = 2,
                        sortProperties = listOf(SortProperty("url", SortDirection.DESCENDING))
                    )
                )
            }
        }

        "require ProductPermission.READ_REPOSITORIES" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ_REPOSITORIES.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/repositories")
            }
        }
    }

    "POST /products/{id}/repositories" should {
        "create a repository" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git", "description")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.Created
                response shouldHaveBody
                    Repository(1, orgId, createdProduct.id, repository.type, repository.url, repository.description)
            }
        }

        "respond with 'Bad Request' if the repository's URL is malformed" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://git hub.com/org/repo.git")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateRepository"
            }
        }

        "respond with 'Bad Request' if the repository's URL contains userinfo" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://user:password@github.com")
                val response = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Request validation has failed."
                body.cause shouldContain "Validation failed for CreateRepository"
            }
        }

        "create Keycloak roles and groups" {
            integrationTestApplication {
                val createdProduct = createProduct()

                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                val createdRepository = superuserClient.post("/api/v1/products/${createdProduct.id}/repositories") {
                    setBody(repository)
                }.body<Repository>()

                keycloakClient.getRoles().map { it.name.value } should containAll(
                    RepositoryPermission.getRolesForRepository(createdRepository.id) +
                        RepositoryRole.getRolesForRepository(createdRepository.id)
                )

                keycloakClient.getGroups().map { it.name.value } should containAll(
                    RepositoryRole.getGroupsForRepository(createdRepository.id)
                )
            }
        }

        "require ProductPermission.CREATE_REPOSITORY" {
            val createdProduct = createProduct()
            requestShouldRequireRole(
                ProductPermission.CREATE_REPOSITORY.roleName(createdProduct.id),
                HttpStatusCode.Created
            ) {
                val repository = CreateRepository(ApiRepositoryType.GIT, "https://example.com/repo.git")
                post("/api/v1/products/${createdProduct.id}/repositories") { setBody(repository) }
            }
        }
    }

    "PUT/DELETE /products/{productId}/roles/{role}" should {
        forAll(
            row(HttpMethod.Put),
            row(HttpMethod.Delete)
        ) { method ->
            "require ProductPermission.MANAGE_GROUPS for method '${method.value}'" {
                val createdProd = createProduct()
                val user = Username(TEST_USER.username.value)
                requestShouldRequireRole(
                    ProductPermission.MANAGE_GROUPS.roleName(createdProd.id),
                    HttpStatusCode.NoContent
                ) {
                    when (method) {
                        HttpMethod.Put -> put("/api/v1/products/${createdProd.id}/roles/READER") {
                            setBody(user)
                        }
                        HttpMethod.Delete -> delete(
                            "/api/v1/products/${createdProd.id}/roles/READER?username=${user.username}"
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
                    val createdProd = createProduct()
                    val user = Username("non-existing-username")
                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/products/${createdProd.id}/roles/READER"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/products/${createdProd.id}/roles/READER?username=${user.username}"
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
            "respond with 'NotFound' if the product does not exist for method '${method.value}'" {
                integrationTestApplication {
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/products/999999/roles/READER"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/products/999999/roles/READER?username=${user.username}"
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
                    val createdProd = createProduct()
                    val org = CreateOrganization(name = "name", description = "description") // Wrong request body

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/products/${createdProd.id}/roles/READER"
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
                    val createdProd = createProduct()
                    val user = Username(TEST_USER.username.value)

                    val response = when (method) {
                        HttpMethod.Put -> superuserClient.put(
                            "/api/v1/products/${createdProd.id}/roles/non-existing-role"
                        ) {
                            setBody(user)
                        }
                        HttpMethod.Delete -> superuserClient.delete(
                            "/api/v1/products/${createdProd.id}/roles/non-existing-role?username=${user.username}"
                        )
                        else -> error("Unsupported method: $method")
                    }

                    response shouldHaveStatus HttpStatusCode.BadRequest
                }
            }
        }
    }

    "PUT /products/{productId}/roles/{role}" should {
        enumValues<ApiProductRole>().forAll { role ->
            "assign the '$role' role to the user" {
                integrationTestApplication {
                    val createdProd = createProduct()
                    val user = Username(TEST_USER.username.value)

                    val response = superuserClient.put(
                        "/api/v1/products/${createdProd.id}/roles/${role.name}"
                    ) {
                        setBody(user)
                    }

                    response shouldHaveStatus HttpStatusCode.NoContent

                    val groupName = role.mapToModel().groupName(createdProd.id)
                    val group = keycloakClient.getGroup(GroupName(groupName))
                    group.shouldNotBeNull()

                    val members = keycloakClient.getGroupMembers(group.name)
                    members shouldHaveSize 1
                    members.map { it.username } shouldContain TEST_USER.username
                }
            }
        }
    }

    "DELETE /products/{productId}/roles/{role}" should {
        enumValues<ApiProductRole>().forAll { role ->
            "remove the '$role' role from the user" {
                integrationTestApplication {
                    val createdProd = createProduct()
                    val user = Username(TEST_USER.username.value)

                    authorizationService.addUserRole(user.username, ProductId(createdProd.id), role.mapToModel())

                    // Check pre-condition
                    val groupName = role.mapToModel().groupName(createdProd.id)
                    val groupBefore = keycloakClient.getGroup(GroupName(groupName))
                    val membersBefore = keycloakClient.getGroupMembers(groupBefore.name)
                    membersBefore shouldHaveSize 1
                    membersBefore.map { it.username } shouldContain TEST_USER.username

                    val response = superuserClient.delete(
                        "/api/v1/products/${createdProd.id}/roles/${role.name}?username=${user.username}"
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

    "GET /products/{productId}/vulnerabilities" should {
        "return vulnerabilities across repositories in the product found in latest successful advisor jobs" {
            integrationTestApplication {
                val productId = createProduct().id
                val description = "description"
                val repository1Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.org/repo.git",
                    productId = productId,
                    description = description
                ).id
                val repository2Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.org/repo2.git",
                    productId = productId,
                    description = description
                ).id

                val commonVulnerability = Vulnerability(
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

                val pkg1 = dbExtension.fixtures.generatePackage(identifier1)
                val pkg2 = dbExtension.fixtures.generatePackage(identifier2)

                val run1Id = dbExtension.fixtures.createOrtRun(repository1Id).id

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
                                    commonVulnerability,
                                    run1Vulnerability
                                )
                            )
                        )
                    )
                )

                val run2Id = dbExtension.fixtures.createOrtRun(repository1Id).id

                val analyzerJob2Id = dbExtension.fixtures.createAnalyzerJob(run2Id).id
                dbExtension.fixtures.createAnalyzerRun(analyzerJob2Id, packages = setOf(pkg1, pkg2))

                val advisorJob2Id = dbExtension.fixtures.createAdvisorJob(run2Id).id
                dbExtension.fixtures.advisorJobRepository.update(
                    advisorJob2Id,
                    status = JobStatus.FAILED.asPresent2()
                )

                val run3Id = dbExtension.fixtures.createOrtRun(repository2Id).id

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
                        identifier1 to listOf(generateAdvisorResult(listOf(commonVulnerability))),
                        identifier2 to listOf(generateAdvisorResult(listOf(commonVulnerability)))
                    )
                )

                val response =
                    superuserClient.get("/api/v1/products/$productId/vulnerabilities?sort=-rating,-repositoriesCount")

                response shouldHaveStatus HttpStatusCode.OK
                response shouldHaveBody PagedResponse(
                    listOf(
                        ProductVulnerability(
                            vulnerability = commonVulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run1Id, run3Id),
                            repositoriesCount = 2
                        ),
                        ProductVulnerability(
                            vulnerability = commonVulnerability.mapToApi(),
                            identifier = identifier2.mapToApi(),
                            purl = pkg2.purl,
                            rating = VulnerabilityRating.MEDIUM,
                            ortRunIds = listOf(run3Id),
                            repositoriesCount = 1
                        ),
                        ProductVulnerability(
                            vulnerability = run1Vulnerability.mapToApi(),
                            identifier = identifier1.mapToApi(),
                            purl = pkg1.purl,
                            rating = VulnerabilityRating.LOW,
                            ortRunIds = listOf(run1Id),
                            repositoriesCount = 1
                        )
                    ),
                    PagingData(
                        limit = DEFAULT_LIMIT,
                        offset = 0,
                        totalCount = 3,
                        sortProperties = listOf(
                            SortProperty("rating", SortDirection.DESCENDING),
                            SortProperty("repositoriesCount", SortDirection.DESCENDING)
                        )
                    )
                )
            }
        }

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/vulnerabilities")
            }
        }
    }

    "GET /products/{productId}/statistics/runs" should {
        "return statistics for runs in repositories of a product" {
            integrationTestApplication {
                val prodId = createProduct().id
                val repo1Id = dbExtension.fixtures.createRepository(productId = prodId).id
                val repo2Id = dbExtension.fixtures.createRepository(
                    url = "https://example.com/repo2.git",
                    productId = prodId
                ).id

                val commonIssue = Issue(
                    timestamp = Clock.System.now(),
                    source = "Analyzer",
                    message = "Issue 1",
                    severity = Severity.ERROR,
                    affectedPath = "path"
                )

                val commonPackage = generatePackage(Identifier("Maven", "com.example", "example", "1.0"))

                val commonVulnerability = Identifier("Maven", "com.example", "example", "1.0") to
                    listOf(
                        generateAdvisorResult(
                            listOf(
                                Vulnerability(
                                    externalId = "CVE-2023-5234",
                                    summary = "A vulnerability",
                                    description = "A description",
                                    references = listOf(
                                        VulnerabilityReference(
                                            url = "https://example.com",
                                            scoringSystem = "CVSS",
                                            severity = "CRITICAL",
                                            score = 1.1f,
                                            vector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                                        )
                                    )
                                )
                            )
                        )
                    )

                val commonRuleViolation = RuleViolation(
                    "rule",
                    null,
                    null,
                    null,
                    Severity.ERROR,
                    "message",
                    "how-to-fix"
                )

                // Successful evaluator run for repository 1
                val repo1Run1Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val evJob1Id = dbExtension.fixtures.createEvaluatorJob(repo1Run1Id).id
                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evJob1Id,
                    startTime = Clock.System.now(),
                    endTime = Clock.System.now(),
                    violations = listOf(
                        commonRuleViolation,
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
                    evJob1Id,
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent2()
                )

                // Successful advisor run for repository 1
                val repo1Run2Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val advJob1Id = dbExtension.fixtures.createAdvisorJob(repo1Run2Id).id
                dbExtension.fixtures.createAdvisorRun(
                    advJob1Id,
                    mapOf(
                        Identifier("NPM", "com.example", "example2", "1.0") to
                            listOf(
                                generateAdvisorResult(
                                    listOf(
                                        Vulnerability(
                                            externalId = "CVE-2021-1234",
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
                                    )
                                )
                            ),
                        commonVulnerability
                    )
                )
                dbExtension.fixtures.advisorJobRepository.update(advJob1Id, status = JobStatus.FINISHED.asPresent2())

                // Successful analyzer run for repository 1
                val repo1Run3Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val anJob1Id = dbExtension.fixtures.createAnalyzerJob(repo1Run3Id).id
                dbExtension.fixtures.createAnalyzerRun(
                    analyzerJobId = anJob1Id,
                    packages = setOf(
                        commonPackage,
                        generatePackage(Identifier("NPM", "com.example", "example2", "1.0"))
                    )
                )
                dbExtension.fixtures.analyzerJobRepository.update(
                    anJob1Id,
                    status = JobStatus.FINISHED.asPresent2()
                )
                // Final analyzer run for repository 1
                val repo1Run4Id = dbExtension.fixtures.createOrtRun(repo1Id).id
                val anJob2Id = dbExtension.fixtures.createAnalyzerJob(repo1Run4Id).id
                dbExtension.fixtures.analyzerJobRepository.update(
                    anJob2Id,
                    status = JobStatus.FAILED.asPresent2()
                )
                dbExtension.fixtures.ortRunRepository.update(
                    repo1Run4Id,
                    issues = listOf(
                        commonIssue,
                        Issue(
                            timestamp = Clock.System.now(),
                            source = "Advisor",
                            message = "Issue 1",
                            severity = Severity.WARNING,
                            affectedPath = "path",
                        ),
                    ).asPresent2()
                )

                val repo2RunId = dbExtension.fixtures.createOrtRun(repo2Id).id

                val anJob3Id = dbExtension.fixtures.createAnalyzerJob(repo2RunId).id
                dbExtension.fixtures.createAnalyzerRun(
                    anJob3Id,
                    packages = setOf(
                        commonPackage,
                        generatePackage(Identifier("PyPI", "", "example", "1.0"))
                    ),
                    issues = listOf(
                        commonIssue,
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
                    anJob3Id,
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent2()
                )

                val advJob2Id = dbExtension.fixtures.createAdvisorJob(repo2RunId).id
                dbExtension.fixtures.createAdvisorRun(
                    advJob2Id,
                    mapOf(
                        Identifier("PyPI", "", "example", "1.0") to
                            listOf(
                                generateAdvisorResult(
                                    listOf(
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
                        commonVulnerability
                    )
                )
                dbExtension.fixtures.advisorJobRepository.update(advJob2Id, status = JobStatus.FINISHED.asPresent2())

                val evJob2Id = dbExtension.fixtures.createEvaluatorJob(repo2RunId).id
                dbExtension.fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evJob2Id,
                    startTime = Clock.System.now(),
                    endTime = Clock.System.now(),
                    violations = listOf(
                        commonRuleViolation,
                        RuleViolation(
                            "rule2",
                            null,
                            null,
                            null,
                            Severity.ERROR,
                            "message",
                            "how-to-fix"
                        )
                    )
                )
                dbExtension.fixtures.evaluatorJobRepository.update(
                    evJob2Id,
                    status = JobStatus.FINISHED_WITH_ISSUES.asPresent2()
                )

                val response = superuserClient.get("/api/v1/products/$prodId/statistics/runs")

                response shouldHaveStatus HttpStatusCode.OK

                val statistics = response.body<OrtRunStatistics>()

                with(statistics) {
                    issuesCount shouldBe 4
                    issuesCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 0,
                            ApiSeverity.WARNING to 2,
                            ApiSeverity.ERROR to 2
                        )
                    )
                    packagesCount shouldBe 3
                    ecosystems?.shouldContainExactlyInAnyOrder(
                        listOf(
                            EcosystemStats("NPM", 1),
                            EcosystemStats("Maven", 1),
                            EcosystemStats("PyPI", 1)
                        )
                    )
                    vulnerabilitiesCount shouldBe 3
                    vulnerabilitiesCountByRating?.shouldContainExactly(
                        mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 1,
                            VulnerabilityRating.MEDIUM to 1,
                            VulnerabilityRating.HIGH to 0,
                            VulnerabilityRating.CRITICAL to 1
                        )
                    )
                    ruleViolationsCount shouldBe 3
                    ruleViolationsCountBySeverity?.shouldContainExactly(
                        mapOf(
                            ApiSeverity.HINT to 1,
                            ApiSeverity.WARNING to 0,
                            ApiSeverity.ERROR to 2
                        )
                    )
                }
            }
        }

        "return nulls for counts if no valid runs are found" {
            integrationTestApplication {
                val prodId = createProduct().id

                val response = superuserClient.get("/api/v1/products/$prodId/statistics/runs")

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

        "require ProductPermission.READ" {
            val createdProduct = createProduct()
            requestShouldRequireRole(ProductPermission.READ.roleName(createdProduct.id)) {
                get("/api/v1/products/${createdProduct.id}/statistics/runs")
            }
        }
    }

    "GET /products/{productId}/users" should {
        "return list of users that have rights for product" {
            integrationTestApplication {
                val productId = createProduct().id

                authorizationService.addUserRole(TEST_USER.username.value, ProductId(productId), ProductRole.READER)
                authorizationService.addUserRole(SUPERUSER.username.value, ProductId(productId), ProductRole.WRITER)
                authorizationService.addUserRole(SUPERUSER.username.value, ProductId(productId), ProductRole.ADMIN)

                val response = superuserClient.get("/api/v1/products/$productId/users")

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

        "return empty list if no user has rights for product" {
            integrationTestApplication {
                val productId = createProduct().id
                val response = superuserClient.get("/api/v1/products/$productId/users")

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
                val productId = createProduct().id

                val response = superuserClient.get("/api/v1/products/$productId/users?sort=username,firstName")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Exactly one sort field must be defined."
            }
        }

        "respond with 'Bad Request' if there is no sort field" {
            integrationTestApplication {
                val productId = createProduct().id

                val response = superuserClient.get("/api/v1/products/$productId/users?sort=")

                response shouldHaveStatus HttpStatusCode.BadRequest

                val body = response.body<ErrorResponse>()
                body.message shouldBe "Invalid query parameters."
                body.cause shouldBe "Empty sort field."
            }
        }

        "require ProductPermission.READ" {
            val productId = createProduct().id

            requestShouldRequireRole(ProductPermission.READ.roleName(productId)) {
                get("/api/v1/products/$productId/users")
            }
        }
    }

    "POST /products/{productId}/runs" should {
        "trigger ORT runs for repositories in the product" {
            integrationTestApplication {
                val productId = createProduct().id
                val description = "description"
                val repository1Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo1.git",
                    productId = productId,
                    description = description
                ).id
                val repository2Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo2.git",
                    productId = productId,
                    description = description
                ).id

                val createOrtRunAll = CreateOrtRun(
                    revision = "main",
                    path = "",
                    jobConfigs = JobConfigurations(),
                    jobConfigContext = null,
                    labels = emptyMap(),
                    environmentConfigPath = null,
                    repositoryIds = emptyList()
                )

                val responseAll = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createOrtRunAll)
                }

                responseAll shouldHaveStatus HttpStatusCode.Created

                val createdRunsAll = responseAll.body<List<OrtRun>>()
                createdRunsAll shouldHaveSize 2

                val repositoryIdsAll = createdRunsAll.map { run ->
                    dbExtension.fixtures.ortRunRepository.get(run.id)?.repositoryId
                }
                repositoryIdsAll shouldContainExactlyInAnyOrder listOf(repository1Id, repository2Id)

                val createOrtRunSpecific = CreateOrtRun(
                    revision = "main",
                    path = "",
                    jobConfigs = JobConfigurations(),
                    jobConfigContext = null,
                    labels = emptyMap(),
                    environmentConfigPath = null,
                    repositoryIds = listOf(repository1Id)
                )

                val responseSpecific = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createOrtRunSpecific)
                }

                responseSpecific shouldHaveStatus HttpStatusCode.Created

                val createdRunsSpecific = responseSpecific.body<List<OrtRun>>()
                createdRunsSpecific shouldHaveSize 1

                val repositoryIdsSpecific = createdRunsSpecific.map { run ->
                    dbExtension.fixtures.ortRunRepository.get(run.id)?.repositoryId
                }
                repositoryIdsSpecific shouldContainExactlyInAnyOrder listOf(repository1Id)
            }
        }

        "respond with 'BadRequest' if not installed plugins are used" {
            integrationTestApplication {
                val productId = createProduct().id

                val advisorPluginId = "notInstalledAdvisor"
                val packageConfigurationProviderPluginId = "notInstalledPackageConfigurationProvider"
                val packageCurationProviderPluginId = "notInstalledPackageCurationProvider"
                val packageManagerPluginId = "notInstalledPackageManager"
                val reporterPluginId = "notInstalledReporter"
                val scannerPluginId = "notInstalledScanner"

                // Create a run with not installed plugins.
                val createRun = CreateOrtRun(
                    revision = "main",
                    jobConfigs = JobConfigurations(
                        analyzer = AnalyzerJobConfiguration(
                            enabledPackageManagers = listOf(packageManagerPluginId),
                            packageCurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageCurationProviderPluginId)
                            )
                        ),
                        advisor = AdvisorJobConfiguration(advisors = listOf(advisorPluginId)),
                        scanner = ScannerJobConfiguration(scanners = listOf(scannerPluginId)),
                        evaluator = EvaluatorJobConfiguration(
                            packageConfigurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageConfigurationProviderPluginId)
                            )
                        ),
                        reporter = ReporterJobConfiguration(
                            formats = listOf(reporterPluginId)
                        )
                    )
                )

                val response = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createRun)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
                val errorMessage = response.bodyAsText()
                errorMessage shouldContain "not installed"
                errorMessage shouldContain advisorPluginId
                errorMessage shouldContain packageConfigurationProviderPluginId
                errorMessage shouldContain packageCurationProviderPluginId
                errorMessage shouldContain packageManagerPluginId
                errorMessage shouldContain reporterPluginId
                errorMessage shouldContain scannerPluginId
            }
        }

        "respond with 'BadRequest' if disabled plugins are used" {
            integrationTestApplication {
                val productId = createProduct().id

                val installedPlugins = pluginService.getPlugins()
                val advisorPluginId = installedPlugins.first { it.type == PluginType.ADVISOR }.id
                val packageConfigurationProviderPluginId =
                    installedPlugins.first { it.type == PluginType.PACKAGE_CONFIGURATION_PROVIDER }.id
                val packageCurationProviderPluginId =
                    installedPlugins.first { it.type == PluginType.PACKAGE_CURATION_PROVIDER }.id
                val packageManagerPluginId = installedPlugins.first { it.type == PluginType.PACKAGE_MANAGER }.id
                val reporterPluginId = installedPlugins.first { it.type == PluginType.REPORTER }.id
                val scannerPluginId = installedPlugins.first { it.type == PluginType.SCANNER }.id

                // Disable one plugin of each type.
                suspend fun disablePlugin(pluginType: PluginType, pluginId: String) =
                    superuserClient.post("/api/v1/admin/plugins/$pluginType/$pluginId/disable")

                disablePlugin(PluginType.ADVISOR, advisorPluginId)
                disablePlugin(PluginType.PACKAGE_CONFIGURATION_PROVIDER, packageConfigurationProviderPluginId)
                disablePlugin(PluginType.PACKAGE_CURATION_PROVIDER, packageCurationProviderPluginId)
                disablePlugin(PluginType.PACKAGE_MANAGER, packageManagerPluginId)
                disablePlugin(PluginType.REPORTER, reporterPluginId)
                disablePlugin(PluginType.SCANNER, scannerPluginId)

                // Create a run with disabled plugins.
                val createRun = CreateOrtRun(
                    revision = "main",
                    jobConfigs = JobConfigurations(
                        analyzer = AnalyzerJobConfiguration(
                            enabledPackageManagers = listOf(packageManagerPluginId),
                            packageCurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageCurationProviderPluginId)
                            )
                        ),
                        advisor = AdvisorJobConfiguration(advisors = listOf(advisorPluginId)),
                        scanner = ScannerJobConfiguration(scanners = listOf(scannerPluginId)),
                        evaluator = EvaluatorJobConfiguration(
                            packageConfigurationProviders = listOf(
                                ProviderPluginConfiguration(type = packageConfigurationProviderPluginId)
                            )
                        ),
                        reporter = ReporterJobConfiguration(
                            formats = listOf(reporterPluginId)
                        )
                    )
                )

                val response = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createRun)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
                val errorMessage = response.bodyAsText()
                errorMessage shouldContain "disabled"
                errorMessage shouldContain advisorPluginId
                errorMessage shouldContain packageConfigurationProviderPluginId
                errorMessage shouldContain packageCurationProviderPluginId
                errorMessage shouldContain packageManagerPluginId
                errorMessage shouldContain reporterPluginId
                errorMessage shouldContain scannerPluginId
            }
        }

        "respond with 'BadRequest' if final plugin template options are overwritten" {
            integrationTestApplication {
                val productId = createProduct().id

                val installedPlugins = pluginService.getPlugins()

                val pluginType = PluginType.ADVISOR
                val plugin = installedPlugins.first { it.type == pluginType && it.id == "VulnerableCode" }
                val pluginId = plugin.id
                val pluginOption = plugin.options.first { it.name == "serverUrl" }

                // Create a plugin template with a final option.
                superuserClient.post("/api/v1/admin/plugins/$pluginType/$pluginId/templates/test") {
                    setBody(
                        listOf(
                            PluginOptionTemplate(
                                option = pluginOption.name,
                                type = pluginOption.type,
                                value = "https://example.org",
                                isFinal = true
                            )
                        )
                    )
                }

                // Make the plugin template globally active.
                superuserClient.post("/api/v1/admin/plugins/$pluginType/$pluginId/templates/test/enableGlobal")

                // Create a run trying to overwrite the final option.
                val createRun = CreateOrtRun(
                    "main",
                    null,
                    JobConfigurations(
                        advisor = AdvisorJobConfiguration(
                            advisors = listOf(pluginId),
                            config = mapOf(
                                pluginId to PluginConfig(
                                    options = mapOf(pluginOption.name to "https://new.example.org"),
                                    secrets = emptyMap()
                                )
                            )
                        )
                    )
                )

                val response = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createRun)
                }

                response shouldHaveStatus HttpStatusCode.BadRequest
                response.bodyAsText() shouldContain "is set to a fixed value by the server administrators"
            }
        }

        val keepAliveJobConfigs = JobConfigurations().let {
            listOf(
                it.copy(analyzer = AnalyzerJobConfiguration(keepAliveWorker = true)),
                it.copy(advisor = AdvisorJobConfiguration(keepAliveWorker = true)),
                it.copy(evaluator = EvaluatorJobConfiguration(keepAliveWorker = true)),
                it.copy(notifier = NotifierJobConfiguration(keepAliveWorker = true)),
                it.copy(reporter = ReporterJobConfiguration(keepAliveWorker = true)),
                it.copy(scanner = ScannerJobConfiguration(keepAliveWorker = true))
            )
        }

        "respond with 'Forbidden' if 'keepAliveWorker' is set by a non super-user" {
            integrationTestApplication {
                val productId = createProduct().id

                keycloak.keycloakAdminClient.addUserRole(
                    TEST_USER.username.value,
                    ProductPermission.TRIGGER_ORT_RUN.roleName(productId)
                )

                keepAliveJobConfigs.forAll {
                    val response = testUserClient.post("/api/v1/products/$productId/runs") {
                        setBody(CreateOrtRun(revision = "main", jobConfigs = it))
                    }

                    response shouldHaveStatus HttpStatusCode.Forbidden
                    response.body<ErrorResponse>().message shouldContainIgnoringCase "keepAliveWorker"
                }
            }
        }

        "continue if 'keepAliveWorker' is set by a super-user" {
            integrationTestApplication {
                val productId = createProduct().id

                keepAliveJobConfigs.forAll {
                    val response = superuserClient.post("/api/v1/products/$productId/runs") {
                        setBody(CreateOrtRun(revision = "main", jobConfigs = it))
                    }

                    response shouldHaveStatus HttpStatusCode.Created
                }
            }
        }

        "create ORT runs when 'repositoryFailedIds' is set and the last runs of all repositories failed" {
            integrationTestApplication {
                val productId = createProduct().id
                val description = "description"
                val repository1Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo1.git",
                    productId = productId,
                    description = description
                ).id
                val repository2Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo2.git",
                    productId = productId,
                    description = description
                ).id
                val repository3Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo3.git",
                    productId = productId,
                    description = description
                ).id

                dbExtension.fixtures.createOrtRun(repository1Id)
                val ortRunRepo1Failed = dbExtension.fixtures.createOrtRun(repository1Id)
                dbExtension.fixtures.createOrtRun(repository2Id)
                dbExtension.fixtures.createOrtRun(repository3Id)
                val ortRunRepo3Failed = dbExtension.fixtures.createOrtRun(repository3Id)

                /*update status ort run */
                dbExtension.fixtures.ortRunRepository.update(
                    id = ortRunRepo1Failed.id,
                    status = OrtRunStatus.FAILED.asPresent2()
                )

                dbExtension.fixtures.ortRunRepository.update(
                    id = ortRunRepo3Failed.id,
                    status = OrtRunStatus.FAILED.asPresent2()
                )

                val createOrtRunFailed = CreateOrtRun(
                    revision = "main",
                    path = "",
                    jobConfigs = JobConfigurations(),
                    jobConfigContext = null,
                    labels = emptyMap(),
                    environmentConfigPath = null,
                    repositoryFailedIds = listOf(repository1Id, repository3Id)
                )

                val responseSpecific = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createOrtRunFailed)
                }

                responseSpecific shouldHaveStatus HttpStatusCode.Created

                val createdRunsSpecific = responseSpecific.body<List<OrtRun>>()
                createdRunsSpecific shouldHaveSize 2

                val repositoryIdsSpecific = createdRunsSpecific.map { it.repositoryId }

                repositoryIdsSpecific shouldContainExactlyInAnyOrder listOf(repository1Id, repository3Id)
            }
        }

        "return 'Conflict' when 'repositoryFailedIds' is used and the latest run for a repository did not fail" {
            integrationTestApplication {
                val productId = createProduct().id
                val description = "description"
                val repository1Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo1.git",
                    productId = productId,
                    description = description
                ).id
                val repository2Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo2.git",
                    productId = productId,
                    description = description
                ).id
                val repository3Id = productService.createRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/repo3.git",
                    productId = productId,
                    description = description
                ).id

                dbExtension.fixtures.createOrtRun(repository1Id)
                val ortRunRepo1Failed = dbExtension.fixtures.createOrtRun(repository1Id)
                dbExtension.fixtures.createOrtRun(repository2Id)
                dbExtension.fixtures.createOrtRun(repository3Id)
                val ortRunRepo3Failed = dbExtension.fixtures.createOrtRun(repository3Id)
                val ortRunRepo3Active = dbExtension.fixtures.createOrtRun(repository3Id)

                /*update status*/
                dbExtension.fixtures.ortRunRepository.update(
                    id = ortRunRepo1Failed.id,
                    status = OrtRunStatus.FAILED.asPresent2()
                )

                dbExtension.fixtures.ortRunRepository.update(
                    id = ortRunRepo3Failed.id,
                    status = OrtRunStatus.FAILED.asPresent2()
                )

                dbExtension.fixtures.ortRunRepository.update(
                    id = ortRunRepo3Active.id,
                    status = OrtRunStatus.ACTIVE.asPresent2()
                )

                val createOrtRunFailed = CreateOrtRun(
                    revision = "main",
                    path = "",
                    jobConfigs = JobConfigurations(),
                    jobConfigContext = null,
                    labels = emptyMap(),
                    environmentConfigPath = null,
                    repositoryFailedIds = listOf(repository1Id, repository3Id)
                )

                val response = superuserClient.post("/api/v1/products/$productId/runs") {
                    setBody(createOrtRunFailed)
                }

                response shouldHaveStatus HttpStatusCode.Conflict

                val body = response.body<ErrorResponse>()

                body.message shouldBe "The repositories do not have a latest ORT run " +
                        "with status FAILED for product $productId."
                body.cause shouldBe "Invalid repository IDs: $repository3Id"
            }
        }

        "require ProductPermission.TRIGGER_ORT_RUN" {
            val createdProduct = createProduct()
            requestShouldRequireRole(
                ProductPermission.TRIGGER_ORT_RUN.roleName(createdProduct.id),
                HttpStatusCode.Created
            ) {
                val createOrtRun = CreateOrtRun(
                    revision = "main",
                    path = "",
                    jobConfigs = JobConfigurations(),
                    jobConfigContext = null,
                    labels = emptyMap(),
                    environmentConfigPath = null,
                    repositoryIds = emptyList()
                )
                post("/api/v1/products/${createdProduct.id}/runs") {
                    setBody(createOrtRun)
                }
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

private fun generatePackage(identifier: Identifier) = Package(
    identifier = identifier,
    purl = "pkg:${identifier.type}/${identifier.namespace}/${identifier.name}@${identifier.version}",
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
