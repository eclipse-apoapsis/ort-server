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

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingData
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent

val getOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizationById"
    summary = "Get details of an organization."
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

val getOrganizations: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizations"
    summary = "Get all organizations."
    tags = listOf("Organizations")

    request {
        standardListQueryParameters()
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

val postOrganizations: OpenApiRoute.() -> Unit = {
    operationId = "PostOrganizations"
    summary = "Create an organization."
    tags = listOf("Organizations")

    request {
        jsonBody<CreateOrganization> {
            example("Create Organization") {
                value = CreateOrganization(name = "My Organization", description = "This is my organization.")
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

val patchOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "PatchOrganizationById"
    summary = "Update an organization."
    tags = listOf("Organizations")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<UpdateOrganization> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
            example("Update Organization") {
                value = UpdateOrganization(
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

val deleteOrganizationById: OpenApiRoute.() -> Unit = {
    operationId = "DeleteOrganizationById"
    summary = "Delete an organization."
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

val getOrganizationProducts: OpenApiRoute.() -> Unit = {
    operationId = "GetOrganizationProducts"
    summary = "Get all products of an organization."
    tags = listOf("Products")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }

        standardListQueryParameters()
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

val postProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostProduct"
    summary = "Create a product for an organization."
    tags = listOf("Products")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<CreateProduct> {
            example("Create product") {
                value = CreateProduct(name = "My product", description = "Description")
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

val getSecretsByOrganizationId: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretsByOrganizationId"
    summary = "Get all secrets of an organization."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("organizationId") {
            description = "The ID of an organization."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<Secret>> {
                example("Get all secrets of an organization") {
                    value = PagedResponse(
                        listOf(
                            Secret(name = "rsa", description = "rsa certificate"),
                            Secret(name = "secret", description = "another secret")
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

val getSecretByOrganizationIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretByOrganizationIdAndName"
    summary = "Get details of a secret of an organization."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
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
                    value = Secret(name = "rsa", description = "rsa certificate")
                }
            }
        }
    }
}

val postSecretForOrganization: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForOrganization"
    summary = "Create a secret for an organization."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<CreateSecret> {
            example("Create Secret") {
                value = CreateSecret(
                    name = "New secret",
                    value = "0rg-s3cr3t-08_15",
                    description = "The new org secret"
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Secret> {
                example("Create Secret") {
                    value = Secret(name = "rsa", description = "New secret")
                }
            }
        }
    }
}

val patchSecretByOrganizationIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByOrganizationIdAndName"
    summary = "Update a secret of an organization."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
        jsonBody<UpdateSecret> {
            example("Update Secret") {
                value = UpdateSecret(
                    name = "My updated Secret".asPresent(),
                    value = "My updated value".asPresent(),
                    description = "Updated description".asPresent()
                )
            }
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example("Update Secret") {
                    value = Secret(name = "My updated Secret", description = "Updated description.")
                }
            }
        }
    }
}

val deleteSecretByOrganizationIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteSecretByOrganizationIdAndName"
    summary = "Delete a secret from an organization."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
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

val getInfrastructureServicesByOrganizationId: OpenApiRoute.() -> Unit = {
    operationId = "GetInfrastructureServicesByOrganizationId"
    summary = "List all infrastructure services of an organization."
    tags = listOf("Infrastructure services")

    request {
        pathParameter<Long>("organizationId") {
            description = "The ID of an organization."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<InfrastructureService>> {
                example("List all infrastructure services for an organization") {
                    value = PagedResponse(
                        listOf(
                            InfrastructureService(
                                name = "Artifactory",
                                url = "https://artifactory.example.org/releases",
                                description = "Artifactory repository",
                                usernameSecretRef = "artifactoryUsername",
                                passwordSecretRef = "artifactoryPassword"
                            ),
                            InfrastructureService(
                                name = "GitHub",
                                url = "https://github.com",
                                description = "GitHub server",
                                usernameSecretRef = "gitHubUsername",
                                passwordSecretRef = "gitHubPassword"
                            )
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

val postInfrastructureServiceForOrganization: OpenApiRoute.() -> Unit = {
    operationId = "PostInfrastructureServiceForOrganization"
    summary = "Create an infrastructure service for an organization."
    tags = listOf("Infrastructure services")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        jsonBody<CreateInfrastructureService> {
            example("Create infrastructure service") {
                value = CreateInfrastructureService(
                    name = "Artifactory",
                    url = "https://artifactory.example.org/releases",
                    description = "Artifactory repository",
                    usernameSecretRef = "artifactoryUsername",
                    passwordSecretRef = "artifactoryPassword"
                )
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example("Create infrastructure service") {
                    value = InfrastructureService(
                        name = "Artifactory",
                        url = "https://artifactory.example.org/releases",
                        description = "Artifactory repository",
                        usernameSecretRef = "artifactoryUsername",
                        passwordSecretRef = "artifactoryPassword"
                    )
                }
            }
        }
    }
}

val patchInfrastructureServiceForOrganizationIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchInfrastructureServiceForOrganizationIdAndName"
    summary = "Update an infrastructure service for an organization."
    tags = listOf("Infrastructure services")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        pathParameter<String>("serviceName") {
            description = "The name of the infrastructure service."
        }
        jsonBody<UpdateInfrastructureService> {
            example("Update infrastructure service") {
                value = UpdateInfrastructureService(
                    url = "https://github.com".asPresent(),
                    description = "Updated description".asPresent(),
                    usernameSecretRef = "newGitHubUser".asPresent(),
                    passwordSecretRef = "newGitHubPassword".asPresent()
                )
            }
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example("Update infrastructure service") {
                    value = InfrastructureService(
                        name = "GitHub",
                        url = "https://github.com",
                        description = "Updated description",
                        usernameSecretRef = "newGitHubUser",
                        passwordSecretRef = "newGitHubPassword"
                    )
                }
            }
        }
    }
}

val deleteInfrastructureServiceForOrganizationIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteInfrastructureServiceForOrganizationIdAndName"
    summary = "Delete an infrastructure service from an organization."
    tags = listOf("Infrastructure services")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
        }
        pathParameter<String>("serviceName") {
            description = "The name of the infrastructure service."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val putUserToGroup: OpenApiRoute.() -> Unit = {
    operationId = "PutUserToGroup"
    summary = "Add a user to a group on Organization level."
    tags = listOf("Groups")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
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
            description = "Organization or group not found."
        }
    }
}

val deleteUserFromGroup: OpenApiRoute.() -> Unit = {
    operationId = "DeleteUserFromGroup"
    summary = "Remove a user from a group on Organization level."
    tags = listOf("Groups")

    request {
        pathParameter<Long>("organizationId") {
            description = "The organization's ID."
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
            description = "Successfully removed the user to the group."
        }

        HttpStatusCode.NotFound to {
            description = "Organization or group not found."
        }
    }
}
