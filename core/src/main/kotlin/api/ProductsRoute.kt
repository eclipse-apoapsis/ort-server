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

package org.eclipse.apoapsis.ortserver.core.api

import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.patch
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.put

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToModel
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserFromProductGroup
import org.eclipse.apoapsis.ortserver.core.apiDocs.getProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getRepositoriesByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSecretsByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.getVulnerabilitiesAcrossRepositoriesByProductId
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchProductById
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchSecretByProductIdAndName
import org.eclipse.apoapsis.ortserver.core.apiDocs.postRepository
import org.eclipse.apoapsis.ortserver.core.apiDocs.postSecretForProduct
import org.eclipse.apoapsis.ortserver.core.apiDocs.putUserToProductGroup
import org.eclipse.apoapsis.ortserver.core.authorization.requirePermission
import org.eclipse.apoapsis.ortserver.core.utils.pagingOptions
import org.eclipse.apoapsis.ortserver.core.utils.requireIdParameter
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.model.authorization.ProductPermission
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.services.VulnerabilityService

import org.koin.ktor.ext.inject

@Suppress("LongMethod")
fun Route.products() = route("products/{productId}") {
    val productService by inject<ProductService>()
    val repositoryService by inject<RepositoryService>()
    val secretService by inject<SecretService>()
    val vulnerabilityService by inject<VulnerabilityService>()

    get(getProductById) {
        requirePermission(ProductPermission.READ)

        val id = call.requireIdParameter("productId")

        val product = productService.getProduct(id)

        if (product != null) {
            call.respond(HttpStatusCode.OK, product.mapToApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    patch(patchProductById) {
        requirePermission(ProductPermission.WRITE)

        val id = call.requireIdParameter("productId")
        val updateProduct = call.receive<UpdateProduct>()

        val updatedProduct =
            productService.updateProduct(id, updateProduct.name.mapToModel(), updateProduct.description.mapToModel())

        call.respond(HttpStatusCode.OK, updatedProduct.mapToApi())
    }

    delete(deleteProductById) {
        requirePermission(ProductPermission.DELETE)

        val id = call.requireIdParameter("productId")

        productService.deleteProduct(id)

        call.respond(HttpStatusCode.NoContent)
    }

    route("repositories") {
        get(getRepositoriesByProductId) {
            requirePermission(ProductPermission.READ_REPOSITORIES)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("url", SortDirection.ASCENDING))

            val repositoriesForProduct =
                productService.listRepositoriesForProduct(productId, pagingOptions.mapToModel())

            val pagedResponse = repositoriesForProduct.mapToApi(Repository::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        post(postRepository) {
            requirePermission(ProductPermission.CREATE_REPOSITORY)

            val id = call.requireIdParameter("productId")
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

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("name", SortDirection.ASCENDING))

            val secretsForProduct = secretService.listForProduct(productId, pagingOptions.mapToModel())

            val pagedResponse = secretsForProduct.mapToApi(Secret::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }

        route("{secretName}") {
            get(getSecretByProductIdAndName) {
                requirePermission(ProductPermission.READ)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")

                secretService.getSecretByProductIdAndName(productId, secretName)
                    ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                    ?: call.respond(HttpStatusCode.NotFound)
            }

            patch(patchSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")
                val updateSecret = call.receive<UpdateSecret>()

                call.respond(
                    HttpStatusCode.OK,
                    secretService.updateSecretByProductAndName(
                        productId,
                        secretName,
                        updateSecret.value.mapToModel(),
                        updateSecret.description.mapToModel()
                    ).mapToApi()
                )
            }

            delete(deleteSecretByProductIdAndName) {
                requirePermission(ProductPermission.WRITE_SECRETS)

                val productId = call.requireIdParameter("productId")
                val secretName = call.requireParameter("secretName")

                secretService.deleteSecretByProductAndName(productId, secretName)

                call.respond(HttpStatusCode.NoContent)
            }
        }

        post(postSecretForProduct) {
            requirePermission(ProductPermission.WRITE_SECRETS)

            val productId = call.requireIdParameter("productId")
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

    route("groups") {
        // Instead of identifying arbitrary groups with a groupId, there are only 3 groups with fixed
        // groupId "readers", "writers" or "admins".
        route("{groupId}") {
            put(putUserToProductGroup) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val productId = call.requireIdParameter("productId")
                val groupId = call.requireParameter("groupId")

                productService.addUserToGroup(user.username, productId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete(deleteUserFromProductGroup) {
                requirePermission(ProductPermission.MANAGE_GROUPS)

                val user = call.receive<Username>()
                val productId = call.requireIdParameter("productId")
                val groupId = call.requireParameter("groupId")

                productService.removeUserFromGroup(user.username, productId, groupId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    route("vulnerabilities") {
        get(getVulnerabilitiesAcrossRepositoriesByProductId) {
            requirePermission(ProductPermission.READ)

            val productId = call.requireIdParameter("productId")
            val pagingOptions = call.pagingOptions(SortProperty("rating", SortDirection.DESCENDING))

            val repositoryIds = productService.getRepositoryIdsForProduct(productId)

            val ortRunIds = repositoryIds.mapNotNull { repositoryId ->
                repositoryService.getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId)
            }

            val vulnerabilities =
                vulnerabilityService.listForOrtRuns(ortRunIds, pagingOptions.mapToModel())

            val pagedResponse = vulnerabilities.mapToApi(VulnerabilityWithAccumulatedData::mapToApi)

            call.respond(HttpStatusCode.OK, pagedResponse)
        }
    }
}
