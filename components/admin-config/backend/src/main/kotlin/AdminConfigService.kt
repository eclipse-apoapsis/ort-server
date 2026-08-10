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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addStreamSource

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault

import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME

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
@Suppress("TooManyFunctions")
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

        /** A special prefix to mark unresolvable assets in the reporter configuration. */
        internal const val UNRESOLVABLE_ASSET_PREFIX = "<<UNRESOLVABLE_ASSET>>"

        private val logger = LoggerFactory.getLogger(AdminConfigService::class.java)

        /** Validate the given [scannerConfig]. Add found issues to the given [issues] list. */
        private fun validateScannerConfig(issues: MutableList<String>, scannerConfig: ScannerConfig) {
            if (scannerConfig.sourceCodeOrigins.isEmpty()) {
                issues += "'sourceCodeOrigins' from scanner configuration must not be empty."
            }

            if (scannerConfig.sourceCodeOrigins.toSet().size != scannerConfig.sourceCodeOrigins.size) {
                issues += "'sourceCodeOrigins' from scanner configuration must not contain duplicates. " +
                        "Current value is ${scannerConfig.sourceCodeOrigins}."
            }
        }

        /** Validate the given [reporterConfig]. Add found issues to the given [issues] list. */
        private fun validateReporterConfig(issues: MutableList<String>, reporterConfig: ReporterConfig) {
            issues += reporterConfig.reportDefinitions.flatMapTo(mutableSetOf()) { definition ->
                (definition.assetFiles + definition.assetDirectories).map(ReporterAsset::sourcePath)
                    .filter { it.startsWith(UNRESOLVABLE_ASSET_PREFIX) }
            }.map { "Undefined reference to a reporter asset: '${it.removePrefix(UNRESOLVABLE_ASSET_PREFIX)}'." }

            reporterConfig.validateReportDefinitions(issues)
        }

        /**
         * Return a [Set] with the paths to all configuration files referenced by this [AdminConfig]. This is used
         * to perform a validation after loading the configuration whether these files can actually be resolved.
         * The set does not contain any default paths because for those it is allowed to not exist.
         */
        private fun getConfigurationFiles(config: AdminConfig): Set<String> = buildSet {
            config.getRuleSet(null).getConfigurationFiles(this)

            config.ruleSetNames.forEach { ruleSet ->
                config.getRuleSet(ruleSet).getConfigurationFiles(this)
            }

            config.reporterConfig.getConfigurationFiles(this)
        }

        /** Add all configuration files referenced by this [RuleSet] to the given [target] set for validation. */
        private fun RuleSet.getConfigurationFiles(target: MutableSet<String>) {
            target.addNonDefault(copyrightGarbageFile, RuleSet.DEFAULT_COPYRIGHT_GARBAGE_FILE)
            target.addNonDefault(licenseClassificationsFile, RuleSet.DEFAULT_LICENSE_CLASSIFICATIONS_FILE)
            target.addNonDefault(resolutionsFile, RuleSet.DEFAULT_RESOLUTIONS_FILE)
            target.addNonDefault(evaluatorRules, RuleSet.DEFAULT_EVALUATOR_RULES_FILE)
        }

        /** Add all configuration files referenced by this [ReporterConfig] to the given [target] set for validation. */
        private fun ReporterConfig.getConfigurationFiles(target: MutableSet<String>) {
            target.addNonDefault(howToFixTextProviderFile, ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME)
            customLicenseTextDir?.also(target::add)
            target += globalAssets.values.flatMap { it.map(ReporterAsset::sourcePath) }

            reportDefinitions.forEach { definition ->
                (definition.assetFiles + definition.assetDirectories).map(ReporterAsset::sourcePath)
                    .filterNot { it.startsWith(UNRESOLVABLE_ASSET_PREFIX) }
                    .forEach(target::add)
            }
        }

        /** Add the given [path] to this [Set] only if it is not *null* and not equal to the given [default]. */
        private fun MutableSet<String>.addNonDefault(path: String?, default: String) {
            if (path != null && path != default) {
                add(path)
            }
        }
    }

    /**
     * Load the [AdminConfig] from the configured path in the given [context].
     * [Optionally][validate], perform a validation after loading.
     */
    fun loadAdminConfig(context: Context?, validate: Boolean = false): AdminConfig {
        val configPath = Path(configManager.getStringOrDefault(PATH_PROPERTY, DEFAULT_PATH))
        if (configPath.path == DEFAULT_PATH && !configManager.containsFile(context, configPath)) {
            logger.warn(
                "No configuration path configured, and the default path '{}' does not exist. " +
                        "Using the default admin configuration.",
                DEFAULT_PATH
            )
            return AdminConfig.DEFAULT
        }

        logger.info("Loading admin configuration from path '{}'.", configPath.path)
        val adminConfigFile = configManager.getFile(context, configPath).use {
            ConfigLoaderBuilder.default()
                .addStreamSource(it, "conf")
                .withResolveTypesCaseInsensitive()
                .build()
                .loadConfigOrThrow<AdminConfigFile>()
        }

        return AdminConfigResolver.resolve(adminConfigFile).also {
            if (validate) {
                validate(context, it)
            }
        }
    }

    /**
     * Perform a validation of the given [config]. Throw an exception if problems are encountered. This function is
     * called after loading the configuration to fail early if invalid properties are found.
     */
    private fun validate(context: Context?, config: AdminConfig): AdminConfig {
        val unresolvableFiles = getConfigurationFiles(config).filterNot { file ->
            configManager.containsFile(context, Path(file))
        }

        val issues = mutableListOf<String>()

        if (unresolvableFiles.isNotEmpty()) {
            issues += "Found unresolvable configuration files referenced from the admin configuration: " +
                    "${unresolvableFiles.joinToString(separator = ", ") { "'$it'" }}."
        }

        validateScannerConfig(issues, config.scannerConfig)
        validateReporterConfig(issues, config.reporterConfig)

        if (issues.isNotEmpty()) {
            throw ConfigException("Invalid admin configuration:\n ${issues.joinToString(separator = "\n")}")
        }

        return config
    }
}
