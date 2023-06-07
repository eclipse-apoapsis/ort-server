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

package org.ossreviewtoolkit.server.workers.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerConfiguration
import org.ossreviewtoolkit.server.model.runs.scanner.ScannerRun
import org.ossreviewtoolkit.utils.common.gibibytes

private const val TIME_STAMP_SECONDS = 1678119934L

class ScannerWorkerDaoTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var dao: ScannerWorkerDao
    lateinit var fixtures: Fixtures

    beforeEach {
        dao = ScannerWorkerDao(
            analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository,
            analyzerRunRepository = dbExtension.fixtures.analyzerRunRepository,
            ortRunRepository = dbExtension.fixtures.ortRunRepository,
            repositoryRepository = dbExtension.fixtures.repositoryRepository,
            scannerJobRepository = dbExtension.fixtures.scannerJobRepository,
            scannerRunRepository = dbExtension.fixtures.scannerRunRepository
        )
        fixtures = dbExtension.fixtures
    }

    "getScannerJob" should {
        "return job if job does exist" {
            val job = dao.getScannerJob(fixtures.scannerJob.id)

            job shouldBe fixtures.scannerJob
        }

        "return null if job does not exist" {
            dao.getScannerJob(-1L) shouldBe null
        }
    }

    "getOrtRun" should {
        "return an ort run" {
            val ortRun = dao.getOrtRun(fixtures.ortRun.id)

            ortRun shouldBe fixtures.ortRun
        }

        "return null if run does not exist" {
            dao.getOrtRun(-1L) shouldBe null
        }
    }

    "getRepository" should {
        "return a repository" {
            val repository = dao.getRepository(fixtures.repository.id)

            repository shouldBe fixtures.repository
        }

        "return null if repository does not exist" {
            dao.getRepository(-1L) shouldBe null
        }
    }

    "getAnalyzerRunForScannerJob" should {
        "return an analyzer run" {
            val createdAnalyzerRun = dbExtension.fixtures.analyzerRunRepository.create(
                analyzerJobId = fixtures.analyzerJob.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = AnalyzerConfiguration(),
                projects = emptySet(),
                packages = emptySet(),
                issues = emptyMap(),
                dependencyGraphs = emptyMap()
            )

            val requestedAnalyzerRun = dao.getAnalyzerRunForScannerJob(fixtures.scannerJob)

            requestedAnalyzerRun shouldBe createdAnalyzerRun
        }

        "return null if run does not exist" {
            dao.getAnalyzerRunForScannerJob(fixtures.scannerJob.copy(ortRunId = -1L)) shouldBe null
        }
    }

    "storeScannerRun" should {
        "store a scanner run in the database" {
            val scannerRun = ScannerRun(
                id = 1L,
                scannerJobId = fixtures.scannerJob.id,
                startTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                endTime = Instant.fromEpochSeconds(TIME_STAMP_SECONDS),
                environment = Environment(
                    ortVersion = "1.0.0",
                    javaVersion = "17",
                    os = "Linux",
                    processors = 8,
                    maxMemory = 16.gibibytes,
                    variables = emptyMap(),
                    toolVersions = emptyMap()
                ),
                config = ScannerConfiguration(
                    skipConcluded = true,
                    archive = null,
                    createMissingArchives = true,
                    detectedLicenseMappings = mapOf("license-1" to "spdx-license-1"),
                    options = emptyMap(),
                    storages = emptyMap(),
                    storageReaders = listOf("reader-1"),
                    storageWriters = listOf("writer-1"),
                    ignorePatterns = listOf("pattern-1"),
                    provenanceStorage = null
                ),
                scanResults = emptyMap()
            )

            dao.storeScannerRun(scannerRun)

            dbExtension.fixtures.scannerRunRepository.getByJobId(fixtures.scannerJob.id) shouldBe scannerRun
        }
    }
})
