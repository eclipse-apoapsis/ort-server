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

import org.apache.logging.log4j.kotlin.logger

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableProviderPluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration

/**
 * A class responsible for applying plugin templates to the job configurations of an ORT run.
 */
class PluginTemplateApplicator(
    private val pluginService: PluginService,
    private val pluginTemplateService: PluginTemplateService
) {
    /**
     * Resolve the default package managers and apply the plugin templates applicable to [organizationId] to
     * [jobConfigs], and return the updated [JobConfigurations]. Only plugins that are configured to run are considered,
     * plugins that are not installed are ignored. Throws an [IllegalStateException] if a template lookup fails.
     */
    fun applyTemplates(jobConfigs: JobConfigurations, organizationId: OrganizationId): JobConfigurations {
        val configs = jobConfigs.withDefaultPackageManagers(organizationId)

        return applyTemplatesToConfigs(configs, fetchTemplates(configs, organizationId))
    }

    /**
     * Return a copy of these [JobConfigurations] with the package managers that are enabled by default for
     * [organizationId] filled in if none are enabled explicitly.
     */
    private fun JobConfigurations.withDefaultPackageManagers(organizationId: OrganizationId): JobConfigurations {
        if (!analyzer.enabledPackageManagers.isNullOrEmpty()) return this

        val defaults = getDefaultPackageManagers(pluginService, pluginTemplateService, organizationId)

        logger.info { "Enabling default package managers as none were configured for the run: $defaults." }

        return copy(analyzer = analyzer.copy(enabledPackageManagers = defaults))
    }

    /**
     * Fetch the templates configured for [organizationId] for all plugins that are active in [jobConfigs]. Plugin IDs
     * are normalized to the IDs of the installed plugins, therefore the returned IDs can differ in case from those in
     * [jobConfigs].
     */
    private fun fetchTemplates(
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
                logger.debug { "Fetching template for plugin type '$pluginType' and ID '$pluginId'." }

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
        jobConfigs.analyzer.packageCurationProviders.map { it.type }

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
    existing: List<ResolvableProviderPluginConfig>,
    templates: Map<String, PluginTemplate>
): List<ResolvableProviderPluginConfig> {
    if (templates.isEmpty()) return existing

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
