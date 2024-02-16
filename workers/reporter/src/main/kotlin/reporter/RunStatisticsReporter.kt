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

package org.ossreviewtoolkit.server.workers.reporter

import java.io.File

import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * A custom reporter which exports the statistics of the run. Unlike ORT's EvaluatedModelReporter, which also exports
 * statistics data, this reporter does not include any other information to the produced reports thus making them more
 * memory-efficient.
 */
class RunStatisticsReporter : Reporter {
    override val type = "RunStatistics"

    override fun generateReport(input: ReporterInput, outputDir: File, config: PluginConfiguration): List<File> {
        val outputFile = outputDir.resolve("run-statistics.json")
        val statistics = input.statistics
        outputFile.writeValue(statistics)
        return listOf(outputFile)
    }
}
