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

package org.eclipse.apoapsis.ortserver.clients.keycloak.test

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.User
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName

import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.RealmRepresentation

/** The name of the test realm. */
const val TEST_REALM = "test-realm"

/** The name of the test client. */
const val TEST_CLIENT = "test-client"

/** The username of the admin user for the [TEST_REALM]. */
const val TEST_REALM_ADMIN_USERNAME = "realm-admin"

/** The password of the admin user for the [TEST_REALM]. */
const val TEST_REALM_ADMIN_PASSWORD = "password"

/** The admin user for the [TEST_REALM]. */
val testRealmAdmin = User(
    id = UserId("28414e51-b0bb-42eb-8b42-f0b4740f4f44"),
    username = UserName(TEST_REALM_ADMIN_USERNAME),
    firstName = "Realm",
    lastName = "Admin",
    email = "realm.admin@example.org"
)

/**
 * Name of a test client that is confidential. Interaction with this client needs to be done using the grant type
 * "client credentials".
 */
const val TEST_CONFIDENTIAL_CLIENT = "test-confidential-client"

/** A secret used by the confidential test client. */
const val TEST_CLIENT_SECRET = "abcdefghijklmnopqrstuvwxyz"

/** The name of a test client that is subject to role manipulations. */
const val TEST_SUBJECT_CLIENT = "subjectClient"

/**
 * A test [realm configuration][RealmRepresentation] that creates a [realm][TEST_REALM] with two clients that can be
 * used to access it:
 * - [TEST_CLIENT] is a client supporting the direct access grant flow for an [admin user][testRealmAdmin]].
 *   The [KeycloakClient] can be configured to authenticate to this client using [TEST_REALM_ADMIN_USERNAME] and
 *   [TEST_REALM_ADMIN_PASSWORD].
 * - [TEST_CONFIDENTIAL_CLIENT] is a confidential client that supports the client credentials flow with
 *   [TEST_CLIENT_SECRET] as secret. Note: To use it, it must be assigned corresponding client roles.
 *
 * There is one additional [client][TEST_SUBJECT_CLIENT] that is the subject of role manipulations. It is not used for
 * authentication, but role manipulations are done on this client.
 */
val testRealm = RealmRepresentation().apply {
    realm = TEST_REALM
    isEnabled = true

    clients = listOf(
        ClientRepresentation().apply {
            id = TEST_CLIENT
            isEnabled = true
            isPublicClient = true
            isDirectAccessGrantsEnabled = true
        },
        ClientRepresentation().apply {
            id = TEST_CONFIDENTIAL_CLIENT
            isEnabled = true
            isPublicClient = false
            isServiceAccountsEnabled = true
            secret = TEST_CLIENT_SECRET
        },
        ClientRepresentation().apply {
            id = TEST_SUBJECT_CLIENT
            isEnabled = true
            isPublicClient = true
            isStandardFlowEnabled = true
        }
    )

    users = listOf(
        testRealmAdmin.toUserRepresentation(
            password = TEST_REALM_ADMIN_PASSWORD,
            clientRoles = mapOf(
                "realm-management" to listOf(RoleName("realm-admin"))
            )
        )
    )
}
