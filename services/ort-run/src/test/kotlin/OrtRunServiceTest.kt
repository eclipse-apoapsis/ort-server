/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services.ortrun

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.reporter.Report
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.services.ReportNotFoundException
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.storage.StorageException

import org.jetbrains.exposed.sql.Database

class OrtRunServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
        }

        "listOrtRuns" should {
            "return all ort runs" {
                val service = createService()
                createOrtRuns()

                val results = service.listOrtRuns().data

                results shouldHaveSize 4
            }

            "return ort runs filtered by status" {
                val service = createService()
                createOrtRuns()

                val filters = OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.IN,
                        setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
                    )
                )

                val results = service.listOrtRuns(ListQueryParameters.DEFAULT, filters)

                results.data shouldHaveSize 2
                results.totalCount shouldBe 2

                results.data.first().status shouldBeIn setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
                results.data.last().status shouldBeIn setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
            }

            "return an empty list if no ORT runs with requested statuses are found" {
                val service = createService()
                createOrtRuns()

                val filters = OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.IN,
                        setOf(OrtRunStatus.FINISHED_WITH_ISSUES)
                    )
                )

                val results = service.listOrtRuns(ListQueryParameters.DEFAULT, filters)

                results.data shouldHaveSize 0
                results.totalCount shouldBe 0
            }
        }

        "deleteOrtRun" should {
            "delete an ORT run" {
                val ortRunId = createOrtRun()
                val jobId = createReporterJob(ortRunId)

                val mockReportStorageService = mockk<ReportStorageService> {
                    coEvery { deleteReport(any() as Long, any() as String) } just Runs
                }

                val service = createService(mockReportStorageService)
                service.deleteOrtRun(ortRunId)

                coVerify(exactly = 3) {
                    mockReportStorageService.deleteReport(
                        eq(ortRunId),
                        or(eq("abc123"), or(eq("def456"), eq("ghi789")))
                    )
                }

                fixtures.reporterRunRepository.getByJobId(jobId) shouldBe null
                fixtures.ortRunRepository.get(ortRunId) shouldBe null
            }

            "delete an ORT run even if some reports are not found in storage" {
                val ortRunId = createOrtRun()
                val jobId = createReporterJob(ortRunId)

                val mockReportStorageService = mockk<ReportStorageService> {
                    coEvery {
                        deleteReport(any() as Long, any() as String)
                    } just Runs andThenThrows ReportNotFoundException(ortRunId, "def456") andThenJust runs
                }

                val service = createService(mockReportStorageService)
                service.deleteOrtRun(ortRunId)

                // Check if the remaining reports were deleted, although one report was not found.
                coVerify(exactly = 2) {
                    mockReportStorageService.deleteReport(
                        eq(ortRunId),
                        or(eq("abc123"), eq("ghi789"))
                    )
                }

                fixtures.reporterRunRepository.getByJobId(jobId) shouldBe null
                fixtures.ortRunRepository.get(ortRunId) shouldBe null
            }

            "not delete an ORT run in case of technical issues of the report storage" {
                val ortRunId = createOrtRun()
                val jobId = createReporterJob(ortRunId)

                val mockReportStorageService = mockk<ReportStorageService> {
                    coEvery {
                        deleteReport(any() as Long, any() as String)
                    } just Runs andThenThrows StorageException("Simulated Exception")
                }

                val service = createService(mockReportStorageService)

                shouldThrow<StorageException> {
                    service.deleteOrtRun(ortRunId)
                }.message shouldBe "Simulated Exception"

                fixtures.reporterRunRepository.getByJobId(jobId) shouldNotBe null
                fixtures.ortRunRepository.get(ortRunId) shouldNotBe null
            }
        }

        "deleteRunsCreatedBefore" should {
            "delete all ORT runs older than the given timestamp" {
                val (ortRunId1, ortRunId2, ortRunId3) = createOrtRuns()
                val reporterjobId1 = createReporterJob(ortRunId1)
                val reporterjobId2 = createReporterJob(ortRunId2)
                val reporterjobId3 = createReporterJob(ortRunId3)

                val mockReportStorageService = mockk<ReportStorageService> {
                    coEvery { deleteReport(any() as Long, any() as String) } just Runs
                }

                val service = createService(mockReportStorageService)

                fixtures.ortRunRepository.update(ortRunId1, OrtRunStatus.FINISHED.asPresent())
                delay(1000)
                fixtures.ortRunRepository.update(ortRunId2, OrtRunStatus.FINISHED.asPresent())
                delay(1000)
                fixtures.ortRunRepository.update(ortRunId3, OrtRunStatus.FINISHED.asPresent())

                val finishedAt = fixtures.ortRunRepository.get(ortRunId3)?.finishedAt
                finishedAt shouldNotBe null
                finishedAt?.let {
                    service.deleteRunsCreatedBefore(it.minus(500.milliseconds))
                }

                fixtures.ortRunRepository.get(ortRunId1) shouldBe null
                // Run 2 is the latest run in the repository and is intentionally retained.
                fixtures.ortRunRepository.get(ortRunId2) shouldNotBe null
                fixtures.ortRunRepository.get(ortRunId3) shouldNotBe null

                fixtures.reporterRunRepository.getByJobId(reporterjobId1) shouldBe null
                fixtures.reporterRunRepository.getByJobId(reporterjobId2) shouldNotBe null
                fixtures.reporterRunRepository.getByJobId(reporterjobId3) shouldNotBe null
            }

            "delete only finished ORT runs older than the given timestamp" {
                val (ortRunId1, ortRunId2, ortRunId3) = createOrtRuns()
                val reporterjobId1 = createReporterJob(ortRunId1)
                val reporterjobId2 = createReporterJob(ortRunId2)
                val reporterjobId3 = createReporterJob(ortRunId3)

                val mockReportStorageService = mockk<ReportStorageService> {
                    coEvery { deleteReport(any() as Long, any() as String) } just Runs
                }

                val service = createService(mockReportStorageService)
                service.deleteRunsCreatedBefore(Clock.System.now())

                fixtures.ortRunRepository.get(ortRunId1) shouldBe null
                // Run 2 is the latest run in the repository and is intentionally retained.
                fixtures.ortRunRepository.get(ortRunId2) shouldNotBe null
                // Run 3 is active and should not be deleted
                fixtures.ortRunRepository.get(ortRunId3) shouldNotBe null

                fixtures.reporterRunRepository.getByJobId(reporterjobId1) shouldBe null
                fixtures.reporterRunRepository.getByJobId(reporterjobId2) shouldNotBe null
                fixtures.reporterRunRepository.getByJobId(reporterjobId3) shouldNotBe null
            }
        }
    }

    private fun createService(reportStorageService: ReportStorageService = mockk()) =
        OrtRunService(db, fixtures.ortRunRepository, fixtures.reporterJobRepository, reportStorageService)

    private fun createRepository(organizationName: String): Long {
        val organizationId = fixtures.createOrganization(organizationName).id
        val productId = fixtures.createProduct(organizationId = organizationId).id
        val repositoryId = fixtures.createRepository(productId = productId).id

        return repositoryId
    }

    private fun createOrtRun(): Long {
        val repositoryId = createRepository("org3")
        val ortRunId = fixtures.createOrtRun(repositoryId).id

        return ortRunId
    }

    private fun createOrtRuns(): List<Long> {
        val repository1Id = createRepository("org1")
        val repository2Id = createRepository("org2")

        val ortRunId1 = fixtures.createOrtRun(repository1Id).id
        val ortRunId2 = fixtures.createOrtRun(repository1Id).id
        val ortRunId3 = fixtures.createOrtRun(repository2Id).id
        fixtures.createOrtRun(repository2Id).id

        fixtures.ortRunRepository.update(ortRunId1, OrtRunStatus.FINISHED.asPresent())
        fixtures.ortRunRepository.update(ortRunId2, OrtRunStatus.FAILED.asPresent())
        fixtures.ortRunRepository.update(ortRunId3, OrtRunStatus.ACTIVE.asPresent())

        return listOf(ortRunId1, ortRunId2, ortRunId3)
    }

    private fun createReporterJob(ortRunId: Long): Long {
        val reporterJobId = fixtures.createReporterJob(ortRunId, ReporterJobConfiguration()).id
        val reports = listOf(
            Report("abc123", "https://example.com/report/abc123", Clock.System.now()),
            Report("def456", "https://example.com/report/def456", Clock.System.now()),
            Report("ghi789", "https://example.com/report/ghi789", Clock.System.now()),
        )

        fixtures.reporterRunRepository.create(
            reporterJobId = reporterJobId,
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            reports = reports
        )

        return reporterJobId
    }
}
