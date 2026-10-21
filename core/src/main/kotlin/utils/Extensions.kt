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

package org.eclipse.apoapsis.ortserver.core.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.tryGetString

import kotlin.enums.enumEntries

import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepositoryRun
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.dao.QueryParametersException

fun ConfigManager.createKeycloakClientConfiguration(): KeycloakClientConfiguration {
    val baseUrl = getString("keycloak.baseUrl")
    val realm = getString("keycloak.realm")

    val defaultApiUrl = "$baseUrl/admin/realms/$realm"
    val defaultAccessTokenUrl = "$baseUrl/realms/$realm/protocol/openid-connect/token"

    return KeycloakClientConfiguration(
        baseUrl = baseUrl,
        realm = realm,
        apiUrl = tryGetString("keycloak.apiUrl") ?: defaultApiUrl,
        clientId = getString("keycloak.clientId"),
        accessTokenUrl = tryGetString("keycloak.accessTokenUrl") ?: defaultAccessTokenUrl,
        apiUser = getString("keycloak.apiUser"),
        apiSecret = getSecret(Path("keycloak.apiSecret"))
    )
}

private val emptyPluginConfig = PluginConfig(options = emptyMap(), secrets = emptyMap())

/**
 * Get all [PluginConfig]s from a [PostRepositoryRun] object. The result is a map from [PluginType]s to maps of plugin
 * IDs to [PluginConfig]s. Some job configs allow to enable plugins without additional configuration, in this case an
 * empty [PluginConfig] is used as the value for the plugin ID in the map.
 */
fun PostRepositoryRun.getPluginConfigs(): Map<PluginType, Map<String, PluginConfig>> =
    buildMap {
        jobConfigs.advisor?.let { jobConfig ->
            val allAdvisors = jobConfig.advisors + jobConfig.config?.keys.orEmpty()
            put(PluginType.ADVISOR, allAdvisors.associateWithConfigIgnoringCase(jobConfig.config) { it })
        }

        jobConfigs.analyzer.let { jobConfig ->
            val disabledPackageManagers = jobConfig.disabledPackageManagers.orEmpty()
            val configuredPackageManagers =
                jobConfig.packageManagerOptions?.keys.orEmpty() + jobConfig.enabledPackageManagers.orEmpty()
            val allPackageManagers = configuredPackageManagers.filterNot { packageManager ->
                disabledPackageManagers.any { it.equals(packageManager, ignoreCase = true) }
            }

            put(
                PluginType.PACKAGE_MANAGER,
                allPackageManagers.associateWithConfigIgnoringCase(jobConfig.packageManagerOptions) { config ->
                    config.options?.let { PluginConfig(it, emptyMap()) } ?: emptyPluginConfig
                }
            )

            jobConfig.packageCurationProviders?.let { packageCurationProviders ->
                put(
                    PluginType.PACKAGE_CURATION_PROVIDER,
                    packageCurationProviders.associate { providerConfig ->
                        providerConfig.type to PluginConfig(
                            options = providerConfig.options,
                            secrets = providerConfig.secrets
                        )
                    }
                )
            }
        }

        jobConfigs.scanner?.let { jobConfig ->
            val allScanners = jobConfig.scanners.orEmpty() +
                    jobConfig.projectScanners.orEmpty() +
                    jobConfig.config?.keys.orEmpty()
            put(PluginType.SCANNER, allScanners.associateWithConfigIgnoringCase(jobConfig.config) { it })
        }

        jobConfigs.evaluator?.let { jobConfig ->
            jobConfig.packageConfigurationProviders?.let { packageConfigurationProviders ->
                put(
                    PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                    packageConfigurationProviders.associate { providerConfig ->
                        providerConfig.type to PluginConfig(
                            options = providerConfig.options,
                            secrets = providerConfig.secrets
                        )
                    }
                )
            }
        }

        jobConfigs.reporter?.let { jobConfig ->
            val allReporters = jobConfig.formats + jobConfig.config?.keys.orEmpty()
            put(PluginType.REPORTER, allReporters.associateWithConfigIgnoringCase(jobConfig.config) { it })

            // Ignore package configuration providers if the evaluator job is configured, because then the reporter
            // worker ignores that part of the configuration.
            if (jobConfigs.evaluator == null) {
                jobConfig.packageConfigurationProviders?.let { packageConfigurationProviders ->
                    put(
                        PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                        packageConfigurationProviders.associate { providerConfig ->
                            providerConfig.type to PluginConfig(
                                options = providerConfig.options,
                                secrets = providerConfig.secrets
                            )
                        }
                    )
                }
            }
        }
    }.filterValues { it.isNotEmpty() }

/** Return true if the `keepAliveWorker` flag is enabled in any job config. */
fun PostRepositoryRun.hasKeepAliveWorkerFlag() =
    jobConfigs.advisor?.keepAliveWorker == true ||
    jobConfigs.analyzer.keepAliveWorker ||
    jobConfigs.evaluator?.keepAliveWorker == true ||
    jobConfigs.notifier?.keepAliveWorker == true ||
    jobConfigs.reporter?.keepAliveWorker == true ||
    jobConfigs.scanner?.keepAliveWorker == true

/**
 * Find the constant of an enum by its [name] ignoring case. Throw a meaningful exception if the name cannot be
 * resolved.
 */
inline fun <reified E : Enum<E>> findByName(name: String): E =
    runCatching { enumValueOf<E>(name.uppercase()) }.getOrNull() ?: throw QueryParametersException(
        "Invalid parameter value: '$name'. Allowed values are: " +
                enumEntries<E>().joinToString { "'$it'" }
    )

/** Extract the vulnerability rating filter from the [ApplicationCall]. */
internal fun ApplicationCall.vulnerabilityRatingFilter(): FilterOperatorAndValue<Set<VulnerabilityRating>>? =
    parameters["rating"]?.let { rating ->
        val ratings = rating.split(',')
        val operator = if (ratings.first() == "-") ComparisonOperator.NOT_IN else ComparisonOperator.IN
        val values = if (operator == ComparisonOperator.NOT_IN) ratings.drop(1) else ratings

        FilterOperatorAndValue(operator, values.mapTo(mutableSetOf()) { findByName<VulnerabilityRating>(it) })
    }

/** Extract the vulnerability identifier filter from the [ApplicationCall]. */
internal fun ApplicationCall.vulnerabilityIdentifierFilter(): FilterOperatorAndValue<String>? =
    parameters["identifier"]?.let { FilterOperatorAndValue(ComparisonOperator.ILIKE, it) }

/** Extract the vulnerability purl filter from the [ApplicationCall]. */
internal fun ApplicationCall.vulnerabilityPurlFilter(): FilterOperatorAndValue<String>? =
    parameters["purl"]?.let { FilterOperatorAndValue(ComparisonOperator.ILIKE, it) }

/** Extract the vulnerability external ID filter from the [ApplicationCall]. */
internal fun ApplicationCall.vulnerabilityExternalIdFilter(): FilterOperatorAndValue<String>? =
    parameters["externalId"]?.let { FilterOperatorAndValue(ComparisonOperator.ILIKE, it) }

/** Extract the vulnerability advisor filter from the [ApplicationCall]. */
internal fun ApplicationCall.vulnerabilityAdvisorFilter(): FilterOperatorAndValue<Set<String>>? =
    parameters["advisors"]?.let { advisors ->
        val names = advisors.split(',')
        val operator = if (names.first() == "-") ComparisonOperator.NOT_IN else ComparisonOperator.IN
        val values = if (operator == ComparisonOperator.NOT_IN) names.drop(1) else names

        FilterOperatorAndValue(operator, values.toSet())
    }

/** Extract [VulnerabilityForRunsFilters] from the [ApplicationCall] */
fun ApplicationCall.vulnerabilityForRunsFilters(): VulnerabilityForRunsFilters = VulnerabilityForRunsFilters(
    rating = vulnerabilityRatingFilter(),
    identifier = vulnerabilityIdentifierFilter(),
    purl = vulnerabilityPurlFilter(),
    externalId = vulnerabilityExternalIdFilter(),
    advisors = vulnerabilityAdvisorFilter()
)

/**
 * Associate the plugin IDs in this collection with the [PluginConfig]s created by [transform] from the matching
 * entries in [configs]. As plugin IDs are case-insensitive, IDs that differ only in case are treated as the same
 * plugin, keeping the spelling of the first occurrence, and the [configs] are looked up ignoring case. Plugins without
 * a config get an empty [PluginConfig].
 */
private fun <T> Collection<String>.associateWithConfigIgnoringCase(
    configs: Map<String, T>?,
    transform: (T) -> PluginConfig
): Map<String, PluginConfig> {
    val distinctPluginIds = sortedSetOf(String.CASE_INSENSITIVE_ORDER)

    return filter { distinctPluginIds.add(it) }.associateWith { pluginId ->
        configs?.entries?.find { it.key.equals(pluginId, ignoreCase = true) }?.value?.let(transform)
            ?: emptyPluginConfig
    }
}
