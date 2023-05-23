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

import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.Group
import org.ossreviewtoolkit.server.clients.keycloak.Role
import org.ossreviewtoolkit.server.clients.keycloak.RoleName
import org.ossreviewtoolkit.server.clients.keycloak.User

/** Create a [GroupRepresentation] from this [Group]. */
fun Group.toGroupRepresentation(): GroupRepresentation =
    GroupRepresentation().also { group ->
        group.id = id.value
        group.name = name.value
        group.subGroups = subGroups.map { it.toGroupRepresentation() }
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
