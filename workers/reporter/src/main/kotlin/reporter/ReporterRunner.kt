/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.reporter

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.licenses.DefaultLicenseInfoProvider
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.CompositePackageConfigurationProvider
import org.ossreviewtoolkit.model.utils.ConfigurationResolver
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.FileArchiver
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.SimplePackageConfigurationProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.ReporterAsset
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.workers.common.JobOptions
import org.ossreviewtoolkit.server.workers.common.OptionsTransformerFactory
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
import org.ossreviewtoolkit.server.workers.common.mapToOrt
import org.ossreviewtoolkit.server.workers.common.readConfigFileWithDefault
import org.ossreviewtoolkit.server.workers.common.resolvedConfigurationContext
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.showStackTrace

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ReporterRunner::class.java)

class ReporterRunner(
    /** The object to store the generated report files. */
    private val reportStorage: ReportStorage,

    /** The factory for creating a worker context. */
    private val contextFactory: WorkerContextFactory,

    /** The factory for creating a transformer for options. */
    private val transformerFactory: OptionsTransformerFactory,

    /** The config manager used to download configuration files. */
    private val configManager: ConfigManager,

    /** The file archiver used for resolving license files. */
    private val fileArchiver: FileArchiver
) {
    fun run(
        runId: Long,
        ortResult: OrtResult,
        config: ReporterJobConfiguration,
        evaluatorConfig: EvaluatorJobConfiguration?
    ): ReporterRunnerResult {
        val reporters = config.formats.map { format ->
            requireNotNull(Reporter.ALL[format]) {
                "No reporter found for the configured format '$format'."
            }
        }

        val outputDir = createOrtTempDir("reporter-worker")

        val copyrightGarbageFile =
            if (evaluatorConfig != null) evaluatorConfig.copyrightGarbageFile else config.copyrightGarbageFile
        val copyrightGarbage = configManager.readConfigFileWithDefault(
            path = copyrightGarbageFile,
            defaultPath = ORT_COPYRIGHT_GARBAGE_FILENAME,
            fallbackValue = CopyrightGarbage(),
            context = null
        )

        val licenseClassificationsFile = if (evaluatorConfig != null) {
            evaluatorConfig.licenseClassificationsFile
        } else {
            config.licenseClassificationsFile
        }
        val licenseClassifications = configManager.readConfigFileWithDefault(
            path = licenseClassificationsFile,
            defaultPath = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
            fallbackValue = LicenseClassifications(),
            context = null
        )

        val packageConfigurationProvider = buildList {
            if (evaluatorConfig != null) {
                // Use only the resolved package configurations if they were already resolved by the evaluator.
                val resolvedPackageConfigurations = ortResult.resolvedConfiguration.packageConfigurations.orEmpty()
                add(SimplePackageConfigurationProvider(resolvedPackageConfigurations))
            } else {
                // Resolve package configurations from the configured providers.
                val repositoryPackageConfigurations = ortResult.repository.config.packageConfigurations
                add(SimplePackageConfigurationProvider(repositoryPackageConfigurations))

                val packageConfigurationProviderConfigs = config.packageConfigurationProviders.map { it.mapToOrt() }
                addAll(
                    PackageConfigurationProviderFactory.create(packageConfigurationProviderConfigs).map { it.second }
                )
            }
        }.let { CompositePackageConfigurationProvider(it) }

        val resolutionProvider = if (evaluatorConfig != null) {
            // Use only the resolved resolutions if they were already resolved by the evaluator.
            DefaultResolutionProvider(ortResult.resolvedConfiguration.resolutions.orEmpty())
        } else {
            // Resolve resolutions from the repository configuration and resolutions file.
            val resolutionsFromOrtResult = ortResult.getResolutions()

            val resolutionsFromFile = configManager.readConfigFileWithDefault(
                path = config.resolutionsFile,
                defaultPath = ORT_RESOLUTIONS_FILENAME,
                fallbackValue = Resolutions(),
                context = null
            )

            DefaultResolutionProvider(resolutionsFromOrtResult.merge(resolutionsFromFile))
        }

        // TODO: The ReporterInput object is created only with the passed ortResult and rest of the parameters are
        //       default values. This should be changed as soon as other parameters can be configured in the
        //       reporter worker.
        val reporterInput = ReporterInput(
            ortResult = ortResult,
            licenseInfoResolver = LicenseInfoResolver(
                provider = DefaultLicenseInfoProvider(ortResult, packageConfigurationProvider),
                copyrightGarbage = copyrightGarbage,
                addAuthorsToCopyrights = true,
                archiver = fileArchiver,
                licenseFilePatterns = LicenseFilePatterns.DEFAULT
            ),
            copyrightGarbage = copyrightGarbage,
            licenseClassifications = licenseClassifications,
            packageConfigurationProvider = packageConfigurationProvider,
            resolutionProvider = resolutionProvider
        )

        val results = runBlocking(Dispatchers.IO) {
            contextFactory.createContext(runId).use { context ->
                val transformedOptions = downloadInputFiles(context, config)

                reporters.map { reporter ->
                    async {
                        logger.info("Generating the '${reporter.type}' report...")
                        reporter to runCatching {
                            val reporterOptions = transformedOptions[reporter.type].orEmpty()
                            reporter.generateReport(reporterInput, outputDir, reporterOptions).also {
                                reportStorage.storeReportFiles(runId, it)
                            }
                        }
                    }
                }.awaitAll()
            }
        }.partition { it.second.isSuccess }

        require(results.second.isEmpty()) {
            val failures = results.second.associate { (reporter, failure) ->
                reporter.type to failure.exceptionOrNull()!!
            }

            failures.forEach { (reporter, e) ->
                e.showStackTrace()

                logger.error("Could not create report for '$reporter' due to '${e.javaClass.name}'.")
            }

            "There was an error creating the report(s) for ${
                failures.entries
                    .joinToString(separator = "\n", prefix = "\n") {
                        "${it.key}: ${it.value.javaClass.name} = ${it.value.message}"
                    }
            }"
        }

        val reports = results.first.associate {
            logger.info("Successfully created '${it.first.type}' report.")
            it.first.type to it.second.getOrDefault(emptyList())
        }

        val packageConfigurations = if (evaluatorConfig != null) {
            null
        } else {
            ConfigurationResolver.resolvePackageConfigurations(
                identifiers = ortResult.getUncuratedPackages().mapTo(mutableSetOf()) { it.id },
                scanResultProvider = { id -> ortResult.getScanResultsForId(id) },
                packageConfigurationProvider = packageConfigurationProvider
            )
        }

        val resolutions = if (evaluatorConfig != null) {
            null
        } else {
            ConfigurationResolver.resolveResolutions(
                issues = ortResult.getIssues().values.flatten(),
                ruleViolations = ortResult.getRuleViolations(),
                vulnerabilities = ortResult.getVulnerabilities().values.flatten(),
                resolutionProvider = resolutionProvider
            )
        }

        return ReporterRunnerResult(reports, packageConfigurations, resolutions)
    }

    /**
     * Handle the download of all files that need to be present for the reporters to execute as specified in the given
     * [config]. This includes template files and other assets like fonts or images. Use the given [context] for the
     * download.
     */
    private suspend fun downloadInputFiles(
        context: WorkerContext,
        config: ReporterJobConfiguration
    ): JobOptions = withContext(Dispatchers.IO) {
        val templateDir = context.createTempDir()
        val transformedOptions = async {
            transformerFactory.newTransformer(config.options.orEmpty())
                .filter { it.contains(ReporterComponent.TEMPLATE_REFERENCE) }
                .transform { context.downloadReporterTemplates(it, templateDir) }
        }

        launch { context.downloadAssetFiles(config.assetFiles, templateDir) }
        launch { context.downloadAssetDirectories(config.assetDirectories, templateDir) }

        transformedOptions.await()
    }
}

data class ReporterRunnerResult(
    val reports: Map<String, List<File>>,
    val resolvedPackageConfigurations: List<PackageConfiguration>?,
    val resolvedResolutions: Resolutions?
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
        val content = configManager.listFiles(resolvedConfigurationContext, Path(asset.sourcePath))
        val targetDir = createAssetDirectory(asset, directory)

        logger.info("Downloading asset directory '{}' to '{}'.", asset.sourcePath, targetDir)
        logger.debug("The directory contains these files: {}}.", content)

        downloadConfigurationFiles(content, targetDir)
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
