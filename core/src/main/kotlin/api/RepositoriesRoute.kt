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
import io.ktor.server.routing.route

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.api.v1.mapToModel
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.services.RepositoryService

fun Route.repositories() = route("repositories/{repositoryId}") {
    val repositoryService by inject<RepositoryService>()

    get {
        val id = call.requireParameter("repositoryId").toLong()

        repositoryService.getRepository(id)?.let { call.respond(HttpStatusCode.OK, it.mapToApi()) }
            ?: call.respond(HttpStatusCode.NotFound)
    }

    patch {
        val id = call.requireParameter("repositoryId").toLong()
        val updateRepository = call.receive<UpdateRepository>()

        val updatedRepository =
            repositoryService.updateRepository(id, updateRepository.type.mapToModel(), updateRepository.url)

        call.respond(HttpStatusCode.OK, updatedRepository.mapToApi())
    }

    delete {
        val id = call.requireParameter("repositoryId").toLong()

        repositoryService.deleteRepository(id)

        call.respond(HttpStatusCode.NoContent)
    }
}
