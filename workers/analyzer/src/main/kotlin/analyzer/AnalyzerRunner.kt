/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagecurationproviders.ortconfig.OrtConfigPackageCurationProvider
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerRunner::class.java)

class AnalyzerRunner {
    fun run(inputDir: File, config: AnalyzerJobConfiguration): OrtResult {
        val analyzerConfig = AnalyzerConfiguration(config.allowDynamicVersions)
        val analyzer = Analyzer(analyzerConfig)

        // TODO: Add support for RepositoryConfiguration.
        val enabledPackageManagers = analyzerConfig.determineEnabledPackageManagers()
        val info = analyzer.findManagedFiles(inputDir, enabledPackageManagers, RepositoryConfiguration())
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

        // TODO: Add support for curation providers.
        val curationProvider = OrtConfigPackageCurationProvider()
        val ortResult = analyzer.analyze(info, listOf("OrtConfig" to curationProvider))

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
