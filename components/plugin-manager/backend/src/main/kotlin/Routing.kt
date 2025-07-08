/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.addTemplateToOrganization
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.createTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.deleteTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.disableGlobalTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.disablePlugin
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.enableGlobalTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.enablePlugin
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.getInstalledPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.getTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.getTemplates
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.removeTemplateFromOrganization
import org.eclipse.apoapsis.ortserver.components.pluginmanager.routes.updateTemplateOptions

/** Add routes for all plugin-manager endpoints. */
fun Route.pluginManagerRoutes(
    eventStore: PluginEventStore,
    pluginService: PluginService,
    pluginTemplateService: PluginTemplateService
) {
    addTemplateToOrganization(pluginTemplateService)
    createTemplate(pluginTemplateService)
    deleteTemplate(pluginTemplateService)
    disableGlobalTemplate(pluginTemplateService)
    disablePlugin(eventStore)
    enableGlobalTemplate(pluginTemplateService)
    enablePlugin(eventStore)
    getInstalledPlugins(pluginService)
    getTemplate(pluginTemplateService)
    getTemplates(pluginTemplateService)
    removeTemplateFromOrganization(pluginTemplateService)
    updateTemplateOptions(pluginTemplateService)
}
