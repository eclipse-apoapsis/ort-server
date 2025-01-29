/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration

/**
 * A typealias for key-value pairs.
 */
typealias Options = Map<String, String>

/**
 * The configurations for the jobs in an [OrtRun].
 */
@Serializable
data class JobConfigurations(
    val analyzer: AnalyzerJobConfiguration = AnalyzerJobConfiguration(),
    val advisor: AdvisorJobConfiguration? = null,
    val scanner: ScannerJobConfiguration? = null,
    val evaluator: EvaluatorJobConfiguration? = null,
    val reporter: ReporterJobConfiguration? = null,
    val notifier: NotifierJobConfiguration? = null,

    /**
     * A map with custom parameters for the whole ORT run. The parameters can be evaluated by the validation script
     * executed by the Config worker. The script can convert these parameters to specific job configurations.
     */
    val parameters: Options = emptyMap()
)

/**
 * The configuration for an analyzer job.
 */
@Serializable
data class AnalyzerJobConfiguration(
    /**
     * Enable the analysis of projects that use version ranges to declare their dependencies. If set to true,
     * dependencies of exactly the same project might change with another scan done at a later time if any of the
     * (transitive) dependencies are declared using version ranges, and a new version of such a dependency was
     * published in the meantime. If set to false, analysis of projects that use version ranges will fail. Defaults to
     * false.
     */
    val allowDynamicVersions: Boolean = false,

    /**
     * A list of the case-insensitive names of package managers that are disabled. Disabling a package manager in this
     * list overrides [enabledPackageManagers].
     */
    val disabledPackageManagers: List<String>? = null,

    /**
     * A list of the case-insensitive names of package managers that are enabled. Disabling a package manager in
     * [disabledPackageManagers] overrides enabling it here. If this is `null`, all package manager plugins that have
     * `isEnabledByDefault` set to `true` are used.
     */
    val enabledPackageManagers: List<String>? = null,

    /**
     * An optional [EnvironmentConfig] to be used for this run. If this configuration is defined, it replaces the
     * configuration defined in the repository (if any).
     */
    val environmentConfig: EnvironmentConfig? = null,

    /**
     * The strategy to use for fetching submodules.
     *
     * Note: Submodule fetch strategy [SubmoduleFetchStrategy.TOP_LEVEL_ONLY] is only supported for Git repositories.
     */
    val submoduleFetchStrategy: SubmoduleFetchStrategy? = SubmoduleFetchStrategy.FULLY_RECURSIVE,

    /**
     * The list of package curation providers to use.
     */
    val packageCurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * Package manager specific configurations. The key needs to match the name of the package manager class, e.g.
     * "NuGet" for the NuGet package manager.
     */
    val packageManagerOptions: Map<String, PackageManagerConfiguration>? = null,

    /**
     * The optional path to a repository configuration file. If this is not defined, the repository configuration is
     * read from the default location `.ort.yml`.
     */
    val repositoryConfigPath: String? = null,

    /**
     * A flag to control whether excluded scopes and paths should be skipped during the analysis.
     */
    val skipExcluded: Boolean? = null
)

/**
 * The configuration for an advisor job.
 */
@Serializable
data class AdvisorJobConfiguration(
    /**
     * The Advisors to use (e.g., NexusIQ, VulnerableCode, DefectDB).
     */
    val advisors: List<String> = emptyList(),

    /**
     * Do not advise excluded packages.
     */
    val skipExcluded: Boolean = false,

    /**
     * A map of plugin configurations that are specific to a concrete advisor.
     */
    val config: Map<String, PluginConfiguration>? = null
)

/**
 * The configuration for a scanner job.
 */
@Serializable
data class ScannerJobConfiguration(
    /**
     * Mappings from licenses returned by the scanner to valid SPDX licenses. Note that these mappings are only applied
     * in new scans, stored scan results are not affected.
     */
    val detectedLicenseMappings: Map<String, String>? = null,

    /**
     * A list of glob expressions that match file paths which are to be excluded from scan results.
     */
    val ignorePatterns: List<String>? = null,

    /**
     * The list of the names of the scanners to use to scan projects. If this is `null`, projects are scanned with the
     * configured [scanners].
     */
    val projectScanners: List<String>? = null,

    /**
     * The list of the names of the scanners to use.
     */
    val scanners: List<String>? = listOf("ScanCode"),

    /**
     * A flag to indicate whether packages that have a concluded license and authors set (to derive copyrights from)
     * should be skipped in the scan in favor of only using the declared information.
     */
    val skipConcluded: Boolean? = null,

    /**
     * Do not scan excluded projects or packages.
     */
    val skipExcluded: Boolean = false,

    /**
     * The source code origins to use, ordered by priority. The list must not be empty or contain any duplicates. If
     * `null`, the default order of [SourceCodeOrigin.ARTIFACT] and [SourceCodeOrigin.VCS] is used.
     */
    val sourceCodeOrigins: List<SourceCodeOrigin>? = null,

    /**
     * A map of plugin configurations that are specific to a concrete scanner.
     */
    val config: Map<String, PluginConfiguration>? = null
)

/**
 * The configuration for an evaluator job.
 */
@Serializable
data class EvaluatorJobConfiguration(
    /**
     * The path to the copyright garbage file which is resolved from the configured configuration source. If this is
     * `null`, the default path from ORT will be used.
     */
    val copyrightGarbageFile: String? = null,

    /**
     * The path to the license classifications file which is resolved from the configured configuration source. If this
     * is `null`, the default path from ORT will be used.
     */
    val licenseClassificationsFile: String? = null,

    /**
     * The list of package configuration providers to use.
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * The path to the resolutions file which is resolved from the configured configuration source. If this is `null`,
     * the default path from ORT will be used.
     */
    val resolutionsFile: String? = null,

    /**
     * The id of the rule set to use for the evaluation.
     */
    val ruleSet: String? = null
)

/**
 * A class defining an asset (such as a font or an image) which is required to generate a report.
 *
 * The Reporter can be configured to download specific assets from the configuration before starting the report
 * generation. That way it is ensured that files referenced from reporter templates are actually available locally at
 * the expected relative paths.
 *
 * A [ReporterAsset] can be a single file or a directory. In the latter case, all files contained in the directory are
 * downloaded.
 */
@Serializable
data class ReporterAsset(
    /**
     * The source path of this asset in the configuration. This path is passed to the configuration manager in order to
     * download this asset.
     */
    val sourcePath: String,

    /**
     * A path (relative to the location of reporter template files) where this asset should be placed. Typically,
     * assets are referenced via relative paths from reporter templates, e.g. _./images/logo.png_. Using this
     * property, such a relative path can be specified. If it is `null`, the root folder of the reporter worker
     * (which also contains the downloaded templates) is used.
     */
    val targetFolder: String? = null,

    /**
     * An optional name for the downloaded asset. This property can be used to rename the asset file or folder locally.
     * If it is undefined, the original name (determined from the last path component of [sourcePath]) is used.
     */
    val targetName: String? = null
)

/**
 * A class that can be used to configure the names under which reports are stored.
 *
 * Per default, the names of reports are determined by the reporters that produce these reports. This may not always
 * be desired, especially if names are constructed dynamically based on some conventions. This class can be used to
 * override the default names used by reporters. The reporter configuration contains a map that assigns each report
 * type an optional instance of this class. The configuration contained in this instance is then used to generate the
 * names for reports.
 */
@Serializable
data class ReportNameMapping(
    /**
     * A name prefix for constructing report names. The names of all reports generated by a reporter will start with
     * this prefix and use the original file extensions. In case there are multiple reports, indices are added
     * according to the further properties of this class.
     */
    val namePrefix: String,

    /**
     * The index to be used for the first report generated by a reporter. If there are multiple reports, their names
     * are derived from [namePrefix] plus a sequential index starting with this value, such as "report-1.html",
     * "report-2.html", etc.
     */
    val startIndex: Int = 1,

    /**
     * A flag that determines whether the name of reports should always contain an index. The flag has only an effect
     * if there is only a single report. If it is *false*, the report is named by the [namePrefix] plus the original
     * file extension. If it is *true*, the [startIndex] is appended to the [namePrefix].
     */
    val alwaysAppendIndex: Boolean = false
)

@Serializable
data class ReporterJobConfiguration(
    /**
     * The path to the copyright garbage file which is resolved from the configured configuration source. If this is
     * `null`, the default path from ORT will be used.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.copyrightGarbageFile] is used to ensure
     * consistency.**
     */
    val copyrightGarbageFile: String? = null,

    /**
     * The report formats to generate.
     */
    val formats: List<String> = emptyList(),

    /**
     * The path to the how-to-fix Kotlin script which is resolved from the configuration source. This Kotlin script
     * will be used to instantiate an instance of HowToFixTextProvider, which injects how-to-fix texts for ORT issues.
     * If this is `null`, the default path from ORT will be used.
     */
    val howToFixTextProviderFile: String? = null,

    /**
     * The path to the license classifications file which is resolved from the configured configuration source. If this
     * is `null`, the default path from ORT will be used.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.licenseClassificationsFile] is used to
     * ensure consistency.**
     */
    val licenseClassificationsFile: String? = null,

    /**
     * The list of package configuration providers to use.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.packageConfigurationProviders] is used to
     * ensure consistency.**
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * The path to the resolutions file which is resolved from the configured configuration source. If this is `null`,
     * the default path from ORT will be used.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.resolutionsFile] is used to ensure
     * consistency.**
     */
    val resolutionsFile: String? = null,

    /**
     * An optional path to a configuration directory containing custom license texts. If defined, all files from this
     * directory are downloaded and made available via a `DefaultLicenseTextProvider` instance.
     */
    val customLicenseTextDir: String? = null,

    /**
     * A list with [ReporterAsset]s pointing to files that must be downloaded before the generation of reports is
     * started.
     */
    val assetFiles: List<ReporterAsset> = emptyList(),

    /**
     * A list with [ReporterAsset]s pointing to directories that must be downloaded before the generation of reports
     * is started. This is analogous to [assetFiles], but all the files contained in the specified directory are
     * downloaded.
     */
    val assetDirectories: List<ReporterAsset> = emptyList(),

    /**
     * A map of configuration options that are specific to a concrete reporter.
     */
    val config: Map<String, PluginConfiguration>? = null,

    /**
     * A map allowing to assign [ReportNameMapping] instances to single reporters. This can be used to override the
     * default names generated by reporters.
     */
    val nameMappings: Map<String, ReportNameMapping>? = null
)

@Serializable
data class NotifierJobConfiguration(
    /**
     * The notifier script to use. If this is `null`, the configured default notification will be used.
     */
    val notifierRules: String? = null,

    /**
     * The path to the resolutions file which is resolved from the configured configuration source. If this is `null`,
     * the default path from ORT will be used.
     */
    val resolutionsFile: String? = null,

    /**
     * The configuration for Email notifications. Is this is `null`, no email notifications will be sent.
     */
    val mail: MailNotificationConfiguration? = null,

    /**
     * The configuration for Jira notifications. Is this is `null`, no Jira notifications will be sent.
     */
    val jira: JiraNotificationConfiguration? = null
)

@Serializable
/**
 * The strategy to use for fetching submodules.
 */
enum class SubmoduleFetchStrategy {
    /**
     * Don't fetch submodules at all.
     */
    DISABLED,

    /**
     * Only fetch the top level of submodules.
     */
    TOP_LEVEL_ONLY,

    /**
     * Fetch all nested submodules recursively.
     */
    FULLY_RECURSIVE
}
