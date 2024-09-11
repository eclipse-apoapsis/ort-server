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

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingData
import org.eclipse.apoapsis.ortserver.api.v1.model.Product
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType.GIT
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType.SUBVERSION
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.api.v1.model.asPresent

val getProductById: OpenApiRoute.() -> Unit = {
    operationId = "GetProductById"
    summary = "Get details of a product."
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
    summary = "Update a product."
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
    summary = "Delete a product."
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
    summary = "Get all repositories of a product."
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
                                type = GIT,
                                url = "https://example.com/first/repo.git"
                            ),
                            Repository(
                                id = 2,
                                organizationId = 3,
                                productId = 4,
                                type = SUBVERSION,
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
    summary = "Create a repository for a product."
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
    summary = "Get all secrets of a specific product."
    tags = listOf("Secrets")

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
                            Secret(name = "rsa", description = "ssh rsa certificate"),
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

val getSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretByProductIdAndName"
    summary = "Get details of a secret of a product."
    tags = listOf("Secrets")

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
                    value = Secret(name = "rsa", description = "rsa certificate")
                }
            }
        }
    }
}

val postSecretForProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForProduct"
    summary = "Create a secret for a product."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<CreateSecret> {
            example("Create Secret") {
                value = CreateSecret(
                    name = "New secret",
                    value = "pr0d-s3cr3t-08_15",
                    description = "The new prod secret"
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

val patchSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByProductIdAndName"
    summary = "Update a secret of a product."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
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

val deleteSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteSecretByProductIdAndName"
    summary = "Delete a secret from a product."
    tags = listOf("Secrets")

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
    summary = "Add a user to a group on Product level."
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
    summary = "Remove a user from a group on Product level."
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
