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

import dasniko.testcontainers.keycloak.KeycloakContainer

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.Group
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.clients.keycloak.Role
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.User

import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation

/**
 * Create a [GroupRepresentation] from this [Group]. [Client roles][clientRoles] can be provided as a map from client
 * ids to lists of [role names][RoleName].
 */
fun Group.toGroupRepresentation(clientRoles: Map<String, List<RoleName>>? = null): GroupRepresentation =
    GroupRepresentation().also { group ->
        group.id = id.value
        group.name = name.value

        if (!clientRoles.isNullOrEmpty()) {
            group.clientRoles = clientRoles.mapValues { (_, roles) -> roles.map { it.value } }
        }
    }

/**
 * Create a [RoleRepresentation] from this [Role]. [Composite client roles][compositeClientRoles] can be provided as a
 * map from client ids to lists of [role names][RoleName].
 */
fun Role.toRoleRepresentation(compositeClientRoles: Map<String, List<RoleName>>? = null): RoleRepresentation =
    RoleRepresentation().also { role ->
        role.id = id.value
        role.name = name.value
        role.description = description

        if (!compositeClientRoles.isNullOrEmpty()) {
            role.isComposite = true
            role.composites = RoleRepresentation.Composites().apply {
                client = compositeClientRoles.mapValues { (_, roles) -> roles.map { it.value } }
            }
        }
    }

/**
 * Create a [UserRepresentation] from this [User] with an optional [password]. [Client roles][clientRoles] can be
 * provided as a map from client ids to lists of [role names][RoleName].
 */
fun User.toUserRepresentation(
    password: String? = null,
    clientRoles: Map<String, List<RoleName>>? = null
): UserRepresentation =
    UserRepresentation().also { user ->
        user.id = id.value
        user.username = username.value
        user.firstName = firstName
        user.lastName = lastName
        user.email = email
        user.isEnabled = true

        if (password != null) {
            user.credentials = listOf(
                CredentialRepresentation().apply {
                    type = CredentialRepresentation.PASSWORD
                    value = password
                }
            )
        }

        if (!clientRoles.isNullOrEmpty()) {
            user.clientRoles = clientRoles.mapValues { (_, roles) -> roles.map { it.value } }
        }
    }

/**
 * Create a [KeycloakClientConfiguration] for the [testRealm] and this [KeycloakContainer]. Support customization of
 * credentials via the given [secret], [user], and [clientId].
 */
fun KeycloakContainer.createKeycloakClientConfigurationForTestRealm(
    secret: String = TEST_REALM_ADMIN_PASSWORD,
    user: String? = TEST_REALM_ADMIN_USERNAME,
    clientId: String = TEST_CLIENT
) =
    KeycloakClientConfiguration(
        apiUrl = "${authServerUrl}admin/realms/$TEST_REALM",
        clientId = clientId,
        accessTokenUrl = "${authServerUrl}realms/$TEST_REALM/protocol/openid-connect/token",
        apiUser = user,
        apiSecret = secret,
        subjectClientId = TEST_SUBJECT_CLIENT
    )

/**
 * Create a map containing configuration properties for the [testRealm] and this [KeycloakContainer]. The map can be
 * used when creating a [KeycloakClient] from configuration.
 */
fun KeycloakContainer.createKeycloakConfigMapForTestRealm() =
    createKeycloakClientConfigurationForTestRealm().let { config ->
        mapOf(
            "keycloak.apiUrl" to config.apiUrl,
            "keycloak.clientId" to config.clientId,
            "keycloak.accessTokenUrl" to config.accessTokenUrl,
            "keycloak.apiUser" to config.apiUser.orEmpty(),
            "keycloak.apiSecret" to config.apiSecret,
            "keycloak.subjectClientId" to config.subjectClientId
        )
    }

/**
 * Create a [KeycloakClient] for the [testRealm] and this [KeycloakContainer].
 */
fun KeycloakContainer.createKeycloakClientForTestRealm() =
    DefaultKeycloakClient.create(createKeycloakClientConfigurationForTestRealm(), Json { ignoreUnknownKeys = true })
