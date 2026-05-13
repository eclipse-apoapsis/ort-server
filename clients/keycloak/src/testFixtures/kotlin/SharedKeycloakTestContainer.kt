/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

/**
 * A singleton that manages a shared [KeycloakContainer] across all test specs within a JVM process. The container is
 * started lazily on the first access to [container] and stopped via a JVM shutdown hook when the process exits.
 *
 * Using this shared container avoids the overhead of starting a new Keycloak instance for every test spec. Realm
 * isolation between specs is still ensured by [KeycloakTestExtension], which creates and removes the test realm around
 * each spec (or each test when [KeycloakTestExtension.createRealmPerTest] is `true`).
 */
internal object SharedKeycloakTestContainer {
    private val containerLazy: Lazy<KeycloakContainer> = lazy {
        KeycloakContainer("quay.io/keycloak/keycloak:26.6.0").also { it.start() }
    }

    val container: KeycloakContainer by containerLazy

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (containerLazy.isInitialized() && container.isRunning) {
                    container.stop()
                }
            }
        )
    }
}
