/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRoute

import io.ktor.http.HttpStatusCode

import org.ossreviewtoolkit.server.api.v1.CreateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.InfrastructureService
import org.ossreviewtoolkit.server.api.v1.Organization
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateInfrastructureService
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.model.util.asPresent

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
                example("Get Organization", Organization(1, "My Organization", "This is my organization."))
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
            jsonBody<List<Organization>> {
                example(
                    "List all organizations",
                    listOf(
                        Organization(1, "First Organization", "Description"),
                        Organization(2, "Second Organization")
                    )
                )
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
            example("Create Organization", CreateOrganization("My Organization", "This is my organization."))
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Organization> {
                example("Create Organization", Organization(1, "My Organization", "This is my organization."))
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
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Organization> {
                example("Update Organization", Organization(1, "My updated Organization", "Updated description."))
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
            jsonBody<List<Product>> {
                example(
                    "Get products of an organization",
                    listOf(
                        Product(1, "My first product", "Description"),
                        Product(2, "My second product")
                    )
                )
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
            example("Create product", CreateProduct("My product", "Description"))
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Product> {
                example("Create product", Product(1, "My product", "Description"))
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
            jsonBody<List<Secret>> {
                example(
                    "Get all secrets of an organization",
                    listOf(
                        Secret(
                            "rsa",
                            "rsa certificate"
                        ),
                        Secret(
                            "secret",
                            "another secret",
                        )
                    )
                )
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
                example(
                    "Get Secret",
                    Secret(
                        "rsa",
                        "rsa certificate"
                    )
                )
            }
        }
    }
}

val postSecretForOrganization: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForOrganization"
    summary = "Create a secret for an organization."
    tags = listOf("Secrets")

    request {
        jsonBody<CreateSecret> {
            example(
                "Create Secret",
                CreateSecret(
                    "New secret",
                    "0rg-s3cr3t-08_15",
                    "The new org secret"
                )
            )
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Create Secret",
                    Secret(
                        "rsa",
                        "New secret"
                    )
                )
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
            example(
                "Update Secret",
                UpdateSecret(
                    "My updated Secret".asPresent(),
                    "Updated description".asPresent()
                )
            )
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Update Secret",
                    Secret(
                        "My updated Secret",
                        "Updated description."
                    )
                )
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
            jsonBody<List<Secret>> {
                example(
                    "List all infrastructure services for an organization",
                    listOf(
                        InfrastructureService(
                            "Artifactory",
                            "https://artifactory.example.org/releases",
                            "Artifactory repository",
                            "artifactoryUsername",
                            "artifactoryPassword"
                        ),
                        InfrastructureService(
                            "GitHub",
                            "https://github.com",
                            "GitHub server",
                            "gitHubUsername",
                            "gitHubPassword"
                        )
                    )
                )
            }
        }
    }
}

val postInfrastructureServiceForOrganization: OpenApiRoute.() -> Unit = {
    operationId = "PostInfrastructureServiceForOrganization"
    summary = "Create an infrastructure service for an organization."
    tags = listOf("Infrastructure services")

    request {
        jsonBody<CreateInfrastructureService> {
            example(
                "Create infrastructure service",
                CreateInfrastructureService(
                    "Artifactory",
                    "https://artifactory.example.org/releases",
                    "Artifactory repository",
                    "artifactoryUsername",
                    "artifactoryPassword"
                )
            )
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<InfrastructureService> {
                example(
                    "Create infrastructure service",
                    InfrastructureService(
                        "Artifactory",
                        "https://artifactory.example.org/releases",
                        "Artifactory repository",
                        "artifactoryUsername",
                        "artifactoryPassword"
                    )
                )
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
        jsonBody<UpdateSecret> {
            example(
                "Update infrastructure service",
                UpdateInfrastructureService(
                    url = "https://github.com".asPresent(),
                    description = "Updated description".asPresent(),
                    passwordSecretRef = "newGitHubPassword".asPresent()
                )
            )
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Update infrastructure service",
                    InfrastructureService(
                        "GitHub",
                        "https://github.com",
                        "Updated description",
                        "gitHubUsername",
                        "newGitHubPassword"
                    )
                )
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
