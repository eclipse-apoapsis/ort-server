/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import java.io.File

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.ResolvedPackageCurations
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.readValueOrNull
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.SimplePackageCurationProvider
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.workers.common.mapToOrt
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerRunner::class.java)

class AnalyzerRunner {
    companion object {
        /** The name of the file from which the Analyzer job configuration is read. */
        private const val ANALYZER_CONFIG_FILE = "analyzer-config.json"

        /** The name of the file to which the Analyzer result is written. */
        private const val ANALYZER_RESULT_FILE = "analyzer-result.yml"

        /** The name of the file to which an error is written in case the run fails. */
        private const val ANALYZER_ERROR_FILE = "analyzer-error.txt"

        /**
         * An alternative function for calling the [AnalyzerRunner]. This function is used when the JVM needs to be
         * forked in order to make newly set environment variables effective. In this case, parameters are passed via
         * command line arguments. The first argument is a temporary directory to be used for exchanging data between
         * the parent and the forked process. Here, the serialized [AnalyzerJobConfiguration] is expected, and the
         * resulting [OrtResult] will be stored in this directory, too. The second argument is the path to the project
         * to be analyzed.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            logger.info("Executing forked AnalyzerRunner with arguments: ${args.joinToString()}")

            val exchangeDir = File(args[0])

            runCatching {
                val projectDir = File(args[1])
                val configFile = exchangeDir.resolve(ANALYZER_CONFIG_FILE)
                val resultFile = exchangeDir.resolve(ANALYZER_RESULT_FILE)

                val config = configFile.readValue<AnalyzerJobConfiguration>()
                val runner = AnalyzerRunner()
                val result = runner.run(projectDir, config)

                resultFile.writeValue(result)
            }.onFailure { exception ->
                logger.error("Analyzer run failed.", exception)
                exchangeDir.resolve(ANALYZER_ERROR_FILE).writeText(exception.toString())
            }
        }
    }

    fun run(inputDir: File, config: AnalyzerJobConfiguration): OrtResult {
        val ortPackageManagerOptions =
            config.packageManagerOptions?.map { entry -> entry.key to entry.value.mapToOrt() }?.toMap()

        val analyzerConfigFromJob = AnalyzerConfiguration(
            config.allowDynamicVersions,
            config.enabledPackageManagers,
            config.disabledPackageManagers,
            ortPackageManagerOptions,
            config.skipExcluded ?: false
        )

        val repositoryConfiguration = inputDir.resolve(ORT_REPO_CONFIG_FILENAME).takeIf { it.isFile }?.readValueOrNull()
            ?: RepositoryConfiguration()

        val analyzerConfig = repositoryConfiguration.analyzer?.let { analyzerConfigFromJob.merge(it) }
            ?: analyzerConfigFromJob

        val analyzer = Analyzer(analyzerConfig)

        val enabledPackageManagers = analyzerConfig.determineEnabledPackageManagers()
        val info = analyzer.findManagedFiles(inputDir, enabledPackageManagers, repositoryConfiguration)
        if (info.managedFiles.isEmpty()) {
            logger.warn("No definition files found.")
        } else {
            val filesPerManager = info.managedFiles.mapKeysTo(sortedMapOf()) { it.key.managerName }
            var count = 0

            filesPerManager.forEach { (manager, files) ->
                count += files.size
                logger.info("Found ${files.size} $manager definition file(s) at:")

                files.forEach { file ->
                    val relativePath = file.toRelativeString(inputDir).takeIf { it.isNotEmpty() } ?: "."
                    logger.info("\t$relativePath")
                }
            }

            logger.info("Found $count definition file(s) from ${filesPerManager.size} package manager(s) in total.")
        }

        logger.info("Creating package curation providers...")

        val packageCurationProviders = buildList {
            add(
                ResolvedPackageCurations.REPOSITORY_CONFIGURATION_PROVIDER_ID to SimplePackageCurationProvider(
                    repositoryConfiguration.curations.packages
                )
            )

            val packageCurationProviderConfigs = config.packageCurationProviders.map { it.mapToOrt() }
            addAll(PackageCurationProviderFactory.create(packageCurationProviderConfigs))
        }

        logger.info("Starting analysis of definition file(s)...")

        val ortResult = analyzer.analyze(info, packageCurationProviders)

        val projectCount = ortResult.getProjects().size
        val packageCount = ortResult.getPackages().size
        logger.info(
            "Found $projectCount project(s) and $packageCount package(s) in total (not counting excluded ones)."
        )

        val curationCount = ortResult.getPackages().sumOf { it.curations.size }
        logger.info("Applied $curationCount curation(s) from 1 provider.")

        check(ortResult.analyzer?.result != null) {
            "There was an error creating the analyzer result."
        }

        return ortResult
    }
}
