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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktoropenapi.config.RouteConfig

import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.ContentManagementSection
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchSection
import org.eclipse.apoapsis.ortserver.api.v1.model.PostUser
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithSuperuserStatus
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody

val runPermissionsSync: RouteConfig.() -> Unit = {
    operationId = "runPermissionsSync"
    summary = "Trigger the synchronization of Keycloak roles"
    tags = listOf("Admin")

    request {
    }

    response {
        HttpStatusCode.Accepted to {
            description = "Success."
        }

        HttpStatusCode.Unauthorized to {
            description = "Unauth."
        }
    }
}

val getUsers: RouteConfig.() -> Unit = {
    operationId = "getUsers"
    summary = "Get all users of the server"
    tags = listOf("Admin")

    request {
    }

    response {
        HttpStatusCode.OK to {
            description = "Successfully retrieved the users."
            jsonBody<List<UserWithSuperuserStatus>> {
                example("Get all users of the server") {
                    value = listOf(
                        UserWithSuperuserStatus(
                            user = User(
                                username = "user1",
                                firstName = "First1",
                                lastName = "Last1",
                                email = "user1@mail.com"
                            ),
                            isSuperuser = true
                        ),
                        UserWithSuperuserStatus(
                            user = User(
                                username = "user2",
                                firstName = "First2",
                                lastName = "Last2",
                                email = "user2@mail.com"
                            ),
                            isSuperuser = false
                        )
                    )
                }
            }
        }
    }
}

val postUser: RouteConfig.() -> Unit = {
    operationId = "postUser"
    summary = "Create a user, possibly with a password"
    tags = listOf("Admin")

    request {
        jsonBody<PostUser> {
            example("Create User") {
                value = PostUser(
                    username = "newUser",
                    firstName = "First",
                    lastName = "Last",
                    email = "first.last@mail.com",
                    password = "password",
                    temporary = true
                )
                description = "temporary=true means the password is for one-time use only and needs to be changed " +
                        "on first login. If password is not set, temporary is ignored."
            }
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Successfully created the user."
        }

        // Note: Keycloak doesn't distinguish technical from logical errors; it just returns 500 for both.
        HttpStatusCode.InternalServerError to {
            description = "A user with the same username already exists."
        }
    }
}

val deleteUser: RouteConfig.() -> Unit = {
    operationId = "deleteUser"
    summary = "Delete a user from the server"
    tags = listOf("Admin")

    request {
        queryParameter<String>("username") {
            description = "The username of the user to delete."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully deleted the user."
        }

        HttpStatusCode.InternalServerError to {
            description = "The user does not exist."
        }
    }
}

val getSection: RouteConfig.() -> Unit = {
    operationId = "getSection"
    summary = "Get a dynamic UI text section by ID."
    tags = listOf("Admin")

    request {
        pathParameter<String>("sectionId") {
            description = "The section's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<ContentManagementSection> {
                example("Get section") {
                    value = ContentManagementSection(
                        id = "footer",
                        isEnabled = true,
                        markdown = "## Footer",
                        updatedAt = CREATED_AT
                    )
                }
            }
        }
    }
}

val patchSection: RouteConfig.() -> Unit = {
    operationId = "patchSection"
    summary = "Update a dynamic UI text section by ID."
    tags = listOf("Admin")

    request {
        pathParameter<String>("sectionId") {
            description = "The section's ID."
        }

        jsonBody<PatchSection> {
            example("Update Section") {
                value = PatchSection(
                    isEnabled = true,
                    markdown = "# This is a new footer"
                )
            }
            description = "Set the values that should be updated."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<ContentManagementSection> {
                example("Update Section") {
                    value = ContentManagementSection(
                        id = "footer",
                        isEnabled = true,
                        markdown = "## Footer",
                        updatedAt = CREATED_AT
                    )
                }
            }
        }
    }
}
