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

import io.ktor.server.config.tryGetString

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig
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
        apiSecret = getSecret(Path("keycloak.apiSecret")),
        subjectClientId = getString("keycloak.subjectClientId")
    )
}

private val emptyPluginConfig = PluginConfig(options = emptyMap(), secrets = emptyMap())

/**
 * Get all [PluginConfig]s from a [CreateOrtRun] object. The result is a map from [PluginType]s to maps of plugin IDs to
 * [PluginConfig]s. Some job configs allow to enable plugins without additional configuration, in this case an empty
 * [PluginConfig] is used as the value for the plugin ID in the map.
 */
fun CreateOrtRun.getPluginConfigs(): Map<PluginType, Map<String, PluginConfig>> =
    buildMap {
        jobConfigs.advisor?.let { jobConfig ->
            val allAdvisors = jobConfig.advisors + jobConfig.config?.keys.orEmpty()
            put(PluginType.ADVISOR, allAdvisors.associateWith { jobConfig.config?.get(it) ?: emptyPluginConfig })
        }

        jobConfigs.analyzer.let { jobConfig ->
            val allPackageManagers = jobConfig.packageManagerOptions?.keys.orEmpty() +
                    jobConfig.enabledPackageManagers.orEmpty() -
                    jobConfig.disabledPackageManagers.orEmpty()

            put(
                PluginType.PACKAGE_MANAGER,
                allPackageManagers.associateWith { packageManager ->
                    jobConfig.packageManagerOptions?.get(packageManager)?.options?.let {
                        PluginConfig(it, emptyMap())
                    } ?: emptyPluginConfig
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
            put(PluginType.SCANNER, allScanners.associateWith { jobConfig.config?.get(it) ?: emptyPluginConfig })
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
            put(PluginType.REPORTER, allReporters.associateWith { jobConfig.config?.get(it) ?: emptyPluginConfig })

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
fun CreateOrtRun.hasKeepAliveWorkerFlag() =
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
                enumValues<E>().joinToString { "'$it'" }
    )
