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

package org.ossreviewtoolkit.server.core.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.UpdateProduct

fun Route.products() = route("products/{productId}") {
    val productRepository by inject<ProductRepository>()
    val repositoryRepository by inject<RepositoryRepository>()

    get {
        val id = call.requireParameter("productId").toLong()

        val product = productRepository.get(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch {
        val id = call.requireParameter("productId").toLong()
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct =
            productRepository.update(id, updateProduct.name.mapToModel(), updateProduct.description.mapToModel())

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete {
        val id = call.requireParameter("productId").toLong()

        productRepository.delete(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get {
            val id = call.requireParameter("productId").toLong()

            call.respond(
                HttpStatusCode.OK,
                repositoryRepository.listForProduct(id).map { it.mapToApi() }
            )
        }

        post {
            val productId = call.requireParameter("productId").toLong()
            val createRepository = call.receive<CreateRepository>()

            call.respond(
                HttpStatusCode.Created,
                repositoryRepository.create(createRepository.type.mapToModel(), createRepository.url, productId)
                    .mapToApi()
            )
        }
    }
}
