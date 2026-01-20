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
import com.typesafe.config.ConfigValueFactory

import java.io.InputStreamReader

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin
import org.eclipse.apoapsis.ortserver.utils.config.getBooleanOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getConfigOrEmpty
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getObjectOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getObjectOrEmpty
import org.eclipse.apoapsis.ortserver.utils.config.getStringListOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringListOrEmpty
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull
import org.eclipse.apoapsis.ortserver.utils.config.withPath

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

        /** The name of the section containing the scanner-related configuration. */
        private const val SCANNER_SECTION = "scanner"

        /** The name of the section containing the reporter-related configuration. */
        private const val REPORTER_SECTION = "reporter"

        /** The name of the section containing the notifier-related configuration. */
        private const val NOTIFIER_SECTION = "notifier"

        /** The name of the subsection containing the mail server configuration for the notifier. */
        private const val NOTIFIER_MAIL_SECTION = "mail"

        /** The name of the subsection containing the Jira server configuration for the notifier. */
        private const val NOTIFIER_JIRA_SECTION = "jira"

        /** The name of the section defining global assets for the reporter. */
        private const val GLOBAL_ASSETS_SECTION = "assets"

        /** A special prefix to mark unresolvable assets in the reporter configuration. */
        private const val UNRESOLVABLE_ASSET_PREFIX = "<<UNRESOLVABLE_ASSET>>"

        /** An object to obtain default values for the mail server configuration. */
        private val defaultMailServerConfig = MailServerConfiguration(fromAddress = "")

        private val logger = LoggerFactory.getLogger(AdminConfigService::class.java)

        /**
         * Create an [AdminConfig] instance from the properties defined in the given [config].
         */
        private fun createAdminConfig(config: Config): AdminConfig {
            val defaultRuleSetConfig = config.getConfigOrEmpty("defaultRuleSet")
            val defaultRuleSet = parseRuleSet(defaultRuleSetConfig, AdminConfig.DEFAULT_RULE_SET)
            val ruleSets = parseRuleSets(config, defaultRuleSet)
            val mavenCentralMirror = parseMavenCentralMirror(config)

            return AdminConfig(
                scannerConfig = parseScannerConfig(config),
                reporterConfig = parseReporterConfig(config),
                notifierConfig = parseNotifierConfig(config),
                defaultRuleSet = defaultRuleSet,
                ruleSets = ruleSets,
                mavenCentralMirror = mavenCentralMirror
            )
        }

        /**
         * Parse all named rule sets defined in the given [config] and return a [Map] with the names as keys. For
         * rule sets that are only partially defined, set the values of missing properties from the given
         * [defaultRuleSet].
         */
        private fun parseRuleSets(config: Config, defaultRuleSet: RuleSet): Map<String, RuleSet> =
            config.getObjectOrEmpty("ruleSets").mapNotNull { entry ->
                (entry.value as? ConfigObject)?.let { obj ->
                    entry.key to parseRuleSet(obj.toConfig(), defaultRuleSet)
                }
            }.toMap()

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

        /**
         * Parse the properties related to the scanner configuration from the given [config] and return a corresponding
         * [ScannerConfig] instance.
         */
        private fun parseScannerConfig(config: Config): ScannerConfig =
            config.parseObjectOrDefault(SCANNER_SECTION, AdminConfig.DEFAULT_SCANNER_CONFIG) {
                ScannerConfig(
                    detectedLicenseMappings = getObjectOrDefault("detectedLicenseMappings") {
                        ConfigValueFactory.fromMap(AdminConfig.DEFAULT_SCANNER_CONFIG.detectedLicenseMappings)
                    }.mapValues { it.value.unwrapped().toString() },
                    ignorePatterns = getStringListOrDefault("ignorePatterns") {
                        AdminConfig.DEFAULT_SCANNER_CONFIG.ignorePatterns
                    },
                    sourceCodeOrigins = getStringListOrDefault("sourceCodeOrigins") {
                        AdminConfig.DEFAULT_SCANNER_CONFIG.sourceCodeOrigins.map { it.name }
                    }.map { SourceCodeOrigin.valueOf(it.uppercase()) }
                )
            }

        /**
         * Validate the given [scannerConfig]. Add found issues to the given [issues] list.
         */
        private fun validateScannerConfig(issues: MutableList<String>, scannerConfig: ScannerConfig) {
            if (scannerConfig.sourceCodeOrigins.isEmpty()) {
                issues += "'sourceCodeOrigins' from scanner configuration must not be empty."
            }

            if (scannerConfig.sourceCodeOrigins.toSet().size != scannerConfig.sourceCodeOrigins.size) {
                issues += "'sourceCodeOrigins' from scanner configuration must not contain duplicates. " +
                        "Current value is ${scannerConfig.sourceCodeOrigins}."
            }
        }

        /**
         * Parse the properties related to the notifier configuration from the given [config] and return a
         * corresponding [NotifierConfig] instance.
         */
        private fun parseNotifierConfig(config: Config): NotifierConfig =
            config.parseObjectOrDefault(NOTIFIER_SECTION, AdminConfig.DEFAULT_NOTIFIER_CONFIG) {
                NotifierConfig(
                    notifierRules = getStringOrDefault(
                        "notifierRules",
                        AdminConfig.DEFAULT_NOTIFIER_CONFIG.notifierRules
                    ),
                    disableMailNotifications = getBooleanOrDefault(
                        "disableMailNotifications",
                        AdminConfig.DEFAULT_NOTIFIER_CONFIG.disableMailNotifications
                    ),
                    disableJiraNotifications = getBooleanOrDefault(
                        "disableJiraNotifications",
                        AdminConfig.DEFAULT_NOTIFIER_CONFIG.disableJiraNotifications
                    ),
                    mail = parseNotifierMailConfig(this),
                    jira = parseNotifierJiraConfig(this)
                )
            }

        /**
         * Parse the mail server configuration for the notifier from the given [config] if it is defined.
         */
        private fun parseNotifierMailConfig(config: Config): MailServerConfiguration? =
            config.parseObjectOrDefault(NOTIFIER_MAIL_SECTION, null) {
                MailServerConfiguration(
                    hostName = getStringOrDefault("host", defaultMailServerConfig.hostName),
                    port = getIntOrDefault("port", defaultMailServerConfig.port),
                    username = getStringOrDefault("username", defaultMailServerConfig.username),
                    password = getStringOrDefault("password", defaultMailServerConfig.password),
                    useSsl = getBooleanOrDefault("ssl", defaultMailServerConfig.useSsl),
                    fromAddress = getString("fromAddress")
                )
            }

        /**
         * Parse the Jira server configuration for the notifier from the given [config] if it is defined.
         */
        private fun parseNotifierJiraConfig(config: Config): JiraRestClientConfiguration? =
            config.parseObjectOrDefault(NOTIFIER_JIRA_SECTION, null) {
                JiraRestClientConfiguration(
                    serverUrl = getString("url"),
                    username = getString("username"),
                    password = getString("password")
                )
            }

        /**
         * Parse the properties related to the reporter configuration from the given [config] and return a
         * corresponding [ReporterConfig] instance.
         */
        private fun parseReporterConfig(config: Config): ReporterConfig =
            config.parseObjectOrDefault(REPORTER_SECTION, AdminConfig.DEFAULT_REPORTER_CONFIG) {
                val globalAssets = parseGlobalReporterAssets(this)

                ReporterConfig(
                    howToFixTextProviderFile = getStringOrDefault(
                        "howToFixTextProviderFile",
                        ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
                    ),
                    customLicenseTextDir = getStringOrNull("customLicenseTextDir"),
                    reportDefinitionsMap = parseReportDefinitions(this, globalAssets),
                    globalAssets = globalAssets
                )
            }

        /**
         * Parse the report definitions defined in the given [config] using the given [globalAssets].
         */
        private fun parseReportDefinitions(
            config: Config,
            globalAssets: GlobalReporterAssets
        ): Map<String, ReportDefinition> =
            ReporterConfig.addDefinitionsForUnreferencedPlugins(
                config.getObjectOrEmpty("reports").filterValues { it is ConfigObject }
                    .mapValues { entry ->
                        parseReportDefinition((entry.value as ConfigObject).toConfig(), globalAssets)
                    }
            )

        /**
         * Parse a [ReportDefinition] at the root of the given [config] using the given [globalAssets].
         */
        private fun parseReportDefinition(
            config: Config,
            globalAssets: GlobalReporterAssets
        ): ReportDefinition =
            ReportDefinition(
                pluginId = config.getString("pluginId"),
                assetFiles = parseReporterAssets(config, "assetFiles", isDirectory = false) +
                        parseReporterAssetReferences(config, "assetFilesRefs", globalAssets, isDirectory = false),
                assetDirectories = parseReporterAssets(config, "assetDirectories", isDirectory = true) +
                        parseReporterAssetReferences(config, "assetDirectoriesRefs", globalAssets, isDirectory = true),
                nameMapping = config.parseObjectOrDefault("nameMapping", null) {
                    ReportNameMapping(
                        namePrefix = getString("namePrefix"),
                        startIndex = getIntOrDefault("startIndex", 1),
                        alwaysAppendIndex = getBooleanOrDefault("alwaysAppendIndex", false)
                    )
                }
            )

        /**
         * Validate the given [reporterConfig]. Add found issues to the given [issues] list.
         */
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

        /**
         * Add all configuration files referenced by this [RuleSet] to the given [target] set for validation.
         */
        private fun RuleSet.getConfigurationFiles(target: MutableSet<String>) {
            target.addNonDefault(copyrightGarbageFile, AdminConfig.DEFAULT_RULE_SET.copyrightGarbageFile)
            target.addNonDefault(licenseClassificationsFile, AdminConfig.DEFAULT_RULE_SET.licenseClassificationsFile)
            target.addNonDefault(resolutionsFile, AdminConfig.DEFAULT_RULE_SET.resolutionsFile)
            target.addNonDefault(evaluatorRules, AdminConfig.DEFAULT_RULE_SET.evaluatorRules)
        }

        /**
         * Add all configuration files referenced by this [ReporterConfig] to the given [target] set for validation.
         */
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

        /**
         * Add the given [path] to this [Set] only if it is not *null* and not equal to the given [default].
         */
        private fun MutableSet<String>.addNonDefault(path: String?, default: String) {
            if (path != null && path != default) {
                add(path)
            }
        }

        /**
         * Parse the section with the top-level reporter assets in the given [config]. These assets can then be
         * referenced in report definitions. Return a [Map] that associates names to asset groups.
         */
        private fun parseGlobalReporterAssets(config: Config): GlobalReporterAssets =
            config.getObjectOrEmpty(GLOBAL_ASSETS_SECTION).mapValues { entry ->
                parseReporterAssets(config, "${GLOBAL_ASSETS_SECTION}.${entry.key}", isDirectory = false)
            }

        /**
         * Parse a list of [ReporterAsset]s from the given [config] at the specified [path]. If [isDirectory] is
         * *true*, make sure that all source paths end with a trailing slash.
         */
        private fun parseReporterAssets(config: Config, path: String, isDirectory: Boolean): List<ReporterAsset> =
            if (config.hasPath(path)) {
                config.getConfigList(path).map { c ->
                    ReporterAsset(
                        sourcePath = directoryPath(c.getString("sourcePath"), isDirectory),
                        targetFolder = c.getStringOrNull("targetFolder"),
                        targetName = c.getStringOrNull("targetName")
                    )
                }
            } else {
                emptyList()
            }

        /**
         * Parse a list of named references to [ReporterAsset]s from the given [config] at the specified [path] that
         * are resolved against the given [globalAssets]. If [isDirectory] is *true*, make sure that the path for
         * the asset ends with a slash. In case of unresolvable references, return special dummy assets; they will be
         * detected by the validation and cause meaningful error messages to be generated.
         */
        private fun parseReporterAssetReferences(
            config: Config,
            path: String,
            globalAssets: GlobalReporterAssets,
            isDirectory: Boolean
        ): List<ReporterAsset> =
            config.getStringListOrEmpty(path).flatMap { globalAssets.resolveAssetReference(it, isDirectory) }

        /**
         * Return a list of [ReporterAsset]s that are assigned to the given [assetRef] name. If [isDirectory] is
         * *true*, make sure that the path for the asset ends with a slash. If no asset with the provided name
         * exists, return a special result that can later be detected by the validation.
         */
        private fun GlobalReporterAssets.resolveAssetReference(
            assetRef: String,
            isDirectory: Boolean
        ): List<ReporterAsset> =
            this[assetRef]?.map { directoryAsset(it, isDirectory) }
                ?: listOf(ReporterAsset(UNRESOLVABLE_ASSET_PREFIX + assetRef))

        private fun parseMavenCentralMirror(config: Config): MavenCentralMirror? =
            config.parseObjectOrDefault("mavenCentralMirror", null) {
                MavenCentralMirror(
                    id = getString("id"),
                    name = getString("name"),
                    url = getString("url"),
                    mirrorOf = getString("mirrorOf"),
                    usernameSecret = getStringOrNull("username"),
                    passwordSecret = getStringOrNull("password")
                )
            }

        /**
         * Parse an object defined at the given [path] in this [Config] using the provided [parser] function.
         * Return the given [default] object if the path does not exist.
         */
        private fun <T> Config.parseObjectOrDefault(path: String, default: T, parser: Config.() -> T): T =
            withPath(path)?.let { c ->
                parser(c.getConfig(path))
            } ?: default

        /**
         * Make sure the given [path] points to a directory if [isDirectory] is *true* by appending a trailing slash if
         * necessary.
         */
        private fun directoryPath(path: String, isDirectory: Boolean): String =
            if (isDirectory && !path.endsWith("/")) "$path/" else path

        /**
         * Make sure the source path of the given [asset] points to a directory if [isDirectory] is *true* by
         * appending a trailing slash to its source path if necessary.
         */
        private fun directoryAsset(asset: ReporterAsset, isDirectory: Boolean): ReporterAsset =
            asset.takeIf { !isDirectory || asset.sourcePath.endsWith("/") }
                ?: asset.copy(sourcePath = "${asset.sourcePath}/")
    }

    /**
     * Load the [AdminConfig] from the configured path in the given [context] for the given [organizationId].
     * [Optionally][validate], perform a validation after loading.
     * TODO: The organization ID is currently not evaluated. In the future, it should be supported to have different
     *       configurations for different organizations.
     */
    fun loadAdminConfig(context: Context?, organizationId: Long, validate: Boolean = false): AdminConfig {
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

        return createAdminConfig(config).also {
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
            throw ConfigException(
                "Invalid admin configuration:\n ${issues.joinToString(separator = "\n")}",
                null
            )
        }

        return config
    }
}
