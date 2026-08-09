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
    val mavenCentralMirror: MavenCentralMirror?
)

/** A representation of the notifier section of the admin config file. */
internal data class NotifierConfigTemplate(
    val notifierRules: String = "run.notifications.kts",
    val jira: JiraRestClientConfiguration? = null,
    val mail: MailServerConfiguration? = null,
    val disableJiraNotifications: Boolean = false,
    val disableMailNotifications: Boolean = false
)

/** A representation of the reporter section of the admin config file. */
internal data class ReporterConfigTemplate(
    val reports: Map<String, ReportDefinitionTemplate>?,
    val howToFixTextProviderFile: String = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
    val customLicenseTextDir: String?,
    val assets: Map<String, List<ReporterAsset>> = emptyMap()
)

/** A representation of the report definition section of the admin config file. */
internal data class ReportDefinitionTemplate(
    val pluginId: String,
    val assetFiles: List<ReporterAsset> = emptyList(),
    val assetFilesRefs: List<String> = emptyList(),
    val assetDirectories: List<ReporterAsset> = emptyList(),
    val assetDirectoriesRefs: List<String> = emptyList(),
    val nameMapping: ReportNameMapping? = null
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
