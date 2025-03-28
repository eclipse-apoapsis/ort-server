/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core

import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser

/**
 * A Keycloak [User] to be used when testing authorization in integration tests. The user is supposed to have the
 * [superuser role][Superuser.ROLE_NAME].
 */
val SUPERUSER = User(
    id = UserId("superuser-id"),
    username = UserName("superuser"),
    firstName = "Super",
    lastName = "User",
    email = "superuser@example.org"
)

/**
 * The password of [SUPERUSER].
 */
const val SUPERUSER_PASSWORD = "superuser-password"

/**
 * A Keycloak [User] to be used when testing authorization in integration tests. The user is supposed to be
 * configured with the roles required for the test scenario.
 */
val TEST_USER = User(
    id = UserId("test-user-id"),
    username = UserName("test-user"),
    firstName = "Test",
    lastName = "User",
    email = "test-user@example.org"
)

/**
 * The password of [TEST_USER].
 */
const val TEST_USER_PASSWORD = "password"
