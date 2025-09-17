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

import io.github.smiley4.ktoropenapi.config.RouteConfig

import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.ProductVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.components.authorization.api.ProductRole
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

val getProductById: RouteConfig.() -> Unit = {
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

val patchProductById: RouteConfig.() -> Unit = {
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

val deleteProductById: RouteConfig.() -> Unit = {
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

val getRepositoriesByProductId: RouteConfig.() -> Unit = {
    operationId = "GetRepositoriesByProductId"
    summary = "Get all repositories of a product"
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

val postRepository: RouteConfig.() -> Unit = {
    operationId = "CreateRepository"
    summary = "Create a repository for a product"
    tags = listOf("Products")

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

val putProductRoleToUser: RouteConfig.() -> Unit = {
    operationId = "PutProductRoleToUser"
    summary = "Assign a product role to a user"
    description = "Assign a product role to a user. If the user already has another role for the same product, it " +
            "will be replaced with the new one."
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<ProductRole>("role") {
            description = "The role to assign to the user."
        }

        jsonBody<Username> {
            example("Add user identified by username 'abc123'.") {
                value = Username(username = "abc123")
            }
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully added the role to the user."
        }

        HttpStatusCode.NotFound to {
            description = "Product or role not found."
        }
    }
}

val deleteProductRoleFromUser: RouteConfig.() -> Unit = {
    operationId = "DeleteProductRoleFromUser"
    summary = "Remove a product role from a user"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        pathParameter<ProductRole>("role") {
            description = "The role to remove from the user."
        }

        queryParameter<String>("username") {
            description = "The username of the user."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully removed the role from the user."
        }

        HttpStatusCode.NotFound to {
            description = "Product or role not found."
        }
    }
}

val getVulnerabilitiesAcrossRepositoriesByProductId: RouteConfig.() -> Unit = {
    operationId = "GetVulnerabilitiesAcrossRepositoriesByProductId"
    summary = "Get vulnerabilities from a product"
    description = "Get the vulnerabilities from latest successful advisor runs across the repositories in a product."
    tags = listOf("Products")

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
                                purl = "pkg:maven/org.namespace/name@1.0",
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

val getOrtRunStatisticsByProductId: RouteConfig.() -> Unit = {
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

val getUsersForProduct: RouteConfig.() -> Unit = {
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

val postOrtRunsForProduct: RouteConfig.() -> Unit = {
    operationId = "postOrtRunsForProduct"
    summary = "Create ORT runs for repositories under a product"
    description = "Create ORT runs for all repositories under a product or specific repositories"
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }

        jsonBody<CreateOrtRun> {
            example("Create ORT runs for all repositories using minimal job configurations") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = minimalJobConfigurations
                )
            }

            example("Create ORT runs for specific repositories using minimal job configurations") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = minimalJobConfigurations,
                    repositoryIds = listOf(1, 2)
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

        HttpStatusCode.BadRequest to {
            description = "Invalid request, e.g., provided repository IDs do not belong to the product."
        }
    }
}
