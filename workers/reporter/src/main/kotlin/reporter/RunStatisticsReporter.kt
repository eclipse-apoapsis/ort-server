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

import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * A custom reporter which exports the statistics of the run. Unlike ORT's EvaluatedModelReporter, which also exports
 * statistics data, this reporter does not include any other information to the produced reports, thus making them more
 * memory-efficient.
 */
@OrtPlugin(
    id = "RunStatistics",
    displayName = "Run Statistics Reporter",
    description = "A reporter that creates a JSON file with the statistics of the ORT run.",
    factory = ReporterFactory::class
)
class RunStatisticsReporter(
    override val descriptor: PluginDescriptor = RunStatisticsReporterFactory.descriptor
) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val outputFile = runCatching {
            outputDir.resolve("statistics.json").apply { writeValue(input.statistics) }
        }

        return listOf(outputFile)
    }
}
