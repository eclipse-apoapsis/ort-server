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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import com.typesafe.config.Config

import org.eclipse.apoapsis.ortserver.utils.config.getBooleanOrNull
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrNull
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull
import org.eclipse.apoapsis.ortserver.utils.config.withPath

/** A representation of the admin config file. */
internal data class AdminConfigFile(
    val notifier: NotifierConfigTemplate?,
    val reporter: ReporterConfigTemplate?,
    val scanner: ScannerConfigTemplate?,
    val defaultRuleSet: RuleSetTemplate?,
    val ruleSets: Map<String, RuleSetTemplate>?,
    val mavenCentralMirror: MavenCentralMirrorTemplate?
) {
    internal companion object {
        fun parse(config: Config): AdminConfigFile {
            val notifierConfig = config.getConfigOrNull("notifier")?.let { NotifierConfigTemplate.parse(it) }
            val reporterConfig = config.getConfigOrNull("reporter")?.let { ReporterConfigTemplate.parse(it) }
            val scannerConfig = config.getConfigOrNull("scanner")?.let { ScannerConfigTemplate.parse(it) }
            val defaultRuleSet = config.getConfigOrNull("defaultRuleSet")?.let { RuleSetTemplate.parse(it) }
            val mavenCentralMirror = config.getConfigOrNull("mavenCentralMirror")?.let { MavenCentralMirrorTemplate.parse(it) }

            val ruleSets = config.getConfigOrNull("ruleSets")?.let { ruleSetsConfig ->
                ruleSetsConfig.root().keys.associateWith { key ->
                    RuleSetTemplate.parse(ruleSetsConfig.getConfig(key))
                }
            }

            return AdminConfigFile(
                notifierConfig,
                reporterConfig,
                scannerConfig,
                defaultRuleSet,
                ruleSets,
                mavenCentralMirror
            )
        }
    }
}

/** A representation of the notifier section of the admin config file. */
internal data class NotifierConfigTemplate(
    val notifierRules: String?,
    val jira: JiraConfigTemplate?,
    val mail: MailConfigTemplate?,
    val disableJiraNotifications: Boolean?,
    val disableMailNotifications: Boolean?
) {
    internal companion object {
        fun parse(config: Config): NotifierConfigTemplate {
            val notifierRules = config.getStringOrNull("notifierRules")
            val jira = config.getConfigOrNull("jira")?.let { JiraConfigTemplate.parse(it) }
            val mail = config.getConfigOrNull("mail")?.let { MailConfigTemplate.parse(it) }
            val disableJiraNotifications = config.getBooleanOrNull("disableJiraNotifications")
            val disableMailNotifications = config.getBooleanOrNull("disableMailNotifications")

            return NotifierConfigTemplate(
                notifierRules = notifierRules,
                jira = jira,
                mail = mail,
                disableJiraNotifications = disableJiraNotifications,
                disableMailNotifications = disableMailNotifications
            )
        }
    }
}

/** A representation of the jira section of the admin config file. */
internal data class JiraConfigTemplate(
    val url: String,
    val username: String,
    val password: String
) {
    internal companion object {
        fun parse(config: Config) =
            JiraConfigTemplate(
                url = config.getString("url"),
                username = config.getString("username"),
                password = config.getString("password")
            )
    }
}

/** A representation of the mail section of the admin config file. */
internal data class MailConfigTemplate(
    val host: String?,
    val port: Int?,
    val username: String?,
    val password: String?,
    val ssl: Boolean?,
    val fromAddress: String
) {
    internal companion object {
        fun parse(config: Config) =
            MailConfigTemplate(
                host = config.getStringOrNull("host"),
                port = config.getIntOrNull("port"),
                username = config.getStringOrNull("username"),
                password = config.getStringOrNull("password"),
                ssl = config.getBooleanOrNull("ssl"),
                fromAddress = config.getString("fromAddress")
            )
    }
}

/** A representation of the reporter section of the admin config file. */
internal data class ReporterConfigTemplate(
    val reports: Map<String, ReportDefinitionTemplate>?,
    val howToFixTextProviderFile: String?,
    val customLicenseTextDir: String?,
    val assets: Map<String, List<ReporterAssetTemplate>>?
) {
    internal companion object {
        fun parse(config: Config): ReporterConfigTemplate {
            val reports = config.getConfigOrNull("reports")?.let { reportsConfig ->
                reportsConfig.root().keys.associateWith { key ->
                    ReportDefinitionTemplate.parse(reportsConfig.getConfig(key))
                }
            }

            val howToFixTextProviderFile = config.getStringOrNull("howToFixTextProviderFile")
            val customLicenseTextDir = config.getStringOrNull("customLicenseTextDir")

            val assets = config.getConfigOrNull("assets")?.let { globalAssetsConfig ->
                globalAssetsConfig.root().keys.associateWith { key ->
                    globalAssetsConfig.getConfigList(key).map { ReporterAssetTemplate.parse(it) }
                }
            }

            return ReporterConfigTemplate(
                reports = reports,
                howToFixTextProviderFile = howToFixTextProviderFile,
                customLicenseTextDir = customLicenseTextDir,
                assets = assets
            )
        }
    }
}

/** A representation of the report definition section of the admin config file. */
internal data class ReportDefinitionTemplate(
    val pluginId: String,
    val assetFiles: List<ReporterAssetTemplate>?,
    val assetFilesRefs: List<String>?,
    val assetDirectories: List<ReporterAssetTemplate>?,
    val assetDirectoriesRefs: List<String>?,
    val nameMapping: ReportNameMappingTemplate?
) {
    internal companion object {
        fun parse(config: Config): ReportDefinitionTemplate {
            val pluginId = config.getString("pluginId")
            val assetFiles = config.getConfigListOrNull("assetFiles")?.map { ReporterAssetTemplate.parse(it) }
            val assetFilesRefs = config.getStringListOrNull("assetFilesRefs")
            val assetDirectories = config.getConfigListOrNull("assetDirectories")?.map { ReporterAssetTemplate.parse(it) }
            val assetDirectoriesRefs = config.getStringListOrNull("assetDirectoriesRefs")
            val nameMapping = config.getConfigOrNull("nameMapping")?.let { ReportNameMappingTemplate.parse(it) }

            return ReportDefinitionTemplate(
                pluginId = pluginId,
                assetFiles = assetFiles,
                assetFilesRefs = assetFilesRefs,
                assetDirectories = assetDirectories,
                assetDirectoriesRefs = assetDirectoriesRefs,
                nameMapping = nameMapping
            )
        }
    }
}

/** A representation of the reporter asset section of the admin config file. */
internal data class ReporterAssetTemplate(
    val sourcePath: String,
    val targetFolder: String?,
    val targetName: String?
) {
    internal companion object {
        fun parse(config: Config) =
            ReporterAssetTemplate(
                sourcePath = config.getString("sourcePath"),
                targetFolder = config.getStringOrNull("targetFolder"),
                targetName = config.getStringOrNull("targetName")
            )
    }
}

/** A representation of the report name mapping section of the admin config file. */
internal data class ReportNameMappingTemplate(
    val namePrefix: String,
    val startIndex: Int?,
    val alwaysAppendIndex: Boolean?
) {
    internal companion object {
        fun parse(config: Config) =
            ReportNameMappingTemplate(
                namePrefix = config.getString("namePrefix"),
                startIndex = config.getIntOrNull("startIndex"),
                alwaysAppendIndex = config.getBooleanOrNull("alwaysAppendIndex")
            )
    }
}

/** A representation of the scanner section of the admin config file. */
internal data class ScannerConfigTemplate(
    val detectedLicenseMappings: Map<String, String>?,
    val ignorePatterns: List<String>?,
    val sourceCodeOrigins: List<String>?
) {
    internal companion object {
        fun parse(config: Config) =
            ScannerConfigTemplate(
                detectedLicenseMappings = config.getObjectOrNull("detectedLicenseMappings")
                    ?.mapValues { it.value.unwrapped().toString() },
                ignorePatterns = config.getStringListOrNull("ignorePatterns"),
                sourceCodeOrigins = config.getStringListOrNull("sourceCodeOrigins")
            )
    }
}

/** A representation of the rule set section of the admin config file. */
internal data class RuleSetTemplate(
    val copyrightGarbageFile: String?,
    val licenseClassificationsFile: String?,
    val resolutionsFile: String?,
    val evaluatorRules: String?
) {
    internal companion object {
        fun parse(config: Config) =
            RuleSetTemplate(
                copyrightGarbageFile = config.getStringOrNull("copyrightGarbageFile"),
                licenseClassificationsFile = config.getStringOrNull("licenseClassificationsFile"),
                resolutionsFile = config.getStringOrNull("resolutionsFile"),
                evaluatorRules = config.getStringOrNull("evaluatorRules")
            )
    }
}

/** A representation of the maven central mirror section of the admin config file. */
internal data class MavenCentralMirrorTemplate(
    val id: String,
    val name: String,
    val url: String,
    val mirrorOf: String,
    val username: String?,
    val password: String?
) {
    internal companion object {
        fun parse(config: Config) =
            MavenCentralMirrorTemplate(
                id = config.getString("id"),
                name = config.getString("name"),
                url = config.getString("url"),
                mirrorOf = config.getString("mirrorOf"),
                username = config.getStringOrNull("username"),
                password = config.getStringOrNull("password")
            )
    }
}

private fun Config.getConfigOrNull(path: String) = withPath(path)?.getConfig(path)

private fun Config.getConfigListOrNull(path: String) = withPath(path)?.getConfigList(path)

private fun Config.getObjectOrNull(path: String) = withPath(path)?.getObject(path)

private fun Config.getStringListOrNull(path: String) = withPath(path)?.getStringList(path)