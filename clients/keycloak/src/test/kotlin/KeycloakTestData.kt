/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.clients.keycloak

import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RolesRepresentation

import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.TEST_SUBJECT_CLIENT
import org.ossreviewtoolkit.server.clients.keycloak.test.testRealm
import org.ossreviewtoolkit.server.clients.keycloak.test.toGroupRepresentation
import org.ossreviewtoolkit.server.clients.keycloak.test.toRoleRepresentation
import org.ossreviewtoolkit.server.clients.keycloak.test.toUserRepresentation

internal val groupOrgA = Group(
    id = GroupId("e6a8bf53-32e1-43d9-9962-ece3863fe4ce"),
    name = GroupName("Organization-A"),
    subGroups = emptySet()
)

internal val subGroupOrgB1 = Group(
    id = GroupId("0e5b6055-adc4-47c6-97a9-a2adf4be96f0"),
    name = GroupName("Sub-Orga-B1"),
    subGroups = emptySet()
)

private val subGroupOrgB2 = Group(
    id = GroupId("1e7526ff-1548-4621-824c-290540cd6264"),
    name = GroupName("Sub-Orga-B2"),
    subGroups = emptySet()
)

internal val groupOrgB = Group(
    id = GroupId("2ec7f144-1810-4c4d-84a2-e5d026388b92"),
    name = GroupName("Organization-B"),
    subGroups = setOf(subGroupOrgB1, subGroupOrgB2)
)

internal val groupOrgC = Group(
    id = GroupId("db48d4f2-ac1e-43da-af9e-b0c1273e97d3"),
    name = GroupName("Organization-C"),
    subGroups = emptySet()
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
