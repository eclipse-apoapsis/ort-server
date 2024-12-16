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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.ReporterAsset
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.mapOptions
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileValueWithDefault
import org.eclipse.apoapsis.ortserver.workers.common.readConfigFileWithDefault
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
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.reporter.DefaultLicenseTextProvider
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.LicenseTextProvider
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

    /** The factory for creating a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The config manager used to download configuration files. */
    private val configManager: ConfigManager,

    /** The file archiver used for resolving license files. */
    private val fileArchiver: FileArchiver
) {
    suspend fun run(
        runId: Long,
        ortResult: OrtResult,
        config: ReporterJobConfiguration,
        evaluatorConfig: EvaluatorJobConfiguration?
    ): ReporterRunnerResult {
        return contextFactory.createContext(runId).use { context ->
            val copyrightGarbageFile =
                if (evaluatorConfig != null) evaluatorConfig.copyrightGarbageFile else config.copyrightGarbageFile
            val copyrightGarbage = configManager.readConfigFileValueWithDefault(
                path = copyrightGarbageFile,
                defaultPath = ORT_COPYRIGHT_GARBAGE_FILENAME,
                fallbackValue = CopyrightGarbage(),
                context = context.resolvedConfigurationContext
            )

            val licenseClassificationsFile = if (evaluatorConfig != null) {
                evaluatorConfig.licenseClassificationsFile
            } else {
                config.licenseClassificationsFile
            }
            val licenseClassifications = configManager.readConfigFileValueWithDefault(
                path = licenseClassificationsFile,
                defaultPath = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
                fallbackValue = LicenseClassifications(),
                context = context.resolvedConfigurationContext
            )

            var resolvedOrtResult = ortResult

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

                val resolutionsFromFile = configManager.readConfigFileValueWithDefault(
                    path = config.resolutionsFile,
                    defaultPath = ORT_RESOLUTIONS_FILENAME,
                    fallbackValue = Resolutions(),
                    context = context.resolvedConfigurationContext
                )

                val resolutionProvider = DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))

                resolvedOrtResult = resolvedOrtResult.setResolutions(resolutionProvider)
            }

            val howToFixTextProviderScript = configManager.readConfigFileWithDefault(
                path = config.howToFixTextProviderFile,
                defaultPath = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
                fallbackValue = "",
                context = context.resolvedConfigurationContext
            )

            val howToFixTextProvider = if (howToFixTextProviderScript.isNotEmpty()) {
                HowToFixTextProvider.fromKotlinScript(howToFixTextProviderScript, resolvedOrtResult)
            } else {
                HowToFixTextProvider.NONE
            }

            val outputDir = context.createTempDir()
            val issues = mutableListOf<Issue>()

            val successes = withContext(Dispatchers.IO) {
                val deferredTransformedOptions = async { processReporterOptions(context, config) }

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
                        licenseTextProvider = createLicenseTextProvider(context, config),
                        howToFixTextProvider = howToFixTextProvider
                    )
                }

                val reporterInput = deferredReporterInput.await()
                val transformedOptions = deferredTransformedOptions.await()

                config.formats.map { format ->
                    async {
                        logger.info("Generating the '$format' report...")

                        val result = runCatching {
                            val reporterFactory = requireNotNull(ReporterFactory.ALL[format]) {
                                "No reporter found for the configured format '$format'."
                            }

                            val reporterConfig = transformedOptions[reporterFactory.descriptor.id]?.let { options ->
                                PluginConfig(options.options, options.secrets)
                            } ?: PluginConfig()

                            val reporter = reporterFactory.create(reporterConfig)
                            val reportFileResults = reporter.generateReport(reporterInput, outputDir)

                            val reportFiles = reportFileResults.mapNotNull { result ->
                                result.getOrElse {
                                    issues += createAndLogReporterIssue(format, it)
                                    null
                                }
                            }

                            reportFiles.takeUnless { it.isEmpty() }?.let { reporter to reportFiles }
                        }.onFailure {
                            issues += createAndLogReporterIssue(format, it)
                        }

                        result.getOrNull()?.let { (reporter, reportFiles) ->
                            val nameMapper = ReportNameMapper.create(config, reporter.descriptor.id)
                            format to nameMapper.mapReportNames(reportFiles)
                                .also { reportStorage.storeReportFiles(runId, it) }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            val reports = successes.associate { (name, report) ->
                logger.info("Successfully created '$name' report.")
                name to report.keys.toList()
            }

            // Only return the package configurations and resolutions if they were not already resolved by the
            // evaluator.
            ReporterRunnerResult(
                reports,
                resolvedOrtResult.resolvedConfiguration.packageConfigurations.takeIf { evaluatorConfig == null },
                resolvedOrtResult.resolvedConfiguration.resolutions.takeIf { evaluatorConfig == null },
                issues = issues
            )
        }
    }

    /**
     * Prepare the generation of reports by processing the options passed to the single reporters in the given
     * [config]. This includes downloading of all files that are referenced by reporters, such as template files and
     * other assets like fonts or images. Also, the secrets required by reporters need to be resolved. Use the given
     * [context] for the processing.
     */
    private suspend fun processReporterOptions(
        context: WorkerContext,
        config: ReporterJobConfiguration
    ): Map<String, PluginConfiguration> = withContext(Dispatchers.IO) {
        val templateDir = context.createTempDir()

        launch { context.downloadAssetFiles(config.assetFiles, templateDir) }
        launch { context.downloadAssetDirectories(config.assetDirectories, templateDir) }

        // Replace the placeholder for the working directory in the options with the actual path.
        val workDirOptions = config.config?.mapOptions { (_, value) ->
            if (value.contains(ReporterComponent.WORK_DIR_PLACEHOLDER)) {
                value.replace(ReporterComponent.WORK_DIR_PLACEHOLDER, templateDir.absolutePath)
            } else {
                value
            }
        }.orEmpty()

        // Get all template file references from the plugin options.
        val templateReferences = workDirOptions.flatMap { (_, pluginConfig) ->
            pluginConfig.options.values.filter { it.contains(ReporterComponent.TEMPLATE_REFERENCE) }
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
    val reports: Map<String, List<String>>,
    val resolvedPackageConfigurations: List<PackageConfiguration>?,
    val resolvedResolutions: Resolutions?,
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
 * Return the provider for license texts based on the given [config]. If a path to custom license texts is configured,
 * create a [CustomLicenseTextProvider] that downloads license texts from this directory on demand. Otherwise, return
 * a [DefaultLicenseTextProvider] that can only handle standard license texts.
 */
internal fun createLicenseTextProvider(
    context: WorkerContext,
    config: ReporterJobConfiguration
): LicenseTextProvider =
    config.customLicenseTextDir?.let { dir ->
        CustomLicenseTextProvider(context.configManager, context.resolvedConfigurationContext, Path(dir))
    } ?: DefaultLicenseTextProvider()

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
        severity = Severity.ERROR,
    )
}
