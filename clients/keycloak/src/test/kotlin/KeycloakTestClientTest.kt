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

package org.eclipse.apoapsis.ortserver.clients.keycloak

import org.eclipse.apoapsis.ortserver.clients.keycloak.test.KeycloakTestClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.test.testRealmAdmin

class KeycloakTestClientTest : AbstractKeycloakClientTest() {
    override val client = KeycloakTestClient(
        groups = mutableSetOf(groupOrgA, groupOrgB, groupOrgC),
        groupClientRoles = mutableMapOf(
            groupOrgA.id to emptySet(),
            groupOrgB.id to setOf(visitorRole.id),
            groupOrgC.id to emptySet()
        ),
        roles = mutableSetOf(visitorRole, compositeRole),
        roleComposites = mutableMapOf(
            visitorRole.id to setOf(compositeRole.id),
            compositeRole.id to emptySet()
        ),
        users = mutableSetOf(testRealmAdmin, adminUser, visitorUser),
        userGroups = mutableMapOf(
            adminUser.id to emptySet(),
            visitorUser.id to setOf(groupOrgB.id)
        )
    )
}
