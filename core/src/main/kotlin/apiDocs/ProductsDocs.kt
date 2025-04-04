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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute

import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingData
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.ProductVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent

val getProductById: OpenApiRoute.() -> Unit = {
    operationId = "GetProductById"
    summary = "Get details of a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Product> {
                example("Create Product") {
                    value = Product(id = 1, organizationId = 2, name = "My Product", description = "Description")
                }
            }
        }
    }
}

val patchProductById: OpenApiRoute.() -> Unit = {
    operationId = "PatchProductById"
    summary = "Update a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<UpdateProduct> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
            example("Update Product") {
                value = UpdateProduct(name = "Update Product".asPresent(), description = "Updated product".asPresent())
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Product> {
                example("Update Product") {
                    value = Product(
                        id = 1,
                        organizationId = 2,
                        name = "My updated product",
                        description = "Updated product."
                    )
                }
            }
        }
    }
}

val deleteProductById: OpenApiRoute.() -> Unit = {
    operationId = "DeleteProductById"
    summary = "Delete a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val getRepositoriesByProductId: OpenApiRoute.() -> Unit = {
    operationId = "GetRepositoriesByProductId"
    summary = "Get all repositories of a product"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<Repository>> {
                example("Get repositories of a product") {
                    value = PagedResponse(
                        listOf(
                            Repository(
                                id = 1,
                                organizationId = 2,
                                productId = 3,
                                type = RepositoryType.GIT,
                                url = "https://example.com/first/repo.git"
                            ),
                            Repository(
                                id = 2,
                                organizationId = 3,
                                productId = 4,
                                type = RepositoryType.SUBVERSION,
                                url = "https://example.com/second/repo"
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("url", SortDirection.ASCENDING)),
                        )
                    )
                }
            }
        }
    }
}

val postRepository: OpenApiRoute.() -> Unit = {
    operationId = "CreateRepository"
    summary = "Create a repository for a product"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<CreateRepository> {
            example("Create repository") {
                value = CreateRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/namspace/repo.git",
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Repository> {
                example("Create repository") {
                    value = Repository(
                        id = 1,
                        organizationId = 2,
                        productId = 3,
                        type = RepositoryType.GIT,
                        url = "https://example.com/namspace/repo.git"
                    )
                }
            }
        }
    }
}

val getSecretsByProductId: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretsByProductId"
    summary = "Get all secrets of a specific product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The ID of a product."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<Secret>> {
                example("List all secrets of a product") {
                    value = PagedResponse(
                        listOf(
                            Secret(name = "token_npm_repo_1", description = "Access token for NPM Repo 1"),
                            Secret(name = "token_maven_repo_1", description = "Access token for Maven Repo 1")
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("name", SortDirection.ASCENDING)),
                        )
                    )
                }
            }
        }
    }
}

val getSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretByProductIdAndName"
    summary = "Get details of a secret of a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example("Get Secret") {
                    value = Secret(name = "token_npm_repo_1", description = "Access token for NPM Repo 1")
                }
            }
        }
    }
}

val postSecretForProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForProduct"
    summary = "Create a secret for a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<CreateSecret> {
            example("Create Secret") {
                value = CreateSecret(
                    name = "token_maven_repo_1",
                    value = "pr0d-s3cr3t-08_15",
                    description = "Access token for Maven Repo 1"
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Secret> {
                example("Create Secret") {
                    value = Secret(name = "token_maven_repo_1", description = "Access token for Maven Repo 1")
                }
            }
        }
    }
}

val patchSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByProductIdAndName"
    summary = "Update a secret of a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
        jsonBody<UpdateSecret> {
            example("Update Secret") {
                value = """
                    {
                        "value": "pr0d-s3cr3t-08_15",
                        "description": "New access token for Maven Repo 1"
                    }
                """.trimIndent()
            }
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example("Update Secret") {
                    value = Secret(name = "token_maven_repo_1", description = "New access token for Maven Repo 1")
                }
            }
        }
    }
}

val deleteSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteSecretByProductIdAndName"
    summary = "Delete a secret from a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val putUserToProductGroup: OpenApiRoute.() -> Unit = {
    operationId = "PutUserToGroupProduct"
    summary = "Add a user to a group on product level"
    tags = listOf("Groups")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("groupId") {
            description = "One of 'readers', 'writers' or 'admins'."
        }

        jsonBody<Username> {
            example("Add user identified by username 'abc123'.") {
                value = Username(username = "abc123")
            }
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully added the user to the group."
        }

        HttpStatusCode.NotFound to {
            description = "Product or group not found."
        }
    }
}

val deleteUserFromProductGroup: OpenApiRoute.() -> Unit = {
    operationId = "DeleteUserFromGroupProduct"
    summary = "Remove a user from a group on product level"
    tags = listOf("Groups")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<String>("groupId") {
            description = "One of 'readers', 'writers' or 'admins'."
        }

        jsonBody<Username> {
            example("Remove user identified by username 'abc123'.") {
                value = Username(username = "abc123")
            }
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully removed the user from the group."
        }

        HttpStatusCode.NotFound to {
            description = "Product or group not found."
        }
    }
}

val getVulnerabilitiesAcrossRepositoriesByProductId: OpenApiRoute.() -> Unit = {
    operationId = "GetVulnerabilitiesAcrossRepositoriesByProductId"
    summary = "Get vulnerabilities from a product"
    description = "Get the vulnerabilities from latest successful advisor runs across the repositories in a product."
    tags = listOf("Vulnerabilities")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            jsonBody<PagedResponse<ProductVulnerability>> {
                example("Get vulnerabilities for product") {
                    value = PagedResponse(
                        listOf(
                            ProductVulnerability(
                                vulnerability = Vulnerability(
                                    externalId = "CVE-2021-1234",
                                    summary = "A vulnerability",
                                    description = "A description",
                                    references = listOf(
                                        VulnerabilityReference(
                                            "https://example.com",
                                            "CVSS3",
                                            "HIGH",
                                            9.8f,
                                            "CVSS:3.0/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
                                        )
                                    )
                                ),
                                identifier = Identifier("Maven", "org.namespace", "name", "1.0"),
                                rating = VulnerabilityRating.HIGH,
                                ortRunIds = listOf(40, 53),
                                repositoriesCount = 2
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("rating", SortDirection.DESCENDING))
                        )
                    )
                }
            }
        }
    }
}

val getOrtRunStatisticsByProductId: OpenApiRoute.() -> Unit = {
    operationId = "GetOrtRunStatisticsByProductId"
    summary = "Get statistics about ORT runs across the repositories of a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            jsonBody<OrtRunStatistics> {
                example("Get run statistics across repositories of a product") {
                    value = OrtRunStatistics(
                        issuesCount = 131,
                        issuesCountBySeverity = mapOf(
                            Severity.HINT to 40,
                            Severity.WARNING to 0,
                            Severity.ERROR to 91
                        ),
                        packagesCount = 953,
                        ecosystems = listOf(
                            EcosystemStats("Maven", 578),
                            EcosystemStats("NPM", 326),
                            EcosystemStats("PyPI", 49)
                        ),
                        vulnerabilitiesCount = 163,
                        vulnerabilitiesCountByRating = mapOf(
                            VulnerabilityRating.NONE to 11,
                            VulnerabilityRating.LOW to 1,
                            VulnerabilityRating.MEDIUM to 47,
                            VulnerabilityRating.HIGH to 83,
                            VulnerabilityRating.CRITICAL to 21
                        ),
                        ruleViolationsCount = 104,
                        ruleViolationsCountBySeverity = mapOf(
                            Severity.HINT to 0,
                            Severity.WARNING to 6,
                            Severity.ERROR to 98
                        )
                    )
                }
            }
        }
    }
}

val getUsersForProduct: OpenApiRoute.() -> Unit = {
    operationId = "GetUsersForProduct"
    summary = "Get all users for a product"
    description = "Get all users that have access rights for a product, including the user privileges (groups) " +
            "the user has within the product. Fields available for sorting: 'username', 'firstName', " +
            "'lastName', 'email', 'group'. NOTE: This endpoint supports only one sort field. All fields other than " +
            " the first one are ignored."
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<UserWithGroups>> {
                example("Get users for product") {
                    value = PagedResponse(
                        listOf(
                            UserWithGroups(
                                User(
                                    username = "jdoe",
                                    firstName = "John",
                                    lastName = "Doe",
                                    email = "johndoe@example.com"
                                ),
                                listOf(
                                    UserGroup.READERS,
                                    UserGroup.WRITERS
                                )
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }
    }
}

private val minimalJobConfigurations = JobConfigurations(
    analyzer = AnalyzerJobConfiguration(
        skipExcluded = true
    ),
    advisor = AdvisorJobConfiguration(
        skipExcluded = true
    )
)

val postOrtRunsForProduct: OpenApiRoute.() -> Unit = {
    operationId = "postOrtRunsForProduct"
    summary = "Create ORT runs for all repositories under a product"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }

        jsonBody<CreateOrtRun> {
            example("Create ORT runs using minimal job configurations (defaults)") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = minimalJobConfigurations
                )
            }

            example("Create ORT runs using full job configurations") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = fullJobConfigurations,
                    labels = mapOf("label key" to "label value"),
                    path = "optional VCS sub-path",
                    jobConfigContext = "optional context",
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<List<OrtRun>> {
                example("Create ORT runs") {
                    value = listOf(
                        OrtRun(
                            id = 1,
                            index = 2,
                            organizationId = 1,
                            productId = 1,
                            repositoryId = 1,
                            revision = "main",
                            createdAt = CREATED_AT,
                            jobConfigs = fullJobConfigurations,
                            resolvedJobConfigs = fullJobConfigurations,
                            jobs = jobs,
                            status = OrtRunStatus.CREATED,
                            finishedAt = null,
                            labels = mapOf("label key" to "label value"),
                            issues = emptyList(),
                            jobConfigContext = null,
                            resolvedJobConfigContext = null,
                            traceId = "35b67724-a85b-4cc3-b2a4-60fd914634e7"
                        ),
                        OrtRun(
                            id = 2,
                            index = 1,
                            organizationId = 1,
                            productId = 1,
                            repositoryId = 2,
                            revision = "main",
                            createdAt = CREATED_AT,
                            jobConfigs = fullJobConfigurations,
                            resolvedJobConfigs = fullJobConfigurations,
                            jobs = jobs,
                            status = OrtRunStatus.CREATED,
                            finishedAt = null,
                            labels = mapOf("label key" to "label value"),
                            issues = emptyList(),
                            jobConfigContext = null,
                            resolvedJobConfigContext = null,
                            traceId = "35b67724-a85b-4cc3-b2a4-60fd914634e7"
                        )
                    )
                }
            }
        }
    }
}
