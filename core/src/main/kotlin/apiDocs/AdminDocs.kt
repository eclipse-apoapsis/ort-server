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

import org.eclipse.apoapsis.ortserver.api.v1.model.PostUser
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithSuperuserStatus
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

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
    summary = "Get users of the server"
    description = "Get users of the server. Fields available for sorting: 'username', 'firstName', 'lastName', " +
            "'email'. By default, users are sorted by username in ascending order."
    tags = listOf("Admin")

    request {
        standardListQueryParameters()

        queryParameter<String>("search") {
            description = "Filter users by a literal case-insensitive substring of their username, first name, " +
                    "last name, or email."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Successfully retrieved the users."
            jsonBody<PagedResponse<UserWithSuperuserStatus>> {
                example("Get users of the server") {
                    value = PagedResponse(
                        data = listOf(
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
                        ),
                        pagination = PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
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

val putSuperuser: RouteConfig.() -> Unit = {
    operationId = "putSuperuser"
    summary = "Make a user a superuser."
    tags = listOf("Admin")

    request {
        pathParameter<String>("username") {
            description = "The username of the user to make a superuser."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }

        HttpStatusCode.NotFound to {
            description = "The user does not exist."
        }
    }
}

val deleteSuperuser: RouteConfig.() -> Unit = {
    operationId = "deleteSuperuser"
    summary = "Revoke superuser status from a user."
    description = "Revoke the superuser status from a user. Note that a user cannot revoke their own superuser status."
    tags = listOf("Admin")

    request {
        pathParameter<String>("username") {
            description = "The username of the user to revoke superuser status from."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }

        HttpStatusCode.NotFound to {
            description = "The user does not exist."
        }

        HttpStatusCode.BadRequest to {
            description = "A superuser cannot remove their own superuser status."
        }
    }
}
