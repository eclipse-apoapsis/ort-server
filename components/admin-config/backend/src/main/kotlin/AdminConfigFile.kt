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

import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME

/** A representation of the admin config file. */
internal data class AdminConfigFile(
    val notifier: NotifierConfigTemplate?,
    val reporter: ReporterConfigTemplate?,
    val scanner: ScannerConfigTemplate?,
    val defaultRuleSet: RuleSetTemplate?,
    val ruleSets: Map<String, RuleSetTemplate>?,
    val mavenCentralMirror: MavenCentralMirrorTemplate?
)

/** A representation of the notifier section of the admin config file. */
internal data class NotifierConfigTemplate(
    val notifierRules: String = "run.notifications.kts",
    val jira: JiraConfigTemplate? = null,
    val mail: MailConfigTemplate? = null,
    val disableJiraNotifications: Boolean = false,
    val disableMailNotifications: Boolean = false
)

/** A representation of the jira section of the admin config file. */
internal data class JiraConfigTemplate(
    val url: String,
    val username: String,
    val password: String
)

/** A representation of the mail section of the admin config file. */
internal data class MailConfigTemplate(
    val host: String = "localhost",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val ssl: Boolean = true,
    val fromAddress: String
)

/** A representation of the reporter section of the admin config file. */
internal data class ReporterConfigTemplate(
    val reports: Map<String, ReportDefinitionTemplate>?,
    val howToFixTextProviderFile: String = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
    val customLicenseTextDir: String?,
    val assets: Map<String, List<ReporterAssetTemplate>>?
)

/** A representation of the report definition section of the admin config file. */
internal data class ReportDefinitionTemplate(
    val pluginId: String,
    val assetFiles: List<ReporterAssetTemplate> = emptyList(),
    val assetFilesRefs: List<String> = emptyList(),
    val assetDirectories: List<ReporterAssetTemplate> = emptyList(),
    val assetDirectoriesRefs: List<String> = emptyList(),
    val nameMapping: ReportNameMappingTemplate? = null
)

/** A representation of the reporter asset section of the admin config file. */
internal data class ReporterAssetTemplate(
    val sourcePath: String,
    val targetFolder: String? = null,
    val targetName: String? = null
)

/** A representation of the report name mapping section of the admin config file. */
internal data class ReportNameMappingTemplate(
    val namePrefix: String,
    val startIndex: Int = 1,
    val alwaysAppendIndex: Boolean = false
)

/** A representation of the scanner section of the admin config file. */
internal data class ScannerConfigTemplate(
    val detectedLicenseMappings: Map<String, String>?,
    val ignorePatterns: List<String>?,
    val sourceCodeOrigins: List<String>?
)

/** A representation of the rule set section of the admin config file. */
internal data class RuleSetTemplate(
    val copyrightGarbageFile: String?,
    val licenseClassificationsFile: String?,
    val resolutionsFile: String?,
    val evaluatorRules: String?
)

/** A representation of the maven central mirror section of the admin config file. */
internal data class MavenCentralMirrorTemplate(
    val id: String,
    val name: String,
    val url: String,
    val mirrorOf: String,
    val username: String? = null,
    val password: String? = null
)
