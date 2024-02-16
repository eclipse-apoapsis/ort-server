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

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRoute

import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.PagedResponse
import org.eclipse.apoapsis.ortserver.api.v1.Product
import org.eclipse.apoapsis.ortserver.api.v1.Repository
import org.eclipse.apoapsis.ortserver.api.v1.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.RepositoryType.GIT
import org.eclipse.apoapsis.ortserver.api.v1.RepositoryType.SUBVERSION
import org.eclipse.apoapsis.ortserver.api.v1.Secret
import org.eclipse.apoapsis.ortserver.api.v1.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.UpdateSecret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection.ASCENDING
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

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
                example(
                    name = "Create Product",
                    value = Product(id = 1, name = "My Product", description = "Description")
                )
            }
        }
    }
}

val patchProductById: OpenApiRoute.() -> Unit = {
    operationId = "PathProductById"
    summary = "Update a product."
    tags = listOf("Products")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<UpdateProduct> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
            example(
                name = "Update Product",
                value = UpdateProduct(name = "Update Product".asPresent(), description = "Updated product".asPresent())
            )
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Product> {
                example(
                    name = "Update Product",
                    value = Product(id = 1, name = "My updated product", description = "Updated product.")
                )
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
                example(
                    name = "Get repositories of a product",
                    value = PagedResponse(
                        listOf(
                            Repository(id = 1, type = GIT, url = "https://example.com/first/repo.git"),
                            Repository(
                                id = 2, type = SUBVERSION, url = "https://example.com/second/repo"
                            )
                        ),
                        ListQueryParameters(
                            sortFields = listOf(OrderField("url", ASCENDING)),
                            limit = 20,
                            offset = 0
                        )
                    )
                )
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
            example(
                name = "Create repository",
                value = CreateRepository(
                    type = RepositoryType.GIT,
                    url = "https://example.com/namspace/repo.git",
                ),
            )
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Repository> {
                example(
                    name = "Create repository",
                    value = Repository(id = 1, type = RepositoryType.GIT, url = "https://example.com/namspace/repo.git")
                )
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
                example(
                    name = "List all secrets of a product",
                    value = PagedResponse(
                        listOf(
                            Secret(name = "rsa", description = "ssh rsa certificate"),
                            Secret(name = "secret", description = "another secret")
                        ),
                        ListQueryParameters(
                            sortFields = listOf(OrderField("name", ASCENDING)),
                            limit = 20,
                            offset = 0
                        )
                    )
                )
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
                example(
                    name = "Get Secret",
                    value = Secret(name = "rsa", description = "rsa certificate")
                )
            }
        }
    }
}

val postSecretForProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForProduct"
    summary = "Create a secret for a product."
    tags = listOf("Secrets")

    request {
        jsonBody<CreateSecret> {
            example(
                name = "Create Secret",
                value = CreateSecret(
                    name = "New secret",
                    value = "pr0d-s3cr3t-08_15",
                    description = "The new prod secret"
                )
            )
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    name = "Create Secret",
                    value = Secret(name = "rsa", description = "New secret")
                )
            }
        }
    }
}

val patchSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByProductIdIdAndName"
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
            example(
                name = "Update Secret",
                value = UpdateSecret(
                    name = "My updated Secret".asPresent(),
                    value = "My updated value".asPresent(),
                    description = "Updated description".asPresent()
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
                    name = "Update Secret",
                    value = Secret(name = "My updated Secret", description = "Updated description.")
                )
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
