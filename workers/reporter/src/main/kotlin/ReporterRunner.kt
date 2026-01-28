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

package org.eclipse.apoapsis.ortserver.workers.reporter

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.config.ReporterAsset
import org.eclipse.apoapsis.ortserver.services.config.ReporterConfig
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.mapOptions
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.resolveResolutionsWithMappings
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.plugins.api.orEmpty
import org.ossreviewtoolkit.plugins.licensefactproviders.api.CompositeLicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.scancode.ScanCodeLicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.config.setPackageConfigurations
import org.ossreviewtoolkit.utils.config.setResolutions
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterRunner::class.java)

class ReporterRunner(
    /** The object to store the generated report files. */
    private val reportStorage: ReportStorage,

    /** The file archiver used for resolving license files. */
    private val fileArchiver: FileArchiver,

    /**
     * The service to access the admin configuration. Some parts of the report configuration are obtained from there.
     */
    private val adminConfigService: AdminConfigService
) {
    suspend fun run(
        ortResult: OrtResult,
        config: ReporterJobConfiguration,
        evaluatorConfig: EvaluatorJobConfiguration?,
        context: WorkerContext
    ): ReporterRunnerResult {
        val adminConfig = adminConfigService.loadAdminConfig(
            context.resolvedConfigurationContext,
            context.ortRun.organizationId
        )
        val ruleSet = adminConfig.getRuleSet(context.ortRun.resolvedJobConfigs?.ruleSet)
        val copyrightGarbage = context.configManager.readConfigFileValueWithDefault(
            path = ruleSet.copyrightGarbageFile,
            defaultPath = ORT_COPYRIGHT_GARBAGE_FILENAME,
            fallbackValue = CopyrightGarbage(),
            context = context.resolvedConfigurationContext
        )

        val licenseClassifications = context.configManager.readConfigFileValueWithDefault(
            path = ruleSet.licenseClassificationsFile,
            defaultPath = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
            fallbackValue = LicenseClassifications(),
            context = context.resolvedConfigurationContext
        )

        var resolvedOrtResult = ortResult
        var resolvedItems: ResolvedItemsResult? = null

        if (evaluatorConfig == null) {
            // Resolve package configurations if not already done by the evaluator.
            val packageConfigurationProvider = buildList {
                val repositoryPackageConfigurations = resolvedOrtResult.repository.config.packageConfigurations
                add(SimplePackageConfigurationProvider(configurations = repositoryPackageConfigurations))

                val packageConfigurationProviderConfigs = context
                    .resolveProviderPluginConfigSecrets(config.packageConfigurationProviders)
                    .map { it.mapToOrt() }

                addAll(
                    PackageConfigurationProviderFactory.create(packageConfigurationProviderConfigs)
                        .map { it.second }
                )
            }.let { CompositePackageConfigurationProvider(it) }

            resolvedOrtResult = resolvedOrtResult.setPackageConfigurations(packageConfigurationProvider)

            // Resolve resolutions if not already done by the evaluator.
            val resolutionsFromOrtResult = resolvedOrtResult.repository.config.resolutions

            val resolutionsFromFile = context.configManager.readConfigFileValueWithDefault(
                path = ruleSet.resolutionsFile,
                defaultPath = ORT_RESOLUTIONS_FILENAME,
                fallbackValue = Resolutions(),
                context = context.resolvedConfigurationContext
            )

            val resolutionProvider = DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))

            resolvedOrtResult = resolvedOrtResult.setResolutions(resolutionProvider)

            // Compute resolved items mappings when evaluator didn't run.
            resolvedItems = resolveResolutionsWithMappings(
                issues = resolvedOrtResult.getIssues().values.flatten(),
                ruleViolations = emptyList(), // No evaluator = no rule violations
                vulnerabilities = resolvedOrtResult.getVulnerabilities().values.flatten(),
                resolutionProvider = resolutionProvider
            )
        }

        val howToFixTextProviderScript = context.configManager.readConfigFileWithDefault(
            path = adminConfig.reporterConfig.howToFixTextProviderFile,
            defaultPath = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
            fallbackValue = "",
            context = context.resolvedConfigurationContext
        )

        val howToFixTextProvider = if (howToFixTextProviderScript.isNotEmpty()) {
            HowToFixTextProvider.fromKotlinScript(howToFixTextProviderScript, resolvedOrtResult)
        } else {
            HowToFixTextProvider.NONE
        }

        val (reportNames, issues) = generateReports(
            context,
            config,
            adminConfig.reporterConfig,
            resolvedOrtResult,
            copyrightGarbage,
            licenseClassifications,
            howToFixTextProvider
        )

        // Only return the package configurations and resolved items if they were not already resolved by
        // the evaluator.
        return ReporterRunnerResult(
            reportNames,
            resolvedOrtResult.resolvedConfiguration.packageConfigurations.takeIf { evaluatorConfig == null },
            resolvedItems,
            issues = issues
        )
    }

    /**
     * Generate all reports for the current [context] as defined by the given [config] and [adminConfig] using as input
     * the given [resolvedOrtResult], [copyrightGarbage], [licenseClassifications], and [howToFixTextProvider]. Return
     * a pair with a set of the names of reports that were created successfully and a list of issues that occurred
     * during the report generation.
     */
    private suspend fun generateReports(
        context: WorkerContext,
        config: ReporterJobConfiguration,
        adminConfig: ReporterConfig,
        resolvedOrtResult: OrtResult,
        copyrightGarbage: CopyrightGarbage,
        licenseClassifications: LicenseClassifications,
        howToFixTextProvider: HowToFixTextProvider
    ): Pair<Set<String>, List<Issue>> =
        withContext(Dispatchers.IO) {
            val outputDir = context.createTempDir()
            val issues = ConcurrentLinkedQueue<Issue>()

            val deferredTransformedOptions = async { processReporterOptions(context, config, adminConfig) }

            val deferredReporterInput = async {
                // TODO: Some parameters of ReporterInput are still set to default values. Make sure that for all
                //       corresponding configuration options exist and are used here.
                ReporterInput(
                    ortResult = resolvedOrtResult,
                    licenseInfoResolver = LicenseInfoResolver(
                        provider = DefaultLicenseInfoProvider(resolvedOrtResult),
                        copyrightGarbage = copyrightGarbage,
                        addAuthorsToCopyrights = true,
                        archiver = fileArchiver,
                        licenseFilePatterns = LicenseFilePatterns.DEFAULT
                    ),
                    copyrightGarbage = copyrightGarbage,
                    licenseClassifications = licenseClassifications,
                    licenseFactProvider = createLicenseFactProvider(context, adminConfig),
                    howToFixTextProvider = howToFixTextProvider
                )
            }

            val reporterInput = deferredReporterInput.await()
            val transformedOptions = deferredTransformedOptions.await()

            val activeReporters = ConcurrentHashMap.newKeySet<String>()
            val monitorJob = launch { logActiveReporters(activeReporters) }

            val success = config.formats.map { format ->
                async {
                    logger.info("Generating the '$format' report...")
                    activeReporters += format

                    val result = runCatching {
                        measureTimedValue {
                            val reporterFactory = fetchReporterFactory(format, adminConfig)

                            val reporterConfig = adminConfig.pluginOptionsForDefinition(
                                format,
                                transformedOptions
                            )?.mapToOrt().orEmpty()

                            val reporter = reporterFactory.create(reporterConfig)
                            val reportFileResults = reporter.generateReport(reporterInput, outputDir)

                            val reportFiles = reportFileResults.mapNotNull { result ->
                                result.getOrElse {
                                    issues += createAndLogReporterIssue(format, it)
                                    null
                                }
                            }

                            reportFiles.takeUnless { it.isEmpty() }?.let { reporter to reportFiles }
                        }.let {
                            logger.info("Successfully created '$format' report in ${it.duration}.")
                            it.value
                        }
                    }.onFailure {
                        issues += createAndLogReporterIssue(format, it)
                    }

                    result.getOrNull()?.let { (_, reportFiles) ->
                        val nameMapper = ReportNameMapper.create(
                            requireNotNull(adminConfig.getReportDefinition(format))
                        )

                        val namedReportFiles = nameMapper.mapReportNames(reportFiles)
                        reportStorage.storeReportFiles(context.ortRun.id, namedReportFiles)

                        namedReportFiles.keys
                    }.also {
                        activeReporters -= format
                    }
                }
            }.awaitAll().filterNotNull().flatMapTo(mutableSetOf()) { it }

            monitorJob.cancel()
            success to issues.toList()
        }

    /**
     * Periodically log the reporters that have not yet finished based on the given set of [activeReporters]. This is
     * useful to identify reporters taking unusually long to finish.
     */
    private suspend fun logActiveReporters(activeReporters: Set<String>) {
        while (true) {
            delay(30.seconds)
            logger.debug("Report generation in progress for the following reporters: {}.", activeReporters)
        }
    }

    /**
     * Prepare the generation of reports by processing the options passed to the single reporters in the given
     * [config], also taking the given [adminConfig] into account. This includes downloading of all files that are
     * referenced by reporters, such as template files and other assets like fonts or images. Also, the secrets
     * required by reporters need to be resolved. Use the given [context] for the processing.
     */
    private suspend fun processReporterOptions(
        context: WorkerContext,
        config: ReporterJobConfiguration,
        adminConfig: ReporterConfig
    ): Map<String, PluginConfig> = withContext(Dispatchers.IO) {
        val templateDir = context.createTempDir()
        val (assetFiles, assetDirectories) = fetchReporterAssets(config, adminConfig)

        launch { context.downloadAssetFiles(assetFiles, templateDir) }
        launch { context.downloadAssetDirectories(assetDirectories, templateDir) }

        // Replace the placeholder for the working directory in the options with the actual path.
        val workDirOptions = config.config?.mapOptions { (_, value) ->
            if (ReporterComponent.WORK_DIR_PLACEHOLDER in value) {
                value.replace(ReporterComponent.WORK_DIR_PLACEHOLDER, templateDir.absolutePath)
            } else {
                value
            }
        }.orEmpty()

        // Get all template file references from the plugin options.
        val templateReferences = workDirOptions.flatMap { (_, pluginConfig) ->
            pluginConfig.options.values.filter { ReporterComponent.TEMPLATE_REFERENCE in it }
        }

        // Download the referenced template files.
        val templateFiles = if (templateReferences.isNotEmpty()) {
            context.downloadReporterTemplates(templateReferences, templateDir)
        } else {
            emptyMap()
        }

        // Replace the template references in the options with the paths of the downloaded files.
        val processedOptions = workDirOptions.mapOptions { (_, value) ->
            templateFiles[value] ?: value
        }

        context.resolvePluginConfigSecrets(processedOptions)
    }
}

data class ReporterRunnerResult(
    val reports: Set<String>,
    val resolvedPackageConfigurations: List<PackageConfiguration>?,
    val resolvedItems: ResolvedItemsResult?,
    val issues: List<Issue> = emptyList()
)

/** Regular expression to split multiple template paths. */
private val regExSplitPaths = Regex("""\s*,\s*""")

/**
 * Download the reporter template files specified by the given [templates] collection to the given [directory]. Return
 * a [Map] pointing to the paths of the temporary files that have been downloaded. Each item in the given collection
 * can point to multiple template files using a comma as separator.
 */
private suspend fun WorkerContext.downloadReporterTemplates(
    templates: Collection<String>,
    directory: File
): Map<String, String> {
    val splitPaths = templates.associateWith { pathValue ->
        pathValue.split(regExSplitPaths)
    }

    val allPaths = splitPaths.values.flatten()
        .filter { it.startsWith(ReporterComponent.TEMPLATE_REFERENCE) }
        .mapTo(mutableSetOf()) { it.toTemplatePath() }
    logger.info("Downloading the following template files: {}.", allPaths)

    val downloadedPaths = downloadConfigurationFiles(allPaths, directory)

    return splitPaths.mapValues { entry ->
        entry.value.joinToString(separator = ",") {
            downloadedPaths[it.toTemplatePath()]?.absolutePath ?: it
        }
    }
}

/**
 * Download all the [ReporterAsset] files from the given [assets] collection to the specified [directory].
 * Relative paths are handled correctly.
 */
private suspend fun WorkerContext.downloadAssetFiles(assets: Collection<ReporterAsset>, directory: File) {
    assets.forEach { asset ->
        val targetDir = createAssetDirectory(asset, directory)

        logger.info("Downloading asset file '{}' to '{}'.", asset.sourcePath, targetDir)

        downloadConfigurationFile(Path(asset.sourcePath), targetDir, asset.targetName)
    }
}

/**
 * Download all the [ReporterAsset] directories from the given [assets] collection to the specified [directory].
 * For each directory, obtain the contained files and download them.
 */
private suspend fun WorkerContext.downloadAssetDirectories(assets: Collection<ReporterAsset>, directory: File) {
    assets.forEach { asset ->
        val targetDir = createAssetDirectory(asset, directory)
        downloadConfigurationDirectory(Path(asset.sourcePath), targetDir)
    }
}

/**
 * Create the directory in which to download an asset if necessary. Evaluate the target folder of the given [asset] for
 * this purpose. Use the provided [directory] as parent.
 */
private fun createAssetDirectory(asset: ReporterAsset, directory: File): File =
    asset.targetFolder?.let(directory::resolve)?.also { it.safeMkdirs() } ?: directory

/**
 * Transform this string to a [Path] for downloading a reporter template file. This requires removing the prefix
 * which marks this string as a template file.
 */
private fun String.toTemplatePath(): Path = Path(removePrefix(ReporterComponent.TEMPLATE_REFERENCE))

/**
 * Return the provider for license facts based on the given [config]. If a path to custom license texts is configured,
 * create a [CustomLicenseFactProvider] that downloads license texts from this directory on demand. Also add the default
 * license fact providers from ORT.
 */
internal fun createLicenseFactProvider(
    context: WorkerContext,
    config: ReporterConfig
): CompositeLicenseFactProvider =
    CompositeLicenseFactProvider(
        listOfNotNull(
            config.customLicenseTextDir?.let { dir ->
                CustomLicenseFactProvider(context.configManager, context.resolvedConfigurationContext, Path(dir))
            },
            SpdxLicenseFactProviderFactory.create(),
            ScanCodeLicenseFactProviderFactory.create()
        )
    )

/**
 * Create an issue for the given report [format] with information derived from the given [throwable][e] and also log it.
 */
private fun createAndLogReporterIssue(format: String, e: Throwable): Issue {
    e.showStackTrace()

    logger.error("Could not create report for '$format' due to '${e.javaClass.name}'.")

    return Issue(
        timestamp = Clock.System.now(),
        source = "Reporter",
        message = "Could not create report for '$format': '${e.message}'",
        severity = Severity.ERROR
    )
}

/**
 * Obtain all [ReporterAsset]s that must be downloaded based on the given [config] and [adminConfig]. Return a pair
 * with the asset files and asset directories referenced by the configuration.
 */
private fun fetchReporterAssets(
    config: ReporterJobConfiguration,
    adminConfig: ReporterConfig
): Pair<Collection<ReporterAsset>, Collection<ReporterAsset>> {
    val assetFiles = adminConfig.fetchGlobalAssets(config.assetFilesGroups)
    val assetDirectories = adminConfig.fetchGlobalAssets(config.assetDirectoriesGroups)

    config.formats.forEach { format ->
        adminConfig.getReportDefinition(format)?.also { definition ->
            assetFiles += definition.assetFiles
            assetDirectories += definition.assetDirectories
        }
    }

    return assetFiles to assetDirectories
}

/**
 * Return a [MutableSet] that contains all the [ReporterAsset]s contained in one of the global asset groups
 * referenced by the given [groupNames]. Unresolvable groups names are ignored; a validation should have taken
 * place already in an earlier step.
 */
private fun ReporterConfig.fetchGlobalAssets(groupNames: Collection<String>): MutableSet<ReporterAsset> =
    groupNames.flatMapTo(mutableSetOf()) { groupName ->
        globalAssets[groupName].orEmpty()
    }

/**
 * Obtain the [ReporterFactory] for the given [format] using the definitions from the given [adminConfig].
 */
private fun fetchReporterFactory(format: String, adminConfig: ReporterConfig): ReporterFactory {
    val pluginId = requireNotNull(adminConfig.getReportDefinition(format)?.pluginId) {
        "No reporter found for the configured format '$format'."
    }

    return requireNotNull(ReporterFactory.ALL[pluginId]) {
        "No reporter plugin found with the ID '$pluginId'."
    }
}
