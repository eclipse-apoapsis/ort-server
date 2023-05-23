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

import dasniko.testcontainers.keycloak.KeycloakContainer

import io.kotest.core.extensions.MountableExtension
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.spec.Spec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.RealmRepresentation

/**
 * A test extension for integration tests that need access to Keycloak. The extension sets up a
 * [Keycloak test container][KeycloakContainer] with the provided [realm] which defaults to [testRealm]. The realm can
 * be further configured using the [Keycloak admin client][Keycloak] when installing the extension. For example, a role
 * can be added like this:
 *
 * ```
 * install(KeycloakTestExtension()) {
 *     realm(TEST_REALM).roles().create(
 *         RoleRepresentation().apply {
 *             name = "test-role"
 *             description = "The role description."
 *         }
 *     )
 * }
 * ```
 *
 * The container is started on installation of the extension and stopped when the [Spec] is completed.
 */
class KeycloakTestExtension(private val realm: RealmRepresentation = testRealm) :
    MountableExtension<Keycloak, KeycloakContainer>, AfterSpecListener {
    private val keycloak = KeycloakContainer()

    override fun mount(configure: Keycloak.() -> Unit): KeycloakContainer {
        keycloak.start()
        keycloak.keycloakAdminClient.realms().create(realm)
        keycloak.keycloakAdminClient.configure()
        return keycloak
    }

    override suspend fun afterSpec(spec: Spec) {
        if (keycloak.isRunning) {
            withContext(Dispatchers.IO) { keycloak.stop() }
        }
    }
}
