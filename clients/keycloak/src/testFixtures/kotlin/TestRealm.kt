/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.clients.keycloak.test

import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.User
import org.ossreviewtoolkit.server.clients.keycloak.UserId
import org.ossreviewtoolkit.server.clients.keycloak.UserName

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
 * A test [realm configuration][RealmRepresentation] that creates a [realm][TEST_REALM] with a [client][TEST_CLIENT] and
 * an [admin user][testRealmAdmin]]. The [KeycloakClient] can be configured to authenticate to this realm using
 * [TEST_REALM_ADMIN_USERNAME] and [TEST_REALM_ADMIN_PASSWORD].
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
        }
    )

    users = listOf(
        UserRepresentation().apply {
            id = testRealmAdmin.id.value
            username = testRealmAdmin.username.value
            firstName = testRealmAdmin.firstName
            lastName = testRealmAdmin.lastName
            email = testRealmAdmin.email
            isEnabled = true
            credentials = listOf(
                CredentialRepresentation().apply {
                    type = CredentialRepresentation.PASSWORD
                    value = TEST_REALM_ADMIN_PASSWORD
                }
            )
            clientRoles = mapOf(
                "realm-management" to listOf(
                    "realm-admin"
                )
            )
        }
    )
}
