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
import com.github.michaelbull.result.map

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository

import org.jetbrains.exposed.v1.jdbc.Database

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration

import org.slf4j.LoggerFactory

/**
 * A service for managing the plugins that are enabled by default if a run does not explicitly configure which plugins
 * to use.
 *
 * Default plugins can be configured globally and per organization. For each [DefaultPluginsCategory], the defaults
 * are resolved in the following order: The defaults configured for the organization, the global defaults, and finally
 * the [built-in defaults][getBuiltInDefaults]. If no project scanners are configured on a level, the package scanners
 * of the same level are used for project scanning before falling back to the next level.
 */
class DefaultPluginsService(
    private val db: Database,
    private val eventStore: DefaultPluginsEventStore,
    private val pluginService: PluginService,
    private val pluginTemplateService: PluginTemplateService,
    private val organizationRepository: OrganizationRepository,
    private val repositoryRepository: RepositoryRepository,
    private val adminConfigService: AdminConfigService,
    private val configManager: ConfigManager
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DefaultPluginsService::class.java)

        /**
         * Return the built-in default plugin IDs for the given [category] that are used if no defaults are configured.
         * These match the defaults that were used before default plugins could be configured. Return `null` for
         * [project scanners][DefaultPluginsCategory.PROJECT_SCANNER], because they fall back to the package scanners.
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

        /** Return the category whose plugins are used if no plugins are configured for the given [category]. */
        private fun getFallbackCategory(category: DefaultPluginsCategory): DefaultPluginsCategory? =
            DefaultPluginsCategory.SCANNER.takeIf { category == DefaultPluginsCategory.PROJECT_SCANNER }
    }

    /**
     * Return the default plugins of all categories that apply to the given [organizationId], or the global defaults
     * if [organizationId] is `null`. The returned plugins are not filtered by their availability, so they can contain
     * plugins that are currently not available. Use [resolveDefaultPlugins] to get the plugins that should actually be
     * used for a run.
     */
    suspend fun getAllDefaultPlugins(
        organizationId: OrganizationId?
    ): Result<List<DefaultPlugins>, DefaultPluginsError> = db.transaction {
        validateOrganizationExists(organizationId).map {
            val configuredDefaults = eventStore.getConfiguredDefaults(organizationId)

            DefaultPluginsCategory.entries.map { category ->
                configuredDefaults.getEffectiveDefaults(organizationId, category)
            }
        }
    }

    /**
     * Return the plugins that can be configured as global default plugins. These are all plugins that are not disabled,
     * including restricted plugins, because these can be made available to organizations by plugin templates. The
     * report definitions from the admin configuration of the default config context are added as reporters and replace
     * the reporters they use, if these reporters are not disabled.
     */
    suspend fun getAvailableGlobalDefaultPlugins(): List<PluginDescriptor> {
        val reportDefinitions = getReportDefinitions(configManager.resolveContext(RequestedConfigContext.EMPTY))

        return pluginTemplateService.getAvailablePlugins(organizationId = null)
            .withReportDefinitions(reportDefinitions)
            .map { it.plugin }
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
            val configuredDefaults = eventStore.getConfiguredDefaults(organizationId)
            val availablePlugins = pluginTemplateService.getAvailablePlugins(organizationId)
                .withReportDefinitions(getReportDefinitions(context))

            DefaultPluginsCategory.entries.map { category ->
                val defaults = configuredDefaults.getEffectiveDefaults(organizationId, category)
                val availableIds = availablePlugins.idsOfType(category.pluginType)

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
     * Resolve the default plugins of all plugin types for the repository with the given [repositoryId] using the admin
     * configuration from the given config [context]. See [resolveDefaultPlugins] for details.
     */
    suspend fun resolveDefaultPluginsForRepository(
        repositoryId: RepositoryId,
        context: ResolvedConfigContext
    ): Result<List<DefaultPlugins>, DefaultPluginsError> = db.transaction {
        val organizationId = repositoryRepository.get(repositoryId.value)?.organizationId?.let(::OrganizationId)
            ?: return@transaction DefaultPluginsError.NotFound("No repository with ID '$repositoryId' found.").toErr()

        resolveDefaultPlugins(organizationId, context)
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
            val state = eventStore.loadState(organizationId, category)

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
                val state = eventStore.loadState(organizationId, category)

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
     * Return the default plugins of [category] for [organizationId] from these configured defaults together with their
     * source, without filtering them by availability. On each level, the plugins of the
     * [fallback category][getFallbackCategory] are used if no plugins are configured for [category] on this level.
     */
    private fun ConfiguredDefaultPlugins.getEffectiveDefaults(
        organizationId: OrganizationId?,
        category: DefaultPluginsCategory
    ): DefaultPlugins {
        val fallbackCategory = getFallbackCategory(category)

        val levels = buildList {
            if (organizationId != null) add(organization to DefaultPluginsSource.ORGANIZATION)
            add(global to DefaultPluginsSource.GLOBAL)
        }

        levels.forEach { (pluginIdsByCategory, source) ->
            pluginIdsByCategory[category]?.let { return DefaultPlugins(category, it, source) }

            fallbackCategory?.let { pluginIdsByCategory[it] }?.let {
                return DefaultPlugins(category, it, source, fallbackCategory)
            }
        }

        val builtInFallbackCategory = fallbackCategory.takeIf { getBuiltInDefaults(category) == null }
        val builtInDefaults = getBuiltInDefaults(builtInFallbackCategory ?: category).orEmpty()
            .map { normalizePluginId(category.pluginType, it) ?: it }

        return DefaultPlugins(category, builtInDefaults, DefaultPluginsSource.BUILT_IN, builtInFallbackCategory)
    }

    /** Return the report definitions from the admin configuration of the given config [context]. */
    private suspend fun getReportDefinitions(context: ResolvedConfigContext) =
        adminConfigService.loadAdminConfig(context).reporterConfig.resolvedReportDefinitions

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
     * For reporters, the names of the report definitions from the admin configuration of the default config context
     * are accepted as well. Return the normalized plugin IDs on success.
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

        // Reporters replaced by report definitions are still accepted, because they might not be replaced in the
        // config contexts of runs.
        val availableIds = pluginTemplateService.getAvailablePlugins(organizationId)
            .withReportDefinitions(
                getReportDefinitions(configManager.resolveContext(RequestedConfigContext.EMPTY)),
                replaceReporters = false
            )
            .idsOfType(pluginType)

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

/** Return the IDs of the plugins of the given [pluginType] from these available plugins. */
private fun List<AvailablePlugin>.idsOfType(pluginType: PluginType): List<String> =
    filter { it.plugin.type == pluginType }.map { it.plugin.id }

/** An error that can occur when managing default plugins. */
sealed interface DefaultPluginsError {
    val message: String

    data class InvalidPlugin(override val message: String) : DefaultPluginsError

    data class NotFound(override val message: String) : DefaultPluginsError
}
