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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

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

    /**
     * A map with custom parameters for the whole ORT run. These parameters are subject for validation performed by a
     * validation script, which can then map modify the `CreateOrtRun` request based on the provided parameters.
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
     * (transitive) dependencies are declared using version ranges and a new version of such a dependency was
     * published in the meantime. If set to false, analysis of projects that use version ranges will fail. Defaults to
     * false.
     */
    val allowDynamicVersions: Boolean = false,

    /**
     * A list of the case-insensitive names of package managers that are enabled. Disabling a package manager in
     * [disabledPackageManagers] overrides enabling it here.
     */
    val disabledPackageManagers: List<String>? = null,

    /**
     * A list of the case-insensitive names of package managers that are disabled. Disabling a package manager in this
     * list overrides [enabledPackageManagers].
     */
    val enabledPackageManagers: List<String>? = null,

    /**
     * An optional [EnvironmentConfig] to be used for this run. If this configuration is defined, it replaces the
     * configuration defined in the repository (if any).
     */
    val environmentConfig: EnvironmentConfig? = null,

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
     * The Advisors to use (e.g. NexusIQ, VulnerableCode, DefectDB).
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
     * Create archives for packages that have a stored scan result but no license archive yet.
     */
    val createMissingArchives: Boolean? = null,

    /**
     * Mappings from licenses returned by the scanner to valid SPDX licenses. Note that these mappings are only applied
     * in new scans, stored scan results are not affected.
     */
    val detectedLicenseMappings: Options? = null,

    /**
     * A list of glob expressions that match file paths which are to be excluded from scan results.
     */
    val ignorePatterns: List<String>? = emptyList(),

    /**
     * The list of the names of the scanners to use to scan projects. If this is null, projects are scanned with the
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
     * null, the default path from ORT will be used.
     */
    val copyrightGarbageFile: String? = null,

    /**
     * The path to the license classifications file which is resolved from the configured configuration source. If this
     * is null, the default path from ORT will be used.
     */
    val licenseClassificationsFile: String? = null,

    /**
     * The list of package configuration providers to use.
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * The path to the resolutions file which is resolved from the configured configuration source. If this is null,
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
     * property, such a relative path can be specified. If it is *null*, the root folder of the reporter worker
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
     * A name prefix for constructing report names. If a reporter returns a single result file, the file is given this
     * name plus the original file extension. In case multiple result files are returned, the names are made unique by
     * appending an index, such as _report-1.html_, _report-2.html_, etc.
     */
    val namePrefix: String
)

/**
 * The configuration for a reporter job.
 */
@Serializable
data class ReporterJobConfiguration(
    /**
     * The path to the copyright garbage file which is resolved from the configured configuration source. If this is
     * null, the default path from ORT will be used.
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
     * The path to the license classifications file which is resolved from the configured configuration source. If this
     * is null, the default path from ORT will be used.
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
     * The path to the resolutions file which is resolved from the configured configuration source. If this is null,
     * the default path from ORT will be used.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.resolutionsFile] is used to ensure
     * consistency.**
     */
    val resolutionsFile: String? = null,

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
