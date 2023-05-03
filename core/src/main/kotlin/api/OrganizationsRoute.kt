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

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.apiDocs.deleteOrganizationById
import org.ossreviewtoolkit.server.core.apiDocs.deleteSecretByOrganizationIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getOrganizationById
import org.ossreviewtoolkit.server.core.apiDocs.getOrganizationProducts
import org.ossreviewtoolkit.server.core.apiDocs.getOrganizations
import org.ossreviewtoolkit.server.core.apiDocs.getSecretByOrganizationIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.getSecretsByOrganizationId
import org.ossreviewtoolkit.server.core.apiDocs.patchOrganizationById
import org.ossreviewtoolkit.server.core.apiDocs.patchSecretByOrganizationIdAndName
import org.ossreviewtoolkit.server.core.apiDocs.postOrganizations
import org.ossreviewtoolkit.server.core.apiDocs.postProduct
import org.ossreviewtoolkit.server.core.apiDocs.postSecretForOrganization
import org.ossreviewtoolkit.server.core.utils.listQueryParameters
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.SecretService

fun Route.organizations() = route("organizations") {
    val organizationService by inject<OrganizationService>()
    val secretService by inject<SecretService>()

    get(getOrganizations) {
        val organizations = organizationService.listOrganizations(call.listQueryParameters())

        call.respond(HttpStatusCode.OK, organizations.map { it.mapToApi() })
    }

    post(postOrganizations) {
        val createOrganization = call.receive<CreateOrganization>()

        val createdOrganization =
            organizationService.createOrganization(createOrganization.name, createOrganization.description)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApi())
    }

    route("{organizationId}") {
        get(getOrganizationById) {
            val id = call.requireParameter("organizationId").toLong()

            val organization = organizationService.getOrganization(id)

            organization?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                ?: call.respond(HttpStatusCode.NotFound)
        }

        patch(patchOrganizationById) {
            val organizationId = call.requireParameter("organizationId").toLong()
            val org = call.receive<UpdateOrganization>()

            val updatedOrg = organizationService.updateOrganization(organizationId, org.name, org.description)

            call.respond(HttpStatusCode.OK, updatedOrg.mapToApi())
        }

        delete(deleteOrganizationById) {
            val id = call.requireParameter("organizationId").toLong()

            organizationService.deleteOrganization(id)

            call.respond(HttpStatusCode.NoContent)
        }

        get("products", getOrganizationProducts) {
            val orgId = call.requireParameter("organizationId").toLong()

            call.respond(
                HttpStatusCode.OK,
                organizationService.listProductsForOrganization(orgId, call.listQueryParameters())
                    .map { it.mapToApi() }
            )
        }

        post("products", postProduct) {
            val createProduct = call.receive<CreateProduct>()
            val orgId = call.requireParameter("organizationId").toLong()

            val createdProduct = organizationService.createProduct(createProduct.name, createProduct.description, orgId)

            call.respond(HttpStatusCode.Created, createdProduct.mapToApi())
        }

        route("secrets") {
            get(getSecretsByOrganizationId) {
                val id = call.requireParameter("organizationId").toLong()

                call.respond(
                    HttpStatusCode.OK,
                    secretService.listForOrganization(id, call.listQueryParameters()).map { it.mapToApi() }
                )
            }

            route("{secretName}") {
                get(getSecretByOrganizationIdAndName) {
                    val organizationId = call.requireParameter("organizationId").toLong()
                    val secretName = call.requireParameter("secretName")

                    secretService.getSecretByOrganizationIdAndName(organizationId, secretName)
                        ?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                        ?: call.respond(HttpStatusCode.NotFound)
                }

                patch(patchSecretByOrganizationIdAndName) {
                    val organizationId = call.requireParameter("organizationId").toLong()
                    val secretName = call.requireParameter("secretName")
                    val updateSecret = call.receive<UpdateSecret>()

                    call.respond(
                        HttpStatusCode.OK,
                        secretService.updateSecretByOrganizationAndName(
                            organizationId,
                            secretName,
                            updateSecret.value,
                            updateSecret.description
                        ).mapToApi()
                    )
                }

                delete(deleteSecretByOrganizationIdAndName) {
                    val organizationId = call.requireParameter("organizationId").toLong()
                    val secretName = call.requireParameter("secretName")

                    secretService.deleteSecretByOrganizationAndName(organizationId, secretName)

                    call.respond(HttpStatusCode.NoContent)
                }
            }

            post(postSecretForOrganization) {
                val organizationId = call.requireParameter("organizationId").toLong()
                val createSecret = call.receive<CreateSecret>()

                call.respond(
                    HttpStatusCode.Created,
                    secretService.createSecret(
                        createSecret.name,
                        createSecret.value,
                        createSecret.description,
                        organizationId,
                        null,
                        null
                    ).mapToApi()
                )
            }
        }
    }
}
