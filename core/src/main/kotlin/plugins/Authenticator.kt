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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.shared.authenticator.OrtServerAuthenticator
import org.eclipse.apoapsis.ortserver.shared.authenticator.infraSecretResolverFromConfig

import org.koin.ktor.ext.get

import org.ossreviewtoolkit.utils.authentication.OrtAuthenticator

/**
 * Install the [OrtServerAuthenticator] as the default authenticator of this JVM, so that outgoing requests to
 * external systems can be authenticated.
 *
 * Note that this has nothing to do with the authentication of incoming API requests, which is set up by
 * `configureAuthentication()`.
 */
fun Application.configureAuthenticator() {
    val configManager = get<ConfigManager>()

    OrtServerAuthenticator.install(infraSecretResolverFromConfig(configManager))

    monitor.subscribe(ApplicationStopped) {
        OrtAuthenticator.uninstall()
    }
}
