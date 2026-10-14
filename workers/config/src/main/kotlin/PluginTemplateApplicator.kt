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

package org.eclipse.apoapsis.ortserver.workers.config

import com.github.michaelbull.result.getOrElse

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableProviderPluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration

import org.slf4j.LoggerFactory

/**
 * A class responsible for resolving the default plugins and applying plugin templates to the job configurations of an
 * ORT run.
 */
class PluginTemplateApplicator(
    private val pluginService: PluginService,
    private val pluginTemplateService: PluginTemplateService,
    private val defaultPluginsService: DefaultPluginsService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PluginTemplateApplicator::class.java)
    }

    /**
     * Resolve the default plugins and apply the plugin templates applicable to [organizationId] to [jobConfigs], and
     * return the updated [JobConfigurations]. The [configContext] is used to resolve the report definitions that can be
     * part of the default reporters. Only plugins that are configured to run are considered, plugins that are not
     * installed are ignored. Throws an [IllegalStateException] if the default plugins cannot be resolved or a template
     * lookup fails.
     */
    suspend fun applyTemplates(
        jobConfigs: JobConfigurations,
        organizationId: OrganizationId,
        configContext: ResolvedConfigContext
    ): JobConfigurations {
        val configs = jobConfigs.withDefaultPlugins(organizationId, configContext)

        return applyTemplatesToConfigs(configs, fetchTemplates(configs, organizationId))
    }

    /**
     * Return a copy of these [JobConfigurations] with the default plugins resolved for [organizationId] and
     * [configContext] filled in for the plugin lists that are not configured. For package managers, the defaults are
     * also used if the list of enabled package managers is empty. The default project scanners are only used if
     * neither scanners nor project scanners are configured, and only if they do not fall back to the default scanners.
     */
    private suspend fun JobConfigurations.withDefaultPlugins(
        organizationId: OrganizationId,
        configContext: ResolvedConfigContext
    ): JobConfigurations {
        val missingCategories = getCategoriesWithoutConfiguration()
        if (missingCategories.isEmpty()) return this

        val defaults = defaultPluginsService.resolveDefaultPlugins(organizationId, configContext)
            .getOrElse { error ->
                throw IllegalStateException("Failed to resolve the default plugins: ${error.message}")
            }
            .associateBy { it.category }

        missingCategories.forEach { category ->
            logger.info(
                "Using default plugins of category '{}' as none were configured for the run: {}.",
                category,
                defaults[category]?.pluginIds.orEmpty()
            )
        }

        fun defaultIds(category: DefaultPluginsCategory) = defaults[category]?.pluginIds.orEmpty()

        fun defaultProviders(category: DefaultPluginsCategory) =
            defaultIds(category).map { ResolvableProviderPluginConfig(type = it) }

        // Leave the project scanners unset if they fall back to the package scanners, as then the scanners are used
        // for projects as well.
        fun defaultProjectScanners(): List<String>? =
            defaults[DefaultPluginsCategory.PROJECT_SCANNER]?.takeIf { it.fallbackCategory == null }?.pluginIds

        return copy(
            analyzer = analyzer.copy(
                enabledPackageManagers = analyzer.enabledPackageManagers?.takeUnless { it.isEmpty() }
                    ?: defaultIds(DefaultPluginsCategory.PACKAGE_MANAGER),
                packageCurationProviders = analyzer.packageCurationProviders
                    ?: defaultProviders(DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER)
            ),
            advisor = advisor?.let { it.copy(advisors = it.advisors ?: defaultIds(DefaultPluginsCategory.ADVISOR)) },
            scanner = scanner?.let {
                it.copy(
                    scanners = it.scanners ?: defaultIds(DefaultPluginsCategory.SCANNER),
                    projectScanners = if (it.scanners == null && it.projectScanners == null) {
                        defaultProjectScanners()
                    } else {
                        it.projectScanners
                    }
                )
            },
            evaluator = evaluator?.let {
                it.copy(
                    packageConfigurationProviders = it.packageConfigurationProviders
                        ?: defaultProviders(DefaultPluginsCategory.PACKAGE_CONFIGURATION_PROVIDER)
                )
            },
            reporter = reporter?.let {
                it.copy(
                    formats = it.formats ?: defaultIds(DefaultPluginsCategory.REPORTER),
                    packageConfigurationProviders = it.packageConfigurationProviders
                        ?: defaultProviders(DefaultPluginsCategory.PACKAGE_CONFIGURATION_PROVIDER)
                )
            }
        )
    }

    /**
     * Return the [DefaultPluginsCategory]s for which no plugins are configured in these [JobConfigurations]. Project
     * scanners are only considered missing if neither scanners nor project scanners are configured.
     */
    private fun JobConfigurations.getCategoriesWithoutConfiguration(): Set<DefaultPluginsCategory> =
        buildSet {
            if (analyzer.enabledPackageManagers.isNullOrEmpty()) add(DefaultPluginsCategory.PACKAGE_MANAGER)
            if (analyzer.packageCurationProviders == null) add(DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER)
            if (advisor?.let { it.advisors == null } == true) add(DefaultPluginsCategory.ADVISOR)
            if (scanner?.let { it.scanners == null } == true) add(DefaultPluginsCategory.SCANNER)
            if (scanner?.let { it.scanners == null && it.projectScanners == null } == true) {
                add(DefaultPluginsCategory.PROJECT_SCANNER)
            }

            if (reporter?.let { it.formats == null } == true) add(DefaultPluginsCategory.REPORTER)

            val evaluatorProviders = evaluator?.let { it.packageConfigurationProviders == null } == true
            val reporterProviders = reporter?.let { it.packageConfigurationProviders == null } == true
            if (evaluatorProviders || reporterProviders) add(DefaultPluginsCategory.PACKAGE_CONFIGURATION_PROVIDER)
        }

    /**
     * Fetch the templates configured for [organizationId] for all plugins that are active in [jobConfigs]. Plugin IDs
     * are normalized to the IDs of the installed plugins, therefore the returned IDs can differ in case from those in
     * [jobConfigs].
     */
    private suspend fun fetchTemplates(
        jobConfigs: JobConfigurations,
        organizationId: OrganizationId
    ): Map<PluginType, Map<String, PluginTemplate?>> {
        val activePluginIds = mapOf(
            PluginType.ADVISOR to getActiveAdvisors(jobConfigs),
            PluginType.SCANNER to getActiveScanners(jobConfigs),
            PluginType.REPORTER to getActiveReporters(jobConfigs),
            PluginType.PACKAGE_MANAGER to getActivePackageManagers(jobConfigs),
            PluginType.PACKAGE_CURATION_PROVIDER to getActivePackageCurationProviders(jobConfigs),
            PluginType.PACKAGE_CONFIGURATION_PROVIDER to getActivePackageConfigProviders(jobConfigs)
        )

        val installedPluginIds = pluginService.getPlugins().groupBy({ it.type }) { it.id }

        return activePluginIds.mapValues { (pluginType, pluginIds) ->
            normalizePluginIds(installedPluginIds[pluginType].orEmpty(), pluginIds).associateWith { pluginId ->
                logger.debug("Fetching template for plugin type '{}' and ID '{}'.", pluginType, pluginId)

                pluginTemplateService.getTemplateForOrganization(pluginType, pluginId, organizationId)
                    .getOrElse { error ->
                        throw IllegalStateException(
                            "Failed to fetch template for plugin type '$pluginType' and ID '$pluginId': " +
                                    error.message
                        )
                    }
            }
        }
    }

    private fun getActiveAdvisors(jobConfigs: JobConfigurations) = jobConfigs.advisor?.advisors.orEmpty()

    private fun getActiveScanners(jobConfigs: JobConfigurations) =
        jobConfigs.scanner?.scanners.orEmpty() + jobConfigs.scanner?.projectScanners.orEmpty()

    private fun getActiveReporters(jobConfigs: JobConfigurations) = jobConfigs.reporter?.formats.orEmpty()

    /** Return the IDs of the package managers that are enabled in [jobConfigs], excluding the disabled ones. */
    private fun getActivePackageManagers(jobConfigs: JobConfigurations): Collection<String> {
        val analyzer = jobConfigs.analyzer
        val disabled = analyzer.disabledPackageManagers.orEmpty().mapTo(mutableSetOf()) { it.lowercase() }

        return analyzer.enabledPackageManagers.orEmpty().filterNot { it.lowercase() in disabled }
    }

    private fun getActivePackageCurationProviders(jobConfigs: JobConfigurations) =
        jobConfigs.analyzer.packageCurationProviders.orEmpty().map { it.type }

    private fun getActivePackageConfigProviders(jobConfigs: JobConfigurations): List<String> {
        val providers = jobConfigs.evaluator?.packageConfigurationProviders.orEmpty() +
                jobConfigs.reporter?.packageConfigurationProviders.orEmpty()

        return providers.map { it.type }
    }
}

/**
 * Map the given [pluginIds] to the matching [installedPluginIds], ignoring case. IDs without a matching installed
 * plugin are dropped because there cannot be a template for them.
 */
private fun normalizePluginIds(installedPluginIds: List<String>, pluginIds: Collection<String>): Set<String> =
    pluginIds.mapNotNullTo(mutableSetOf()) { pluginId ->
        installedPluginIds.find { it.equals(pluginId, ignoreCase = true) }
    }

/**
 * Apply the pre-fetched plugin [templates] to [jobConfigs] and return the updated [JobConfigurations].
 *
 * Plugin IDs are matched against the plugin configs in [jobConfigs] ignoring case. A non-null template is merged using
 * the following rules:
 * - Options with `isFinal = true` always override the user-supplied value, other options are only applied if the user
 *   did not supply a value.
 * - Options of type [PluginOptionType.SECRET] populate the `secrets` map with [SecretSource.ADMIN].
 * - Options with a `null` value are ignored, so a template that does not contribute any value never creates a plugin
 *   config.
 */
internal fun applyTemplatesToConfigs(
    jobConfigs: JobConfigurations,
    templates: Map<PluginType, Map<String, PluginTemplate?>>
): JobConfigurations {
    fun templatesFor(pluginType: PluginType) = templates[pluginType].orEmpty().filterNotNullValues()

    val packageConfigTemplates = templatesFor(PluginType.PACKAGE_CONFIGURATION_PROVIDER)

    return jobConfigs.copy(
        analyzer = jobConfigs.analyzer.copy(
            packageManagerOptions = mergePackageManagerConfigs(
                jobConfigs.analyzer.packageManagerOptions,
                templatesFor(PluginType.PACKAGE_MANAGER)
            ),
            packageCurationProviders = mergeProviderConfigs(
                jobConfigs.analyzer.packageCurationProviders,
                templatesFor(PluginType.PACKAGE_CURATION_PROVIDER)
            )
        ),
        advisor = jobConfigs.advisor?.let { advisor ->
            advisor.copy(config = mergePluginConfigs(advisor.config, templatesFor(PluginType.ADVISOR)))
        },
        scanner = jobConfigs.scanner?.let { scanner ->
            scanner.copy(config = mergePluginConfigs(scanner.config, templatesFor(PluginType.SCANNER)))
        },
        evaluator = jobConfigs.evaluator?.let { evaluator ->
            evaluator.copy(
                packageConfigurationProviders = mergeProviderConfigs(
                    evaluator.packageConfigurationProviders,
                    packageConfigTemplates
                )
            )
        },
        reporter = jobConfigs.reporter?.let { reporter ->
            reporter.copy(
                config = mergePluginConfigs(reporter.config, templatesFor(PluginType.REPORTER)),
                packageConfigurationProviders = mergeProviderConfigs(
                    reporter.packageConfigurationProviders,
                    packageConfigTemplates
                )
            )
        }
    )
}

/** Return a map containing only the entries of this map with a non-null value. */
private fun <K, V : Any> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    mapNotNull { (key, value) -> value?.let { key to it } }.toMap()

/** Return the key of this map that equals [key] ignoring case, or [key] if there is no such key. */
private fun Map<String, *>.findKeyIgnoringCase(key: String): String =
    keys.find { it.equals(key, ignoreCase = true) } ?: key

/**
 * Merge the [templates] into the [existing] map of [ResolvablePluginConfig]s. Plugins without an entry in [existing]
 * receive a new one if their template contributes at least one value.
 */
private fun mergePluginConfigs(
    existing: Map<String, ResolvablePluginConfig>?,
    templates: Map<String, PluginTemplate>
): Map<String, ResolvablePluginConfig>? {
    val result = existing.orEmpty().toMutableMap()

    templates.forEach { (pluginId, template) ->
        val key = result.findKeyIgnoringCase(pluginId)
        val config = result[key]
        val options = config?.options.orEmpty().toMutableMap()
        val secrets = config?.secrets.orEmpty().toMutableMap()

        mergeTemplateOptions(template, options, secrets)

        if (config != null || options.isNotEmpty() || secrets.isNotEmpty()) {
            result[key] = ResolvablePluginConfig(options, secrets)
        }
    }

    return result.ifEmpty { existing }
}

/**
 * Merge the [templates] into the [existing] map of [PackageManagerConfiguration]s. Package managers without an entry
 * in [existing] receive a new one if their template contributes at least one option.
 */
private fun mergePackageManagerConfigs(
    existing: Map<String, PackageManagerConfiguration>?,
    templates: Map<String, PluginTemplate>
): Map<String, PackageManagerConfiguration>? {
    val result = existing.orEmpty().toMutableMap()

    templates.forEach { (pluginId, template) ->
        val key = result.findKeyIgnoringCase(pluginId)
        val config = result[key]
        val options = config?.options.orEmpty().toMutableMap()

        mergeTemplateOptions(template, options, secrets = null)

        if (config != null) {
            result[key] = config.copy(options = options.ifEmpty { null })
        } else if (options.isNotEmpty()) {
            result[key] = PackageManagerConfiguration(options = options)
        }
    }

    return result.ifEmpty { existing }
}

/**
 * Merge the [templates] into the matching entries of the [existing] provider configs. Providers are matched by their
 * type ignoring case. Templates for providers that are not configured are ignored.
 */
private fun mergeProviderConfigs(
    existing: List<ResolvableProviderPluginConfig>?,
    templates: Map<String, PluginTemplate>
): List<ResolvableProviderPluginConfig>? {
    if (existing == null || templates.isEmpty()) return existing

    return existing.map { provider ->
        val template = templates.entries.find { it.key.equals(provider.type, ignoreCase = true) }?.value
            ?: return@map provider

        val options = provider.options.toMutableMap()
        val secrets = provider.secrets.toMutableMap()

        mergeTemplateOptions(template, options, secrets)

        provider.copy(options = options, secrets = secrets)
    }
}

/**
 * Merge the options of [template] into [options] and [secrets]. Options with `isFinal` set override existing values,
 * other options are only applied if they are not set yet. Options of type [PluginOptionType.SECRET] are ignored if
 * [secrets] is `null`, which is the case for configs that cannot hold secrets.
 */
private fun mergeTemplateOptions(
    template: PluginTemplate,
    options: MutableMap<String, String>,
    secrets: MutableMap<String, ResolvableSecret>?
) {
    template.options.forEach { option ->
        val value = option.value ?: return@forEach

        if (option.type == PluginOptionType.SECRET) {
            if (secrets != null && (option.isFinal || option.option !in secrets)) {
                secrets[option.option] = ResolvableSecret(value, SecretSource.ADMIN)
            }
        } else if (option.isFinal || option.option !in options) {
            options[option.option] = value
        }
    }
}
