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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.server.storage.Key
import org.ossreviewtoolkit.server.storage.Storage
import org.ossreviewtoolkit.server.storage.StorageProviderFactoryForTesting

class ReportStorageTest : StringSpec({
    "Report files should be written to the storage" {
        val reportData = listOf(
            "Content of a report",
            "Content of another report",
            "A more complex content of a sophisticated report."
        )
        val reportFiles = reportData.map { content ->
            val file = tempfile()
            file.writeText(content)
            file
        }

        val reportStorage = ReportStorage(createStorage())
        reportStorage.storeReportFiles(RUN_ID, reportFiles)

        reportData.zip(reportFiles).forAll { (data, file) ->
            val key = Key("$RUN_ID|${file.name}")
            val entry = StorageProviderFactoryForTesting.getEntry(key)
            entry.data shouldBe data.toByteArray()
            entry.length shouldBe data.length
            entry.contentType shouldBe "application/octet-stream"
        }
    }

    "The content type should be detected" {
        val reportFile = tempfile(suffix = ".json")
        reportFile.writeText("""{ "test": true }""")

        val reportStorage = ReportStorage(createStorage())
        reportStorage.storeReportFiles(RUN_ID, listOf(reportFile))

        val entry = StorageProviderFactoryForTesting.getEntry(Key("$RUN_ID|${reportFile.name}"))
        entry.contentType shouldBe "application/json"
    }

    "Exceptions during content type detection are handled" {
        val file = File("nonExistingReportFile")

        ReportStorage.guessContentType(file) shouldBe "application/octet-stream"
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

    return Storage.create(storageType, config)
}
