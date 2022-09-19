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

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository

fun Route.organizations() = route("organizations") {
    val organizationRepository by inject<OrganizationRepository>()
    val productRepository by inject<ProductRepository>()

    get {
        val organizations = organizationRepository.list()

        call.respond(HttpStatusCode.OK, organizations.map { it.mapToApi() })
    }

    post {
        val createOrganization = call.receive<CreateOrganization>()

        val createdOrganization = organizationRepository.create(createOrganization.name, createOrganization.description)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApi())
    }

    route("{organizationId}") {
        get {
            val id = call.requireParameter("organizationId").toLong()

            val organization = organizationRepository.get(id)

            organization?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
                ?: call.respond(HttpStatusCode.NotFound)
        }

        patch {
            val organizationId = call.requireParameter("organizationId").toLong()
            val org = call.receive<UpdateOrganization>()

            val updatedOrg = organizationRepository.update(organizationId, org.name, org.description)

            call.respond(HttpStatusCode.OK, updatedOrg.mapToApi())
        }

        delete {
            val id = call.requireParameter("organizationId").toLong()

            organizationRepository.delete(id)

            call.respond(HttpStatusCode.NoContent)
        }

        get("products") {
            val orgId = call.requireParameter("organizationId").toLong()

            call.respond(HttpStatusCode.OK, productRepository.listForOrganization(orgId).map { it.mapToApi() })
        }

        post("products") {
            val createProduct = call.receive<CreateProduct>()
            val orgId = call.requireParameter("organizationId").toLong()

            val createdProduct = productRepository.create(createProduct.name, createProduct.description, orgId)

            call.respond(HttpStatusCode.Created, createdProduct.mapToApi())
        }
    }
}
