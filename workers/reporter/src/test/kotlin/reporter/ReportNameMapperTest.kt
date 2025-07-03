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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.eclipse.apoapsis.ortserver.model.ReportNameMapping
import org.eclipse.apoapsis.ortserver.services.config.ReportDefinition

class ReportNameMapperTest : StringSpec({
    "Default file names should be used if no configuration is defined" {
        val definition = ReportDefinition("unConfiguredReporter")
        val reportFiles = listOf(File("report1.pdf"), File("parent", "sub.html"))

        val mapper = ReportNameMapper.create(definition)
        val mappedFiles = mapper.mapReportNames(reportFiles)

        mappedFiles shouldBe mapOf(
            "report1.pdf" to reportFiles[0],
            "sub.html" to reportFiles[1]
        )
    }

    "A mapping from the definition should be applied to file names" {
        val reportNameMapping = ReportNameMapping(namePrefix = "testReport")
        val definition = ReportDefinition("testReporter", nameMapping = reportNameMapping)
        val reportFiles = listOf(File("report1.pdf"), File("parent", "sub.html"))

        val mapper = ReportNameMapper.create(definition)
        val mappedFiles = mapper.mapReportNames(reportFiles)

        mappedFiles shouldBe mapOf(
            "testReport-1.pdf" to reportFiles[0],
            "testReport-2.html" to reportFiles[1]
        )
    }

    "The start index should be applied correctly" {
        val reportNameMapping = ReportNameMapping(namePrefix = "testReport", startIndex = 0)
        val definition = ReportDefinition("testReporterZeroIndex", nameMapping = reportNameMapping)
        val reportFiles = listOf(File("report1.pdf"), File("parent", "sub.html"))

        val mapper = ReportNameMapper.create(definition)
        val mappedFiles = mapper.mapReportNames(reportFiles)

        mappedFiles shouldBe mapOf(
            "testReport-0.pdf" to reportFiles[0],
            "testReport-1.html" to reportFiles[1]
        )
    }

    "A single result file should be mapped correctly if no index should be added" {
        val reportNameMapping = ReportNameMapping(namePrefix = "testReport")
        val definition = ReportDefinition("testReporter", nameMapping = reportNameMapping)
        val reportFiles = listOf(File("single-result.pdf"))

        val mapper = ReportNameMapper.create(definition)
        val mappedFiles = mapper.mapReportNames(reportFiles)

        mappedFiles shouldBe mapOf(
            "testReport.pdf" to reportFiles[0]
        )
    }

    "A single result file should be mapped correctly if always an index should be added" {
        val reportNameMapping = ReportNameMapping(namePrefix = "testReport", startIndex = 42, alwaysAppendIndex = true)
        val config = ReportDefinition("testReporterWithIndex", nameMapping = reportNameMapping)
        val reportFiles = listOf(File("single-result.pdf"))

        val mapper = ReportNameMapper.create(config)
        val mappedFiles = mapper.mapReportNames(reportFiles)

        mappedFiles shouldBe mapOf(
            "testReport-42.pdf" to reportFiles[0]
        )
    }
})
