/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.github.smiley4.ktorswaggerui.dsl.routing.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateUser
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserByUsername
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.postUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.runPermissionsSync
import org.eclipse.apoapsis.ortserver.core.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.UserService

import org.koin.ktor.ext.inject

fun Route.admin() = route("admin") {
    route("sync-roles") {
        val authorizationService by inject<AuthorizationService>()

        get(runPermissionsSync) {
            requireSuperuser()

            withContext(Dispatchers.IO) {
                launch {
                    authorizationService.ensureSuperuserAndSynchronizeRolesAndPermissions()
                }

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
    /**
     * For CRUD operations for users.
     */
    route("users") {
        val userService by inject<UserService>()

        get(getUsers) {
            requireSuperuser()

            val users = userService.getUsers().map { user -> user.mapToApi() }
            call.respond(users)
        }

        post(postUsers) {
            requireSuperuser()

            val createUser = call.receive<CreateUser>()
            userService.createUser(createUser.username, createUser.password, createUser.temporary)

            call.respond(HttpStatusCode.Created)
        }

        delete(deleteUserByUsername) {
            requireSuperuser()

            val username = call.requireParameter("username")
            userService.deleteUser(username)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
