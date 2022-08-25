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

package org.ossreviewtoolkit.server.core.client

internal val groupOrgA = Group(
    id = "e6a8bf53-32e1-43d9-9962-ece3863fe4ce",
    name = "Organization-A",
    subGroups = emptySet()
)

private val subGroupOrgB1 = Group(
    id = "0e5b6055-adc4-47c6-97a9-a2adf4be96f0",
    name = "Sub-Orga-B1",
    subGroups = emptySet()
)

private val subGroupOrgB2 = Group(
    id = "1e7526ff-1548-4621-824c-290540cd6264",
    name = "Sub-Orga-B2",
    subGroups = emptySet()
)

internal val groupOrgB = Group(
    id = "2ec7f144-1810-4c4d-84a2-e5d026388b92",
    name = "Organization-B",
    subGroups = setOf(subGroupOrgB1, subGroupOrgB2)
)

internal val groupOrgC = Group(
    id = "db48d4f2-ac1e-43da-af9e-b0c1273e97d3",
    name = "Organization-C",
    subGroups = emptySet()
)

internal val adminRole = Role(
    id = "d9e21fcd-807e-4336-9ccc-e6a84137d530",
    name = "ADMIN",
    description = "This is a test admin role."
)

internal val visitorRole = Role(
    id = "c3e85536-548b-4c9f-bb40-d5686c362819",
    name = "VISITOR",
    description = "This is a test visitor role."
)

internal val adminUser = User(
    id = "002a40cc-3bef-4c8e-8045-ac7d00f36b19",
    username = "admin",
    firstName = "Admin",
    lastName = "User",
    email = "realm-admin@org.com"
)

internal val ortAdminUser = User(
    id = "28414e51-b0bb-42eb-8b42-f0b4740f4f44",
    username = "ort-test-admin",
    firstName = "Test",
    lastName = "User",
    email = "admin@org.com"
)

internal val visitorUser = User(
    id = "cc07c45f-11e9-4c9b-8ff0-873c93351d42",
    username = "visitor",
    firstName = "Test",
    lastName = "Visitor",
    email = "visitor@org.com"
)

/** The name of the realm containing the test data. */
internal const val REALM = "ort"

/** The clientId of the Keycloak test client. */
internal const val CLIENT_ID = "test-server"

/** The internal ID of the Keycloak test client (not clientId). */
internal const val INTERNAL_ID = "d6fa01bd-525d-4b3c-aa75-c3ff0bb1bd1c"

/** The API user to access the Keycloak REST API. */
internal const val API_USER = "ort-test-admin"

/** The secret used by the API user to access the Keycloak REST API. */
internal const val API_SECRET = "secret"
