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

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.Product
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.model.util.asPresent

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
                example("Create Product", Product(1, "My Product", "Description"))
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
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Product> {
                example("Update Product", Product(1, "My updated product", "Updated product."))
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
    summary = "List all repositories for a product."
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
            jsonBody<List<Repository>> {
                example(
                    "Get repositories of a product",
                    listOf(
                        Repository(1, RepositoryType.GIT, "https://example.com/first/repo.git"),
                        Repository(2, RepositoryType.SUBVERSION, "https://example.com/second/repo")
                    )
                )
            }
        }
    }
}

val postRepository: OpenApiRoute.() -> Unit = {
    operationId = "CreateRepository"
    summary = "Create a repository for the product."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("productId") {
            description = "The product's ID."
        }
        jsonBody<CreateRepository> {
            example("Create repository", CreateRepository(RepositoryType.GIT, "https://example.com/namspace/repo.git"))
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Repository> {
                example("Create repository", Repository(1, RepositoryType.GIT, "https://example.com/namspace/repo.git"))
            }
        }
    }
}

val getSecretsByProductId: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretsByProductId"
    summary = "List all secrets for a specific product."
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
            jsonBody<List<Secret>> {
                example(
                    "List all secrets for a specific product",
                    listOf(
                        Secret(
                            "rsa",
                            "ssh rsa certificate"
                        ),
                        Secret(
                            "secret",
                            "another secret"
                        )
                    )
                )
            }
        }
    }
}

val getSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretByProductIdAndName"
    summary = "Get details of a secret by product id and name."
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

val postSecretForProduct: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForProduct"
    summary = "Create a secret for productId."
    tags = listOf("Secrets")

    request {
        jsonBody<CreateSecret> {
            example(
                "Create Secret",
                CreateSecret(
                    "New secret",
                    "The new prod secret",
                    null,
                    1,
                    null
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

val patchSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByProductIdIdAndName"
    summary = "Update a secret by product id and name."
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

val deleteSecretByProductIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteSecretByProductIdAndName"
    summary = "Delete a secret by product id and name."
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
