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

package org.eclipse.apoapsis.ortserver.clients.keycloak

import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.testRealm
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.toGroupRepresentation
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.toRoleRepresentation
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.toUserRepresentation

import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RolesRepresentation

internal val groupOrgA = Group(
    id = GroupId("e6a8bf53-32e1-43d9-9962-ece3863fe4ce"),
    name = GroupName("Organization-A")
)

internal val groupOrgB = Group(
    id = GroupId("2ec7f144-1810-4c4d-84a2-e5d026388b92"),
    name = GroupName("Organization-B")
)

internal val groupOrgC = Group(
    id = GroupId("db48d4f2-ac1e-43da-af9e-b0c1273e97d3"),
    name = GroupName("Organization-C")
)

internal val adminRole = Role(
    id = RoleId("d9e21fcd-807e-4336-9ccc-e6a84137d530"),
    name = RoleName("ADMIN"),
    description = "This is a test admin role."
)

internal val visitorRole = Role(
    id = RoleId("c3e85536-548b-4c9f-bb40-d5686c362819"),
    name = RoleName("VISITOR"),
    description = "This is a test visitor role."
)

internal val compositeRole = Role(
    id = RoleId("c3e02976-7abe-4a41-86f0-c8f012d0ca3b"),
    name = RoleName("COMPOSITE"),
    description = "This is a test composite role."
)

internal val adminUser = User(
    id = UserId("002a40cc-3bef-4c8e-8045-ac7d00f36b19"),
    username = UserName("admin"),
    firstName = "Admin",
    lastName = "User",
    email = "realm-admin@org.com"
)

internal val visitorUser = User(
    id = UserId("cc07c45f-11e9-4c9b-8ff0-873c93351d42"),
    username = UserName("visitor"),
    firstName = "Test",
    lastName = "Visitor",
    email = "visitor@org.com"
)

val clientTestRealm = RealmRepresentation().apply {
    realm = testRealm.realm
    isEnabled = true

    clients = testRealm.clients

    roles = RolesRepresentation().apply {
        client = mapOf(
            TEST_CLIENT to listOf(
                adminRole.toRoleRepresentation()
            ),
            TEST_SUBJECT_CLIENT to listOf(
                visitorRole.toRoleRepresentation(
                    compositeClientRoles = mapOf(TEST_SUBJECT_CLIENT to listOf(compositeRole.name))
                ),
                compositeRole.toRoleRepresentation()
            )
        )
    }

    users = testRealm.users + listOf(
        adminUser.toUserRepresentation(
            clientRoles = mapOf(TEST_CLIENT to listOf(adminRole.name))
        ),
        visitorUser.toUserRepresentation(
            clientRoles = mapOf(TEST_SUBJECT_CLIENT to listOf(visitorRole.name))
        )
    )

    groups = listOf(
        groupOrgA.toGroupRepresentation(
            clientRoles = mapOf(TEST_CLIENT to listOf(adminRole.name))
        ),
        groupOrgB.toGroupRepresentation(
            clientRoles = mapOf(TEST_SUBJECT_CLIENT to listOf(visitorRole.name))
        ),
        groupOrgC.toGroupRepresentation()
    )
}
