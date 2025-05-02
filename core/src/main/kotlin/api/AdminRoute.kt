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

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.api.v1.mapping.mapToApi
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateContentManagementSection
import org.eclipse.apoapsis.ortserver.components.authorization.requireAuthenticated
import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.core.apiDocs.deleteUserByUsername
import org.eclipse.apoapsis.ortserver.core.apiDocs.getSectionById
import org.eclipse.apoapsis.ortserver.core.apiDocs.getUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.patchSectionById
import org.eclipse.apoapsis.ortserver.core.apiDocs.postUsers
import org.eclipse.apoapsis.ortserver.core.apiDocs.runPermissionsSync
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.ContentManagementService
import org.eclipse.apoapsis.ortserver.services.UserService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireParameter

fun Route.admin(
    authorizationService: AuthorizationService,
    userService: UserService
) = route("admin") {
    route("sync-roles") {
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
        get(getUsers) {
            requireSuperuser()

            val users = userService.getUsers().map { user -> user.mapToApi() }
            call.respond(users)
        }

        post(postUsers) {
            requireSuperuser()

            val createUser = call.receive<CreateUser>()
            userService.createUser(
                username = createUser.username,
                firstName = createUser.firstName,
                lastName = createUser.lastName,
                email = createUser.email,
                password = createUser.password,
                temporary = createUser.temporary
            )

            call.respond(HttpStatusCode.Created)
        }

        delete(deleteUserByUsername) {
            requireSuperuser()

            val username = call.requireParameter("username")
            userService.deleteUser(username)

            call.respond(HttpStatusCode.NoContent)
        }
    }

    /**
     * For dynamic text sections.
     */
    route("content-management") {
        val contentManagementService by inject<ContentManagementService>()

        route("sections/{sectionId}") {
            get(getSectionById) {
                requireAuthenticated()

                val id = call.requireParameter("sectionId")

                val section = contentManagementService.findSectionById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                call.respond(HttpStatusCode.OK, section.mapToApi())
            }

            patch(patchSectionById) {
                requireSuperuser()

                val id = call.requireParameter("sectionId")
                val updateSection = call.receive<UpdateContentManagementSection>()

                val section = contentManagementService.updateSectionById(
                    id = id,
                    isEnabled = updateSection.isEnabled,
                    markdown = updateSection.markdown
                )

                call.respond(HttpStatusCode.OK, section.mapToApi())
            }
        }
    }
}
