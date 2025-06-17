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

package org.eclipse.apoapsis.ortserver.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject

import java.io.InputStreamReader

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getConfigOrEmpty
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault

import org.slf4j.LoggerFactory

/**
 * A service providing access to the ORT Server Admin configuration.
 *
 * The Admin configuration is obtained from the `ConfigFileProvider` managed by the [ConfigManager]. The path to this
 * configuration file is defined by the [PATH_PROPERTY] property in the current configuration. If this property
 * is not defined, the [DEFAULT_PATH] is used instead. It is possible to run ORT Server without an Admin configuration,
 * although this will hardly be useful in practice. To enable this mode, the [PATH_PROPERTY] property must be
 * unspecified, and the [DEFAULT_PATH] must not exist. In all other cases, a missing configuration file causes an
 * exception to be thrown.
 */
class AdminConfigService(
    private val configManager: ConfigManager
) {
    companion object {
        /**
         * The name of the property defining the path to the Admin configuration file. If this is undefined, a
         * default path is assumed.
         */
        const val PATH_PROPERTY = "adminConfigPath"

        /** The default path to the Admin configuration file. */
        const val DEFAULT_PATH = "ort-server.conf"

        private val logger = LoggerFactory.getLogger(AdminConfigService::class.java)

        /**
         * Create an [AdminConfig] instance from the properties defined in the given [config].
         */
        private fun createAdminConfig(config: Config): AdminConfig {
            val defaultRuleSetConfig = config.getConfigOrEmpty("defaultRuleSet")
            val defaultRuleSet = parseRuleSet(defaultRuleSetConfig, AdminConfig.DEFAULT_RULE_SET)
            val ruleSets = parseRuleSets(config, defaultRuleSet)

            return AdminConfig(
                defaultRuleSet = defaultRuleSet,
                ruleSets = ruleSets
            )
        }

        /**
         * Parse all named rule sets defined in the given [config] and return a [Map] with the names as keys. For
         * rule sets that are only partially defined, set the values of missing properties from the given
         * [defaultRuleSet].
         */
        private fun parseRuleSets(config: Config, defaultRuleSet: RuleSet): Map<String, RuleSet> {
            if (!config.hasPath("ruleSets")) return emptyMap()

            return config.getObject("ruleSets").mapNotNull { entry ->
                (entry.value as? ConfigObject)?.let { obj ->
                    entry.key to parseRuleSet(obj.toConfig(), defaultRuleSet)
                }
            }.toMap()
        }

        /**
         * Create a [RuleSet] based on the properties in the given [config]. Use the values from the given [default]
         * instance for undefined properties.
         */
        private fun parseRuleSet(config: Config, default: RuleSet): RuleSet =
            RuleSet(
                copyrightGarbageFile = config.getStringOrDefault("copyrightGarbageFile", default.copyrightGarbageFile),
                licenseClassificationsFile = config.getStringOrDefault(
                    "licenseClassificationsFile",
                    default.licenseClassificationsFile
                ),
                resolutionsFile = config.getStringOrDefault("resolutionsFile", default.resolutionsFile),
                evaluatorRules = config.getStringOrDefault("evaluatorRules", default.evaluatorRules)
            )
    }

    /**
     * Load the [AdminConfig] from the configured path in the given [context] for the given [organizationId].
     * TODO: The organization ID is currently not evaluated. In the future, it should be supported to have different
     *       configurations for different organizations.
     */
    fun loadAdminConfig(context: Context?, organizationId: Long): AdminConfig {
        val configPath = Path(configManager.getStringOrDefault(PATH_PROPERTY, DEFAULT_PATH))
        if (configPath.path == DEFAULT_PATH && !configManager.containsFile(context, configPath)) {
            logger.warn(
                "No configuration path configured, and the default path '{}' does not exist. " +
                        "Using the default admin configuration.",
                DEFAULT_PATH
            )
            return AdminConfig.DEFAULT
        }

        logger.info("Loading admin configuration from path '{}' for organization {}.", configPath.path, organizationId)
        val config = InputStreamReader(configManager.getFile(context, configPath)).use { reader ->
            ConfigFactory.parseReader(reader)
        }

        return createAdminConfig(config)
    }
}
