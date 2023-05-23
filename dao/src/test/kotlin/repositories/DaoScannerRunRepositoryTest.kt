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

package org.ossreviewtoolkit.server.dao.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.scanner.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileArchiveConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileBasedStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.FileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.LocalFileStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresConnection
import org.ossreviewtoolkit.server.model.runs.scanner.PostgresStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ProvenanceStorageConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun

class DaoScannerRunRepositoryTest : StringSpec() {
    private val scannerRunRepository = DaoScannerRunRepository()

    private lateinit var fixtures: Fixtures
    private var scannerJobId = -1L

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
                scannerJobId = fixtures.scannerJob.id
            }
        )

        "create should create an entry in the database" {
            val variables = mapOf(
                "SHELL" to "/bin/bash",
                "TERM" to "xterm-256color"
            )

            val toolVersions = mapOf(
                "Conan" to "1.53.0",
                "NPM" to "8.15.1"
            )

            val environment = Environment(
                ortVersion = "1.0",
                javaVersion = "11.0.16",
                os = "Linux",
                processors = 8,
                maxMemory = 8321499136,
                variables = variables,
                toolVersions = toolVersions
            )

            val scannerConfiguration = ScannerConfiguration(
                skipConcluded = false,
                archive = FileArchiveConfiguration(
                    enabled = true,
                    fileStorage = FileStorageConfiguration(
                        localFileStorage = LocalFileStorageConfiguration(
                            directory = "/path/to/storage",
                            compression = true
                        )
                    )
                ),
                createMissingArchives = true,
                detectedLicenseMappings = mapOf(
                    "license-1" to "spdx-license-1",
                    "license-2" to "spdx-license-2"
                ),
                options = mapOf(
                    "scanner-1" to mapOf("option-key-1" to "option-value-1"),
                    "scanner-2" to mapOf("option-key-1" to "option-value1", "option-key-2" to "option-value-2")
                ),
                storages = mapOf(
                    "local" to FileBasedStorageConfiguration(
                        backend = FileStorageConfiguration(
                            localFileStorage = LocalFileStorageConfiguration(
                                directory = "/path/to/storage",
                                compression = true
                            )
                        ),
                        type = "PROVENANCE_BASED"
                    ),
                    "clearlyDefined" to ClearlyDefinedStorageConfiguration(
                        serverUrl = "https://api.clearlydefined.io"
                    )
                ),
                storageReaders = listOf("reader-1", "reader-2"),
                storageWriters = listOf("writer-1", "writer-2"),
                ignorePatterns = listOf("pattern-1", "pattern-2"),
                provenanceStorage = ProvenanceStorageConfiguration(
                    postgresStorageConfiguration = PostgresStorageConfiguration(
                        connection = PostgresConnection(
                            url = "jdbc:postgresql://postgresql-server:5432/database",
                            schema = "public",
                            username = "username",
                            sslMode = "required",
                            sslCert = "/defaultdir/postgresql.crt",
                            sslKey = "/defaultdir/postgresql.pk8",
                            sslRootCert = "/defaultdir/root.crt",
                            parallelTransactions = 5
                        ),
                        type = "PROVENANCE_BASED"
                    )
                )
            )

            val createdScannerRun = scannerRunRepository.create(
                scannerJobId = scannerJobId,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = environment,
                config = scannerConfiguration,
                results = emptyMap()
            )

            val dbEntry = scannerRunRepository.get(createdScannerRun.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe ScannerRun(
                id = createdScannerRun.id,
                scannerJobId = scannerJobId,
                startTime = createdScannerRun.startTime,
                endTime = createdScannerRun.endTime,
                environment = environment,
                config = scannerConfiguration,
                scanResults = emptyMap()
            )
        }

        "get should return null" {
            scannerRunRepository.get(1L).shouldBeNull()
        }
    }
}
