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

import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization
import org.eclipse.apoapsis.ortserver.api.v1.model.OrganizationVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatistics
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.api.OrganizationRole
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedSearchResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

val getOrganization: RouteConfig.() -> Unit = {
    operationId = "getOrganization"
    summary = "Get details of an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Organization> {
                example("Get Organization") {
                    value = Organization(id = 1, name = "My Organization", description = "This is my organization.")
                }
            }
        }
    }
}

val getOrganizations: RouteConfig.() -> Unit = {
    operationId = "getOrganizations"
    summary = "Get all organizations"
    tags = listOf("Organizations")

    request {
        standardListQueryParameters()

        queryParameter<String>("filter") {
            description = "A regular expression to filter organizations by name."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<Organization>> {
                example("List all organizations") {
                    value = PagedResponse(
                        listOf(
                            Organization(id = 1, name = "First Organization", description = "Description"),
                            Organization(id = 2, name = "Second Organization")
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

val postOrganization: RouteConfig.() -> Unit = {
    operationId = "postOrganization"
    summary = "Create an organization"
    tags = listOf("Organizations")

    request {
        jsonBody<PostOrganization> {
            example("Create Organization") {
                value = PostOrganization(name = "My Organization", description = "This is my organization.")
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Organization> {
                example("Create Organization") {
                    value = Organization(id = 1, name = "My Organization", description = "This is my organization.")
                }
            }
        }
    }
}

val patchOrganization: RouteConfig.() -> Unit = {
    operationId = "patchOrganization"
    summary = "Update an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<PatchOrganization> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
            example("Update Organization") {
                value = PatchOrganization(
                    name = "My updated Organization".asPresent(),
                    description = "Updated description".asPresent()
                )
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Organization> {
                example("Update Organization") {
                    value = Organization(id = 1, name = "My updated Organization", description = "Updated description.")
                }
            }
        }
    }
}

val deleteOrganization: RouteConfig.() -> Unit = {
    operationId = "deleteOrganization"
    summary = "Delete an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val getOrganizationProducts: RouteConfig.() -> Unit = {
    operationId = "getOrganizationProducts"
    summary = "Get all products of an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }

        standardListQueryParameters()

        queryParameter<String>("filter") {
            description = "A regular expression to filter products by name."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<Product>> {
                example("Get products of an organization") {
                    value = PagedResponse(
                        listOf(
                            Product(id = 1, organizationId = 2, name = "My first product", description = "Description"),
                            Product(id = 2, organizationId = 2, name = "My second product")
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

val postProduct: RouteConfig.() -> Unit = {
    operationId = "postProduct"
    summary = "Create a product for an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<PostProduct> {
            example("Create product") {
                value = PostProduct(name = "My product", description = "Description")
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Product> {
                example("Create product") {
                    value = Product(id = 1, organizationId = 2, name = "My product", description = "Description")
                }
            }
        }
    }
}

val putOrganizationRoleToUser: RouteConfig.() -> Unit = {
    operationId = "putOrganizationRoleToUser"
    summary = "Assign an organization role to a user"
    description = "Assign an organization role to a user. If the user already has another role for the same " +
            "organization, it will be replaced with the new one."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        pathParameter<OrganizationRole>("role") {
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
            description = "Organization or role not found."
        }
    }
}

val deleteOrganizationRoleFromUser: RouteConfig.() -> Unit = {
    operationId = "deleteOrganizationRoleFromUser"
    summary = "Remove an organization role from a user"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        pathParameter<String>("role") {
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
            description = "Organization or role not found."
        }
    }
}

val getOrganizationVulnerabilities: RouteConfig.() -> Unit = {
    operationId = "getOrganizationVulnerabilities"
    summary = "Get vulnerabilities from an organization"
    description = "Get the vulnerabilities from latest successful advisor runs across the repositories in an " +
            " organization."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }

        standardListQueryParameters()

        queryParameter<String>("rating") {
            description = "Defines the ratings to filter the results by. This is a comma-separated string with the" +
                    " following allowed ratings: " + VulnerabilityRating.entries.joinToString { "'$it" } + ". Add a " +
                    "minus as the first item to exclude packages with the specified ratings, e.g. '-,HIGH'."
        }

        queryParameter<String>("identifier") {
            description = "Defines an ORT package identifier to filter the results by. Uses a case-insensitive " +
                    "substring match."
        }

        queryParameter<String>("purl") {
            description = "Defines a purl to filter the results by. Uses a case-insensitive substring match."
        }

        queryParameter<String>("externalId") {
            description = "Defines an external ID to filter the results by. Uses a case-insensitive substring match."
        }
    }

    response {
        HttpStatusCode.OK to {
            jsonBody<PagedSearchResponse<OrganizationVulnerability, VulnerabilityForRunsFilters>> {
                example("Get vulnerabilities for organization") {
                    value = PagedSearchResponse(
                        listOf(
                            OrganizationVulnerability(
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
                        ),
                        VulnerabilityForRunsFilters(
                            rating = FilterOperatorAndValue(
                                operator = ComparisonOperator.IN,
                                value = setOf(VulnerabilityRating.HIGH)
                            )
                        )
                    )
                }
            }
        }
    }
}

val getOrganizationRunStatistics: RouteConfig.() -> Unit = {
    operationId = "getOrganizationRunStatistics"
    summary = "Get statistics about ORT runs across the repositories of an organization"
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The ID of an organization."
        }
    }

    response {
        HttpStatusCode.OK to {
            jsonBody<OrtRunStatistics> {
                example("Get run statistics across repositories of an organization") {
                    value = OrtRunStatistics(
                        issuesCount = 5,
                        issuesCountBySeverity = mapOf(
                            Severity.HINT to 0,
                            Severity.WARNING to 4,
                            Severity.ERROR to 1
                        ),
                        packagesCount = 452,
                        ecosystems = listOf(
                            EcosystemStats("Maven", 422),
                            EcosystemStats("NPM", 30)
                        ),
                        vulnerabilitiesCount = 3,
                        vulnerabilitiesCountByRating = mapOf(
                            VulnerabilityRating.NONE to 0,
                            VulnerabilityRating.LOW to 0,
                            VulnerabilityRating.MEDIUM to 2,
                            VulnerabilityRating.HIGH to 0,
                            VulnerabilityRating.CRITICAL to 1
                        ),
                        ruleViolationsCount = 4,
                        ruleViolationsCountBySeverity = mapOf(
                            Severity.HINT to 3,
                            Severity.WARNING to 1,
                            Severity.ERROR to 0
                        )
                    )
                }
            }
        }
    }
}

val getOrganizationUsers: RouteConfig.() -> Unit = {
    operationId = "getOrganizationUsers"
    summary = "Get all users for an organization"
    description = "Get all users that have access rights for an organization, including the user privileges (groups) " +
            "the user has within the organization. Fields available for sorting: 'username', 'firstName', " +
            "'lastName', 'email', 'group'. NOTE: This endpoint supports only one sort field. All fields other than " +
            " the first one are ignored."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The ID of an organization."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<UserWithGroups>> {
                example("Get users for organization") {
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
