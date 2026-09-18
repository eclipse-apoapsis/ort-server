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

package org.eclipse.apoapsis.ortserver.components.reportstorage

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.http.ContentType

import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayOutputStream
import java.io.File

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.StorageProviderFactoryForTesting

class ReportStorageServiceTest : WordSpec({
    "fetchReport()" should {
        "return a ReportDownloadData object for an existing report" {
            val fileName = "testReport.html"
            val reportData = "This is a report from the storage."
            val contentType = ContentType.Text.Html

            val storage = createStorage()
            storage.write(generateKey(RUN_ID, fileName), reportData, contentType.toString())

            val service = ReportStorageService(storage, mockk())

            val downloadData = service.fetchReport(RUN_ID, fileName)
            downloadData.contentType shouldBe contentType

            val stream = ByteArrayOutputStream()
            downloadData.loader(stream)
            stream.toString() shouldBe reportData
        }

        "throw an exception if the requested report cannot be found" {
            val fileName = "nonExistingReport.dat"

            val storage = createStorage()

            val service = ReportStorageService(storage, mockk())
            val exception = shouldThrow<ReportNotFoundException> {
                service.fetchReport(RUN_ID, fileName)
            }

            exception.message shouldContain RUN_ID.toString()
            exception.message shouldContain fileName
        }

        "handle an undefined content type" {
            val fileName = "testReportWithoutContentType.dat"
            val reportData = "This is a report from the storage.".toByteArray()

            val storage = createStorage()
            storage.write(generateKey(RUN_ID, fileName), reportData)

            val service = ReportStorageService(storage, mockk())
            val downloadData = service.fetchReport(RUN_ID, fileName)

            downloadData.contentType shouldBe ContentType.Application.OctetStream
        }
    }

    "fetchReportByToken()" should {
        "return a ReportDownloadData object for a valid token" {
            val fileName = "testReport.html"
            val reportData = "This is a report from the storage, resolved from a token."
            val contentType = ContentType.Text.Html
            val token = "test-report-token"

            val storage = createStorage()
            storage.write(generateKey(RUN_ID, fileName), reportData, contentType.toString())

            val reporterJobRepository = mockk<ReporterJobRepository> {
                every { getReportByToken(RUN_ID, token) } returns Report(fileName, token, Instant.DISTANT_FUTURE)
            }

            val service = ReportStorageService(storage, reporterJobRepository)
            val downloadData = service.fetchReportByToken(RUN_ID, token)

            downloadData.contentType shouldBe contentType

            val stream = ByteArrayOutputStream()
            downloadData.loader(stream)
            stream.toString() shouldBe reportData
        }

        "throw an exception if the token cannot be resolved" {
            val token = "anInvalidToken"
            val reporterJobRepository = mockk<ReporterJobRepository> {
                every { getReportByToken(RUN_ID, token) } returns null
            }

            val service = ReportStorageService(createStorage(), reporterJobRepository)

            val exception = shouldThrow<ReportNotFoundException> {
                service.fetchReportByToken(RUN_ID, token)
            }

            exception.message shouldContain RUN_ID.toString()
        }
    }

    "guessContentType()" should {
        "return a default content type in case of an exception" {
            val file = File("nonExistingReportFile")

            guessContentType(file) shouldBe "application/octet-stream"
        }
    }

    "storeReports()" should {
        "write the provided files to the storage" {
            val reportData = listOf(
                "Content of a report",
                "Content of another report",
                "A more complex content of a sophisticated report."
            )
            val reportFiles = reportData.mapIndexed { index, content ->
                val file = tempfile()
                file.writeText(content)
                "report-$index.txt" to file
            }.toMap()

            val service = ReportStorageService(createStorage(), mockk())
            service.storeReports(RUN_ID, reportFiles)

            reportData.zip(reportFiles.entries).forAll { (data, file) ->
                val key = Key("$RUN_ID|${file.key}")
                val entry = StorageProviderFactoryForTesting.getEntry(key)
                entry.data shouldBe data.toByteArray()
                entry.length shouldBe data.length
                entry.contentType shouldBe "application/octet-stream"
            }
        }

        "detect the content type" {
            val key = "testReport"
            val reportFile = tempfile(suffix = ".json")
            reportFile.writeText("""{ "test": true }""")

            val reportStorage = ReportStorageService(createStorage(), mockk())
            reportStorage.storeReports(RUN_ID, mapOf(key to reportFile))

            val entry = StorageProviderFactoryForTesting.getEntry(Key("$RUN_ID|$key"))
            entry.contentType shouldBe "application/json"
        }
    }
})

private const val RUN_ID = 20230522073118L

/**
 * Create the [Storage] to be used for tests. This is a test storage, so the stored data can be inspected.
 */
private fun createStorage(): Storage {
    val storageType = "test"
    val configMap = mapOf(storageType to mapOf("name" to StorageProviderFactoryForTesting.NAME))
    val config = ConfigFactory.parseMap(configMap)

    return Storage.create(storageType, ConfigManager.create(config))
}
