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
    val parameters: Options = emptyMap(),

    /**
     * The name of the rule set to be used during the run. The rule set defines a number of files that are required by
     * different worker jobs. Therefore, this is a top-level property. If this property is unspecified, the default
     * rule set configured for this ORT Server instance is used.
     */
    val ruleSet: String? = null
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
     * An additional [EnvironmentConfig] to be used for this run. If this configuration is defined, it is merged with
     * the configuration defined in the repository (if any).
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
    val skipExcluded: Boolean? = null,

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false
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
    val config: Map<String, PluginConfig>? = null,

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false
)

/**
 * The configuration for a scanner job.
 */
@Serializable
data class ScannerJobConfiguration(
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
     * A map of plugin configurations that are specific to a concrete scanner.
     */
    val config: Map<String, PluginConfig>? = null,

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false,

    /**
     * Specifies how submodules are fetched when resolving provenances. Currently supported only for Git repositories.
     * If set to [SubmoduleFetchStrategy.FULLY_RECURSIVE] (default), all submodules are fetched recursively. If set
     * to [SubmoduleFetchStrategy.TOP_LEVEL_ONLY], only the top-level submodules are fetched.
     */
    val submoduleFetchStrategy: SubmoduleFetchStrategy = SubmoduleFetchStrategy.FULLY_RECURSIVE
)

/**
 * The configuration for an evaluator job.
 */
@Serializable
data class EvaluatorJobConfiguration(
    /**
     * The list of package configuration providers to use.
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false
)

@Serializable
data class ReporterJobConfiguration(
    /**
     * The report formats to generate.
     */
    val formats: List<String> = emptyList(),

    /**
     * A list with the names of pre-configured groups of asset files that need to be downloaded before the report
     * generation starts. This can be used for instance to select templates or theming files dynamically.
     */
    val assetFilesGroups: List<String> = emptyList(),

    /**
     * A list with the name of pre-configured groups of asset directories that need to be downloaded before the report
     * generation starts. This is analogous to [assetFilesGroups], but the contents of the selected directories are
     * downloaded completely.
     */
    val assetDirectoriesGroups: List<String> = emptyList(),

    /**
     * The list of package configuration providers to use.
     *
     * **This value is only used if no [evaluator job][EvaluatorJobConfiguration] is configured, otherwise the
     * [value from the evaluator job configuration][EvaluatorJobConfiguration.packageConfigurationProviders] is used to
     * ensure consistency.**
     */
    val packageConfigurationProviders: List<ProviderPluginConfiguration> = emptyList(),

    /**
     * A map of configuration options that are specific to a concrete reporter.
     */
    val config: Map<String, PluginConfig>? = null,

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false
)

@Serializable
data class NotifierJobConfiguration(
    /**
     * The list of email addresses to which notifications should be sent.
     */
    val recipientAddresses: List<String> = emptyList(),

    /**
     * Keep the worker alive after it has finished. This is useful for manual problem analysis directly
     * within the pod's execution environment.
     */
    val keepAliveWorker: Boolean = false
)

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
