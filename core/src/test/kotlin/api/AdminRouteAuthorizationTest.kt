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

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateUser
import org.eclipse.apoapsis.ortserver.core.TEST_USER
import org.eclipse.apoapsis.ortserver.model.authorization.Superuser
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class AdminRouteAuthorizationTest : AbstractAuthorizationTest({
    tags(Integration)

    val testUsername = "test123"
    val testFirstName = "FirstName"
    val testLastName = "LastName"
    val testEmail = "test@test.com"
    val testPassword = "password123"
    val testTemporary = true

    "GET /admin/sync-roles" should {
        "require superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.Accepted) {
                get("/api/v1/admin/sync-roles")
            }
        }
    }

    "GET /admin/users" should {
        "require superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.OK) {
                get("/api/v1/admin/users")
            }
        }
    }

    "POST /admin/users" should {
        "require superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.Created) {
                post("/api/v1/admin/users") {
                    setBody(
                        CreateUser(
                            username = testUsername,
                            firstName = testFirstName,
                            lastName = testLastName,
                            email = testEmail,
                            password = testPassword,
                            temporary = testTemporary
                        )
                    )
                }
            }
        }
    }

    "DELETE /admin/users" should {
        "require superuser role" {
            requestShouldRequireRole(Superuser.ROLE_NAME, HttpStatusCode.NoContent) {
                delete("/api/v1/admin/users") {
                    parameter("username", TEST_USER.username.value)
                }
            }
        }
    }
})
