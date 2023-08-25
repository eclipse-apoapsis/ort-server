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
import kotlinx.coroutines.runBlocking

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.workers.common.OptionsTransformerFactory
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.context.WorkerContextFactory
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
    private val transformerFactory: OptionsTransformerFactory
) {
    fun run(runId: Long, ortResult: OrtResult, config: ReporterJobConfiguration): Map<String, List<File>> {
        val reporters = config.formats.map { format ->
            requireNotNull(Reporter.ALL[format]) {
                "No reporter found for the configured format '$format'."
            }
        }

        val outputDir = createOrtTempDir("reporter-worker")

        // TODO: The ReporterInput object is created only with the passed ortResult and rest of the parameters are
        //       default values. This should be changed as soon as other parameters can be configured in the
        //       reporter worker.
        val reporterInput = ReporterInput(ortResult)

        val results = runBlocking(Dispatchers.IO) {
            contextFactory.createContext(runId).use { context ->
                val transformedOptions = transformerFactory.newTransformer(config.options.orEmpty())
                    .filter { it.contains(ReporterComponent.TEMPLATE_REFERENCE) }
                    .transform { context.downloadReporterTemplates(it) }

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

        return results.first.associate {
            logger.info("Successfully created '${it.first.type}' report.")
            it.first.type to it.second.getOrDefault(emptyList())
        }
    }
}

/** Regular expression to split multiple template paths. */
private val regExSplitPaths = Regex("""\s*,\s*""")

/**
 * Download the reporter template files specified by the given [templates] collection. Return a [Map] pointing to
 * the paths of the temporary files that have been downloaded. Each item in the given collection can point to multiple
 * template files using a comma as separator.
 */
private suspend fun WorkerContext.downloadReporterTemplates(templates: Collection<String>): Map<String, String> {
    val splitPaths = templates.associateWith { pathValue ->
        pathValue.split(regExSplitPaths)
    }

    val allPaths = splitPaths.values.flatten()
        .filter { it.startsWith(ReporterComponent.TEMPLATE_REFERENCE) }
        .mapTo(mutableSetOf()) { it.toTemplatePath() }
    logger.info("Downloading the following template files: {}.", allPaths)

    val downloadedPaths = downloadConfigurationFiles(allPaths)

    return splitPaths.mapValues { entry ->
        entry.value.joinToString(separator = ",") {
            downloadedPaths[it.toTemplatePath()]?.absolutePath ?: it
        }
    }
}

/**
 * Transform this string to a [Path] for downloading a reporter template file. This requires removing the prefix
 * which marks this string as a template file.
 */
private fun String.toTemplatePath(): Path = Path(removePrefix(ReporterComponent.TEMPLATE_REFERENCE))
