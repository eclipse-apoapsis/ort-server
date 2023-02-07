/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.advisor

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration

import org.slf4j.LoggerFactory

/**
 * An internal helper class that creates an initialized [Advisor] instance based on the application configuration.
 *
 * This class is responsible for creating the configurations for the single advisor providers. For this purpose, the
 * _application.conf_ file has a special section with subsections for all supported advisor implementations in which
 * the settings for this implementation are defined. When an advisor job arrives, the list of selected advisor
 * providers is matched against the list of configured providers, and the intersection is used to create the [Advisor]
 * instance.
 */
internal class AdvisorConfigurator(
    /** The configuration of the Advisor worker application. */
    config: Config
) {
    companion object {
        /** The configuration path to the section containing the options for specific advise providers. */
        private const val PROVIDERS_PATH = "providers"

        /** Configuration property referencing the server URL of an advisor. */
        private const val SERVER_URL_PROPERTY = "serverUrl"

        /** Configuration property referencing an API key to authorize against a server. */
        private const val API_KEY_PROPERTY = "apiKey"

        /** Configuration property for a URL to browse vulnerability information. */
        private const val BROWSE_URL_PROPERTY = "browseUrl"

        /** Configuration property for a username. */
        private const val USERNAME_PROPERTY = "user"

        /** Configuration property for a password. */
        private const val PASSWORD_PROPERTY = "password"

        /** The default URL of the OSV advisor. */
        private const val OSV_DEFAULT_URL = "https://api.osv.dev"

        private val logger = LoggerFactory.getLogger(AdvisorConfigurator::class.java)

        /**
         * Return the value of the given [key] from this [Config] or [defaultValue] if it is not defined.
         */
        private fun Config.getStringOrDefault(key: String, defaultValue: String? = null): String? =
            if (hasPath(key)) getString(key) else defaultValue

        /**
         * Return the sub configuration at the given [path] or an empty [Config] if this path is not defined.
         */
        private fun Config.getConfigOrEmpty(path: String): Config =
            if (hasPath(path)) getConfig(path) else ConfigFactory.empty()

        /**
         * Generate a string that can be used to log the advisor providers in this collection. Use [caption] as
         * caption.
         */
        private fun Collection<String>.toLogStatement(caption: String): String =
            """
                The following advisors are $caption:
                    ${joinToString()}
            """.trimIndent()
    }

    /** Stores the providers-specific configuration settings. */
    private val providerConfig = config.getConfigOrEmpty(PROVIDERS_PATH)

    /**
     * A map with the configurators for specific advisor providers keyed by the advisor names.
     */
    private val providerConfigurators = AdvisorProviderConfigurator.values().associateBy { it.advisorName }

    /**
     * Return an initialized [Advisor] instance that invokes the given [advisorProviders] if possible. Unknown
     * advisor providers or providers that are not configured are dropped.
     */
    fun createAdvisor(advisorProviders: Collection<String>): Advisor {
        val configuredProviders = findConfiguredProviders(advisorProviders)

        val advisorConfiguration = createAdvisorConfiguration(configuredProviders)

        val selectedProviders = configuredProviders.mapNotNull { Advisor.ALL[it.first.advisorName] }

        return Advisor(selectedProviders, advisorConfiguration)
    }

    /**
     * Create the [AdvisorConfiguration] to be used based on the collection of [configuredProviders].
     */
    internal fun createAdvisorConfiguration(
        configuredProviders: Collection<Pair<AdvisorProviderConfigurator, Config>>
    ): AdvisorConfiguration =
        configuredProviders
            .fold(AdvisorConfiguration()) { advisorConfig, (configurator, providerConfig) ->
                configurator.configure(providerConfig, advisorConfig)
            }

    /**
     * Return a list with information about the available configured advisor providers based on the given collection
     * of [selected advisor providers][advisorProviders].
     */
    internal fun findConfiguredProviders(
        advisorProviders: Collection<String>
    ): List<Pair<AdvisorProviderConfigurator, Config>> {
        val configuredProviders = advisorProviders.mapNotNull(providerConfigurators::get)
            .map { it to providerConfig.getConfigOrEmpty(it.advisorName) }
            .filter { (configurator, config) -> configurator.mandatoryKeys().all(config::hasPath) }

        val configuredProviderNames = configuredProviders.map { it.first.advisorName }
        if (configuredProviders.size < advisorProviders.size) {
            logger.warn(
                (advisorProviders - configuredProviderNames.toSet())
                    .toLogStatement("unknown or have no valid configuration")
            )
        }

        logger.info(configuredProviderNames.toLogStatement("activated"))

        return configuredProviders
    }

    /**
     * An internal enum class to represent the supported advisor providers. The single constants of this class know
     * which configuration options are required for a specific advisor implementation and can create a corresponding
     * configuration.
     */
    internal enum class AdvisorProviderConfigurator(
        /** The name of the advisor provider associated with this instance. */
        val advisorName: String
    ) {
        NEXUS_IQ("NexusIq") {
            override fun mandatoryKeys(): Set<String> = setOf(SERVER_URL_PROPERTY)

            override fun configure(
                providerConfig: Config,
                advisorConfiguration: AdvisorConfiguration
            ): AdvisorConfiguration {
                val iqServerUrl = providerConfig.getString(SERVER_URL_PROPERTY)
                val iqConfig = NexusIqConfiguration(
                    serverUrl = iqServerUrl,
                    browseUrl = providerConfig.getStringOrDefault(BROWSE_URL_PROPERTY, iqServerUrl)!!,
                    username = providerConfig.getStringOrDefault(USERNAME_PROPERTY),
                    password = providerConfig.getStringOrDefault(PASSWORD_PROPERTY)
                )
                return advisorConfiguration.copy(nexusIq = iqConfig)
            }
        },

        OSV("OSV") {
            override fun configure(
                providerConfig: Config,
                advisorConfiguration: AdvisorConfiguration
            ): AdvisorConfiguration {
                val osvServerUrl = providerConfig.getStringOrDefault(SERVER_URL_PROPERTY, OSV_DEFAULT_URL)
                return advisorConfiguration.copy(osv = OsvConfiguration(osvServerUrl))
            }
        },

        VULNERABLE_CODE("VulnerableCode") {
            override fun configure(
                providerConfig: Config,
                advisorConfiguration: AdvisorConfiguration
            ): AdvisorConfiguration {
                val vcServer = providerConfig.getStringOrDefault(SERVER_URL_PROPERTY)
                val vcKey = providerConfig.getStringOrDefault(API_KEY_PROPERTY)
                return advisorConfiguration.copy(vulnerableCode = VulnerableCodeConfiguration(vcServer, vcKey))
            }
        };

        /**
         * Return a set of keys that are mandatory to configure the associated advisor provider. If some of these
         * keys are missing in the configuration, no instance of this provider can be created.
         */
        open fun mandatoryKeys(): Set<String> = emptySet()

        /**
         * Create a specific configuration for the associated advisor provider from the given [providerConfig] and
         * add it to [advisorConfiguration]. When this function is called it has already been checked that all
         * mandatory properties are set.
         */
        abstract fun configure(
            providerConfig: Config,
            advisorConfiguration: AdvisorConfiguration
        ): AdvisorConfiguration
    }
}
