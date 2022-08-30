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
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.shared.models.api.Organization

fun Route.organizations() = route("organizations") {
    get {
        val organizations = OrganizationsRepository.listOrganizations()

        call.respond(HttpStatusCode.OK, organizations.map { it.mapToApiModel() })
    }

    get("/{organizationId}") {
        val id = call.requireParameter("organizationId").toLong()

        val organization = OrganizationsRepository.getOrganization(id)

        organization?.let { call.respond(HttpStatusCode.OK, it.mapToApiModel()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

    post {
        val organization = call.receive<Organization>()

        val createdOrganization =
            OrganizationsRepository.createOrganization(organization.name, organization.description)

        call.respond(HttpStatusCode.Created, createdOrganization.mapToApiModel())
    }

    put("/{organizationId}") {
        val organizationId = call.requireParameter("organizationId").toLong()
        val org = call.receive<Organization>()

        val updatedOrg = OrganizationsRepository.updateOrganization(organizationId, org.name, org.description)

        call.respond(HttpStatusCode.OK, updatedOrg.mapToApiModel())
    }

    delete("/{organizationId}") {
        val id = call.requireParameter("organizationId").toLong()

        OrganizationsRepository.deleteOrganization(id)

        call.respond(HttpStatusCode.NoContent)
    }
}
