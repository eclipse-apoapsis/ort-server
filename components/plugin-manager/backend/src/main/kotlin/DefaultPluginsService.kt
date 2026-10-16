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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository

import org.jetbrains.exposed.v1.jdbc.Database

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration

import org.slf4j.LoggerFactory

/**
 * A service for managing the plugins that are enabled by default if a run does not explicitly configure which plugins
 * to use.
 *
 * Default plugins can be configured globally and per organization. For each [DefaultPluginsCategory], the defaults
 * are resolved in the following order: The defaults configured for the organization, the global defaults, and finally
 * the [built-in defaults][getBuiltInDefaults]. If no project scanners are configured, the package scanners are used as
 * built-in defaults for project scanners.
 */
class DefaultPluginsService(
    private val db: Database,
    private val eventStore: DefaultPluginsEventStore,
    private val pluginService: PluginService,
    private val pluginTemplateService: PluginTemplateService,
    private val organizationRepository: OrganizationRepository,
    private val adminConfigService: AdminConfigService,
    private val configManager: ConfigManager
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DefaultPluginsService::class.java)

        /**
         * Return the built-in default plugin IDs for the given [category] that are used if no defaults are configured.
         * These match the defaults that were used before default plugins could be configured. Return `null` for
         * [project scanners][DefaultPluginsCategory.PROJECT_SCANNER], because they default to the effective package
         * scanners.
         */
        internal fun getBuiltInDefaults(category: DefaultPluginsCategory): List<String>? =
            when (category) {
                DefaultPluginsCategory.ADVISOR -> listOf("OSV", "VulnerableCode")
                DefaultPluginsCategory.PACKAGE_CONFIGURATION_PROVIDER -> emptyList()
                DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER -> emptyList()
                DefaultPluginsCategory.PACKAGE_MANAGER -> AnalyzerConfiguration().enabledPackageManagers
                DefaultPluginsCategory.PROJECT_SCANNER -> null
                DefaultPluginsCategory.REPORTER -> listOf("CycloneDX", "SpdxDocument", "WebApp")
                DefaultPluginsCategory.SCANNER -> listOf("ScanCode")
            }
    }

    /**
     * Return the default plugins of the given [category] that apply to the given [organizationId], or the global
     * defaults if [organizationId] is `null`. The returned plugins are not filtered by their availability, so they
     * can contain plugins that are currently not available. Use [resolveDefaultPlugins] to get the plugins that should
     * actually be used for a run.
     */
    suspend fun getDefaultPlugins(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory
    ): Result<DefaultPlugins, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId).map {
            getEffectiveDefaults(organizationId, category)
        }
    }

    /**
     * Return the default plugins of all categories that apply to the given [organizationId], or the global defaults
     * if [organizationId] is `null`. See [getDefaultPlugins] for details.
     */
    suspend fun getAllDefaultPlugins(
        organizationId: OrganizationId?
    ): Result<List<DefaultPlugins>, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId).map {
            DefaultPluginsCategory.entries.map { category -> getEffectiveDefaults(organizationId, category) }
        }
    }

    /**
     * Resolve the default plugins of all categories for the organization with the given [organizationId] using the
     * admin configuration from the given config [context]. Plugins that are not available for the organization and
     * report definitions which do not exist in the config [context] are removed.
     */
    suspend fun resolveDefaultPlugins(
        organizationId: OrganizationId,
        context: ResolvedConfigContext
    ): Result<List<DefaultPlugins>, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId).map {
            val reportDefinitions = getReportDefinitions(context)

            DefaultPluginsCategory.entries.map { category ->
                val defaults = getEffectiveDefaults(organizationId, category)
                val availableIds = getAvailablePluginIds(
                    category.pluginType,
                    organizationId,
                    reportDefinitions.names,
                    reportDefinitions.replacedPluginIds
                )

                val resolvedIds = defaults.pluginIds.mapNotNull { pluginId ->
                    availableIds.find { it.equals(pluginId, ignoreCase = true) }.also {
                        if (it == null) {
                            logger.info(
                                "Ignoring default plugin '{}' of category '{}' from source '{}', because it is " +
                                        "not available for the organization with ID '{}'.",
                                pluginId,
                                category,
                                defaults.source,
                                organizationId
                            )
                        }
                    }
                }

                defaults.copy(pluginIds = resolvedIds.distinct())
            }
        }
    }

    /**
     * Reset the default plugins of the given [category] for the organization with the given [organizationId], or
     * the global defaults if [organizationId] is `null`, so that the defaults from the next higher level are used.
     */
    internal suspend fun resetDefaultPlugins(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory,
        userId: String
    ): Result<Unit, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId).map {
            val state = eventStore.getDefaultPlugins(organizationId, category)

            if (state.pluginIds != null) {
                eventStore.appendEvent(
                    DefaultPluginsEvent(
                        organizationId = organizationId,
                        category = category,
                        version = state.version + 1,
                        payload = DefaultPluginsReset,
                        createdBy = userId
                    )
                )
            }
        }
    }

    /**
     * Set the default plugins of the given [category] for the organization with the given [organizationId], or the
     * global defaults if [organizationId] is `null`, to the given ordered list of [pluginIds]. Plugin IDs are
     * normalized to the IDs of the installed plugins. For reporters, the names of report definitions from the admin
     * configuration of the default config context are accepted as well.
     */
    internal suspend fun setDefaultPlugins(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory,
        pluginIds: List<String>,
        userId: String
    ): Result<Unit, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId)
            .andThen { validatePluginIds(organizationId, category.pluginType, pluginIds) }
            .map { normalizedPluginIds ->
                val state = eventStore.getDefaultPlugins(organizationId, category)

                if (state.pluginIds != normalizedPluginIds) {
                    eventStore.appendEvent(
                        DefaultPluginsEvent(
                            organizationId = organizationId,
                            category = category,
                            version = state.version + 1,
                            payload = DefaultPluginsSet(normalizedPluginIds),
                            createdBy = userId
                        )
                    )
                }
            }
    }

    /**
     * Return the default plugins of [category] for [organizationId] together with their source, without filtering
     * them by availability.
     */
    private suspend fun getEffectiveDefaults(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory
    ): DefaultPlugins {
        organizationId?.let { eventStore.getDefaultPlugins(it, category).pluginIds }?.let {
            return DefaultPlugins(category, it, DefaultPluginsSource.ORGANIZATION)
        }

        eventStore.getDefaultPlugins(null, category).pluginIds?.let {
            return DefaultPlugins(category, it, DefaultPluginsSource.GLOBAL)
        }

        val builtInDefaults = getBuiltInDefaults(category)?.map { normalizePluginId(category.pluginType, it) ?: it }
            ?: getEffectiveDefaults(organizationId, DefaultPluginsCategory.SCANNER).pluginIds

        return DefaultPlugins(category, builtInDefaults, DefaultPluginsSource.BUILT_IN)
    }

    /**
     * Return the IDs of all plugins of [pluginType] that can be used by the organization with the given
     * [organizationId], or by any organization if [organizationId] is `null`. For reporters, the given
     * [reportDefinitionNames] are included, and the plugins with the [replacedReporterIds] are excluded.
     */
    private suspend fun getAvailablePluginIds(
        pluginType: PluginType,
        organizationId: OrganizationId?,
        reportDefinitionNames: Set<String>,
        replacedReporterIds: Set<String>
    ): List<String> {
        val isReporter = pluginType == PluginType.REPORTER

        val pluginIds = pluginService.getPlugins()
            .filter { it.type == pluginType && !(isReporter && it.id.lowercase() in replacedReporterIds) }
            .filter { isAvailable(it, organizationId) }
            .map { it.id }

        return if (isReporter) pluginIds + reportDefinitionNames else pluginIds
    }

    /** Return information about the report definitions from the admin configuration of the given config [context]. */
    private suspend fun getReportDefinitions(context: ResolvedConfigContext): ReportDefinitionsInfo {
        val definitions = adminConfigService.loadAdminConfig(context).reporterConfig.resolvedReportDefinitions

        return ReportDefinitionsInfo(
            names = definitions.keys,
            replacedPluginIds = definitions.values.mapTo(mutableSetOf()) { it.pluginId.lowercase() }
        )
    }

    /**
     * Return whether the plugin with the given [descriptor] is available for the organization with the given
     * [organizationId]. If [organizationId] is `null`, restricted plugins are considered available, because they can
     * be made available to organizations by plugin templates.
     */
    private suspend fun isAvailable(descriptor: PluginDescriptor, organizationId: OrganizationId?): Boolean =
        when (descriptor.availability) {
            PluginAvailability.ENABLED -> true

            PluginAvailability.DISABLED -> false

            PluginAvailability.RESTRICTED -> {
                // Restricted plugins can be made available to organizations by plugin templates.
                organizationId == null ||
                        pluginTemplateService.getTemplateForOrganization(
                            descriptor.type,
                            descriptor.id,
                            organizationId
                        ).getOrElse { null } != null
            }
        }

    /** Validate that the organization with the given [organizationId] exists, if it is not `null`. */
    private suspend fun validateOrganizationExists(organizationId: OrganizationId?): Result<Unit, DefaultPluginsError> =
        if (organizationId != null && organizationRepository.get(organizationId.value) == null) {
            DefaultPluginsError.NotFound("No organization with ID '$organizationId' found.").toErr()
        } else {
            Ok(Unit)
        }

    /**
     * Validate that all [pluginIds] refer to plugins of [pluginType] that can be used by the organization with the
     * given [organizationId], or by any organization if [organizationId] is `null`, and that there are no duplicates.
     * Return the normalized plugin IDs on success.
     */
    private suspend fun validatePluginIds(
        organizationId: OrganizationId?,
        pluginType: PluginType,
        pluginIds: List<String>
    ): Result<List<String>, DefaultPluginsError> {
        val duplicates = pluginIds.groupBy { it.lowercase() }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            return DefaultPluginsError.InvalidPlugin(
                "The plugin IDs ${duplicates.joinToString { "'$it'" }} are configured multiple times."
            ).toErr()
        }

        // Report definitions are validated against the default config context. Plugins replaced by report
        // definitions are still accepted, because they might not be replaced in other config contexts.
        val reportDefinitionNames = if (pluginType == PluginType.REPORTER) {
            getReportDefinitions(configManager.resolveContext(RequestedConfigContext.EMPTY)).names
        } else {
            emptySet()
        }

        val availableIds = getAvailablePluginIds(
            pluginType,
            organizationId,
            reportDefinitionNames,
            replacedReporterIds = emptySet()
        )

        val normalizedIds = pluginIds.map { pluginId ->
            availableIds.find { it.equals(pluginId, ignoreCase = true) } ?: return DefaultPluginsError.InvalidPlugin(
                if (pluginService.isInstalled(pluginType, pluginId)) {
                    "The plugin with type '$pluginType' and ID '$pluginId' is not available."
                } else {
                    "No plugin with type '$pluginType' and ID '$pluginId' found."
                }
            ).toErr()
        }

        return Ok(normalizedIds)
    }
}

/** Information about the report definitions from an admin configuration. */
private class ReportDefinitionsInfo(
    /** The names of the report definitions. */
    val names: Set<String>,

    /** The lowercase IDs of the reporter plugins that are replaced by report definitions. */
    val replacedPluginIds: Set<String>
)

/** An error that can occur when managing default plugins. */
sealed interface DefaultPluginsError {
    val message: String

    data class InvalidPlugin(override val message: String) : DefaultPluginsError

    data class NotFound(override val message: String) : DefaultPluginsError
}
