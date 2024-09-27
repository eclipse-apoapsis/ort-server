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

import io.kotest.assertions.ktor.client.shouldHaveStatus

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateUser
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class UsersRouteIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    val testUsername = "test123"
    val testPassword = "password123"
    val testTemporary = true

    "POST /admin/users" should {
        "create a new user" {
            integrationTestApplication {
                val user = CreateUser(testUsername, testPassword, testTemporary)

                val response = superuserClient.post("/api/v1/admin/users") {
                    setBody(user)
                }

                response shouldHaveStatus HttpStatusCode.Created
            }
        }
        "respond with an internal error if the user already exists" {
            integrationTestApplication {
                val user = CreateUser(testUsername, testPassword, testTemporary)

                superuserClient.post("/api/v1/admin/users") {
                    setBody(user)
                }
                val response = superuserClient.post("/api/v1/admin/users") {
                    setBody(user)
                }

                response shouldHaveStatus HttpStatusCode.InternalServerError
            }
        }
    }
})
