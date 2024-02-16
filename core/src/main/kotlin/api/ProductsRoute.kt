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

package org.ossreviewtoolkit.server.core.api

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.api.v1.CreateRepository
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.PagedResponse
import org.ossreviewtoolkit.server.api.v1.UpdateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.api.v1.mapToModel
import org.ossreviewtoolkit.server.core.apiDocs.deleteProductById
import org.ossreviewtoolkit.server.core.apiDocs.deleteSecretByProductIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getProductById
import org.ossreviewtoolkit.server.core.apiDocs.getRepositoriesByProductId
import org.ossreviewtoolkit.server.core.apiDocs.getSecretByProductIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getSecretsByProductId
import org.ossreviewtoolkit.server.core.apiDocs.patchProductById
import org.ossreviewtoolkit.server.core.apiDocs.patchSecretByProductIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.postRepository
import org.ossreviewtoolkit.server.core.apiDocs.postSecretForProduct
import org.ossreviewtoolkit.server.core.authorization.requirePermission
import org.ossreviewtoolkit.server.core.utils.listQueryParameters
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.authorization.ProductPermission
import org.ossreviewtoolkit.server.model.util.OrderDirection
import org.ossreviewtoolkit.server.model.util.OrderField
import org.ossreviewtoolkit.server.services.ProductService
import org.ossreviewtoolkit.server.services.SecretService

fun Route.products() = route("products/{productId}") {
    val productService by inject<ProductService>()
    val secretService by inject<SecretService>()

    get(getProductById) {
        requirePermission(ProductPermission.READ)

        val id = call.requireParameter("productId").toLong()

        val product = productService.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch(patchProductById) {
        requirePermission(ProductPermission.WRITE)

        val id = call.requireParameter("productId").toLong()
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct =
            productService.updateProduct(id, updateProduct.name, updateProduct.description)

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete(deleteProductById) {
        requirePermission(ProductPermission.DELETE)

        val id = call.requireParameter("productId").toLong()

        productService.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get(getRepositoriesByProductId) {
            requirePermission(ProductPermission.READ_REPOSITORIES)

            val productId = call.requireParameter("productId").toLong()
            val paginationParameters = call.listQueryParameters(OrderField("url", OrderDirection.ASCENDING))

            val repositoriesForProduct =
                productService.listRepositoriesForProduct(productId, paginationParameters)
            val pagedResponse = PagedResponse(
                repositoriesForProduct.map { it.mapToApi() },
                paginationParameters
            )

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postRepository) {
            requirePermission(ProductPermission.CREATE_REPOSITORY)

            val id = call.requireParameter("productId").toLong()
            val createRepository = call.receive<CreateRepository>()

            call.respond(
                HttpStatusCode.Created,
                productService.createRepository(createRepository.type.mapToModel(), createRepository.url, id)
                    .mapToApi()
            )
        }
    }

    route("secrets") {
        get(getSecretsByProductId) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireParameter("productId").toLong()
            val paginationParameters = call.listQueryParameters(OrderField("name", OrderDirection.ASCENDING))

            val secretsForProduct = secretService.listForProduct(productId, paginationParameters)
            val pagedResponse = PagedResponse(
                secretsForProduct.map { it.mapToApi() },
                paginationParameters
            )

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        route("{secretName}") {
            get(getSecretByProductIdAndName) {
                requirePermission(ProductPermission.READ)

                val productId = call.requireParameter("productId").toLong()
                val secretName = call.requireParameter("secretName")

                secretService.getSecretByProductIdAndName(productId, secretName)
                    ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                    ?: call.respond(HttpStatusCode.NotFound)
            }

            patch(patchSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireParameter("productId").toLong()
                val secretName = call.requireParameter("secretName")
                val updateSecret = call.receive<UpdateSecret>()

                call.respond(
                    HttpStatusCode.OK,
                    secretService.updateSecretByProductAndName(
                        productId,
                        secretName,
                        updateSecret.value,
                        updateSecret.description
                    ).mapToApi()
                )
            }

            delete(deleteSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireParameter("productId").toLong()
                val secretName = call.requireParameter("secretName")

                secretService.deleteSecretByProductAndName(productId, secretName)

                call.respond(HttpStatusCode.NoContent)
            }
        }

        post(postSecretForProduct) {
            requirePermission(ProductPermission.WRITE_SECRETS)

            val productId = call.requireParameter("productId").toLong()
            val createSecret = call.receive<CreateSecret>()

            call.respond(
                HttpStatusCode.Created,
                secretService.createSecret(
                    createSecret.name,
                    createSecret.value,
                    createSecret.description,
                    null,
                    productId,
                    null
                ).mapToApi()
            )
        }
    }
}
