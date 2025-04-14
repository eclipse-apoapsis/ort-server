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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

import org.eclipse.apoapsis.ortserver.components.authorization.SecurityConfigurations
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints.disablePlugin
import org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints.enablePlugin
import org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints.getInstalledPlugins
import org.eclipse.apoapsis.ortserver.core.api.admin
import org.eclipse.apoapsis.ortserver.core.api.authentication
import org.eclipse.apoapsis.ortserver.core.api.downloads
import org.eclipse.apoapsis.ortserver.core.api.healthChecks
import org.eclipse.apoapsis.ortserver.core.api.organizations
import org.eclipse.apoapsis.ortserver.core.api.products
import org.eclipse.apoapsis.ortserver.core.api.repositories
import org.eclipse.apoapsis.ortserver.core.api.runs
import org.eclipse.apoapsis.ortserver.core.api.versions

import org.koin.ktor.ext.get

fun Application.configureRouting() {
    routing {
        route("api/v1") {
            authentication()
            healthChecks()
            downloads()
            authenticate(SecurityConfigurations.token) {
                disablePlugin(get())
                enablePlugin(get())
                getInstalledPlugins()
                admin()
                organizations()
                products()
                repositories()
                runs()
                versions()
            }
        }
    }
}
