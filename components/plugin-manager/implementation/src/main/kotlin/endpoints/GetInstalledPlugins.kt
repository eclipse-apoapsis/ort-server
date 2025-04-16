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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints

import io.github.smiley4.ktoropenapi.get

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.requireSuperuser
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOption
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.Plugins
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory
import org.ossreviewtoolkit.plugins.api.PluginDescriptor as OrtPluginDescriptor
import org.ossreviewtoolkit.plugins.api.PluginOption as OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginOptionType as OrtPluginOptionType
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagemanagers.node.npm.NpmFactory
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

fun Route.getInstalledPlugins() = get("admin/plugins", {
    operationId = "GetInstalledPlugins"
    summary = "Get installed ORT plugins"
    description = "Get a list with detailed information about all installed ORT plugins."
    tags = listOf("Plugins")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            body<List<PluginDescriptor>> {
                mediaTypes = setOf(ContentType.Application.Json)
                example("List of ORT plugins") {
                    value = listOf(
                        VulnerableCodeFactory.descriptor.mapToApi(PluginType.ADVISOR, true),
                        NpmFactory.descriptor.mapToApi(PluginType.PACKAGE_MANAGER, false)
                    )
                }
            }
        }
    }
}) {
    val db by inject<Database>()

    requireSuperuser()

    fun isEnabled(pluginType: PluginType, pluginId: String) = db.transaction {
        Plugins.select(Plugins.enabled)
            .where { Plugins.pluginType eq pluginType and (Plugins.pluginId eq pluginId) }
            .firstOrNull()?.get(Plugins.enabled) ?: true
    }

    val advisors = AdviceProviderFactory.ALL.values.map {
        it.descriptor.mapToApi(PluginType.ADVISOR, isEnabled(PluginType.ADVISOR, it.descriptor.id))
    }
    val packageConfigurationProviders = PackageConfigurationProviderFactory.ALL.values.map {
        it.descriptor.mapToApi(
            PluginType.PACKAGE_CONFIGURATION_PROVIDER,
            isEnabled(PluginType.PACKAGE_CONFIGURATION_PROVIDER, it.descriptor.id)
        )
    }
    val packageCurationProviders = PackageCurationProviderFactory.ALL.values.map {
        it.descriptor.mapToApi(
            PluginType.PACKAGE_CURATION_PROVIDER,
            isEnabled(PluginType.PACKAGE_CURATION_PROVIDER, it.descriptor.id)
        )
    }
    val packageManagers = PackageManagerFactory.ALL.values.map {
        it.descriptor.mapToApi(PluginType.PACKAGE_MANAGER, isEnabled(PluginType.PACKAGE_MANAGER, it.descriptor.id))
    }
    val reporters = ReporterFactory.ALL.values.map {
        it.descriptor.mapToApi(PluginType.REPORTER, isEnabled(PluginType.REPORTER, it.descriptor.id))
    }
    val scanners = ScannerWrapperFactory.ALL.values.map {
        it.descriptor.mapToApi(PluginType.SCANNER, isEnabled(PluginType.SCANNER, it.descriptor.id))
    }

    val allPlugins = advisors +
            packageConfigurationProviders +
            packageCurationProviders +
            packageManagers +
            reporters +
            scanners

    call.respond(HttpStatusCode.OK, allPlugins)
}

internal fun OrtPluginDescriptor.mapToApi(type: PluginType, enabled: Boolean) = PluginDescriptor(
    id = id,
    type = type,
    displayName = displayName,
    description = description,
    options = options.map { it.mapToApi() },
    enabled = enabled
)

internal fun OrtPluginOption.mapToApi() = PluginOption(
    name = name,
    description = description,
    type = type.mapToApi(),
    defaultValue = defaultValue,
    isNullable = isNullable,
    isRequired = isRequired
)

internal fun OrtPluginOptionType.mapToApi() = when (this) {
    OrtPluginOptionType.BOOLEAN -> PluginOptionType.BOOLEAN
    OrtPluginOptionType.INTEGER -> PluginOptionType.INTEGER
    OrtPluginOptionType.LONG -> PluginOptionType.LONG
    OrtPluginOptionType.SECRET -> PluginOptionType.SECRET
    OrtPluginOptionType.STRING -> PluginOptionType.STRING
    OrtPluginOptionType.STRING_LIST -> PluginOptionType.STRING_LIST
}
