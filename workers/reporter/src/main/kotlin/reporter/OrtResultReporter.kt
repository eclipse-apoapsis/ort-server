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
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.packZip

data class OrtResultReporterConfig(
    /**
     * Whether to compress the generated file. If set to `true` (which is the default), a Zip archive is generated which
     * contains the ORT result file.
     */
    @OrtPluginOption(defaultValue = "true")
    val compressed: Boolean
)

@OrtPlugin(
    id = "OrtResult",
    displayName = "ORT Result Reporter",
    description = "A reporter that creates an ORT result YAML file for the ORT run.",
    factory = ReporterFactory::class
)
class OrtResultReporter(
    override val descriptor: PluginDescriptor = OrtResultReporterFactory.descriptor,
    private val config: OrtResultReporterConfig
) : Reporter {
    companion object {
        /**
         * Name of the property that enables compression of the generated file. If set to *true* (which is the
         * default), a Zip archive is generated which contains the ORT result file.
         */
        const val COMPRESSED_PROPERTY = "compressed"

        /** The name of the file with the result. */
        private const val RESULT_FILE_NAME = "ort-result.yml"

        /** The name of the archive file containing the compressed result. */
        private const val ARCHIVE_FILE_NAME = "ort-result.zip"
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFile = runCatching {
            val targetDir = outputDir.resolve("ort-result").apply { mkdir() }
            val outputFile = targetDir.resolve(RESULT_FILE_NAME).apply { writeValue(input.ortResult) }

            if (config.compressed) {
                val archiveFile = outputDir.resolve(ARCHIVE_FILE_NAME)
                targetDir.packZip(archiveFile)
                archiveFile
            } else {
                outputFile
            }
        }

        return listOf(reportFile)
    }
}
