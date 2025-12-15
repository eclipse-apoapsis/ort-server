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

import org.eclipse.apoapsis.ortserver.services.config.ReportDefinition
import org.eclipse.apoapsis.ortserver.services.config.ReportNameMapping

/**
 * An interface for renaming report files. An implementation of this interface is used by the reporter runner to
 * optionally override the default names generated for report files.
 */
interface ReportNameMapper {
    companion object {
        /**
         * A default [ReportNameMapper] instance that maps report files to their original names.
         */
        private val DEFAULT_MAPPER = object : ReportNameMapper {
            override fun mapReportNames(reportFiles: Collection<File>): Map<String, File> {
                return reportFiles.associateBy { it.name }
            }
        }

        /**
         * Return a [ReportNameMapper] instance to map the file names for the reporter described by the given
         * [reportDefinition].
         */
        fun create(reportDefinition: ReportDefinition): ReportNameMapper =
            reportDefinition.nameMapping?.let(::createWithMapping) ?: DEFAULT_MAPPER

        /**
         * Return a [ReportNameMapper] implementation that applies the given [nameMapping].
         */
        private fun createWithMapping(nameMapping: ReportNameMapping): ReportNameMapper =
            object : ReportNameMapper {
                override fun mapReportNames(reportFiles: Collection<File>): Map<String, File> =
                    if (reportFiles.size == 1 && !nameMapping.alwaysAppendIndex) {
                        mapOf(mapReportFile(nameMapping, 0, false, reportFiles.first()))
                    } else {
                        reportFiles.mapIndexed { index, file ->
                            mapReportFile(nameMapping, index, true, file)
                        }.toMap()
                    }
            }

        /**
         * Return a mapping for the given report [file] with the given [index] (0-based) in the result file collection
         * based on the given [nameMapping]. The [applyIndex] flag controls whether the index is appended to the name.
         */
        private fun mapReportFile(
            nameMapping: ReportNameMapping,
            index: Int,
            applyIndex: Boolean,
            file: File
        ): Pair<String, File> {
            val indexSuffix = if (applyIndex) "-${index + nameMapping.startIndex}" else ""
            return "${nameMapping.namePrefix}$indexSuffix.${file.extension}" to file
        }
    }

    /**
     * Return a map that assigns a name to each element of the given [reportFiles] collection. The report file is then
     * stored under this name.
     */
    fun mapReportNames(reportFiles: Collection<File>): Map<String, File>
}
