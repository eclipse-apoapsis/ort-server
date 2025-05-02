/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.ortrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll

import kotlin.time.Duration.Companion.seconds

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunIssueDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.OrtRunsIssuesTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.ActiveOrtRun
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

@Suppress("LargeClass")
class DaoOrtRunRepositoryTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var ortRunRepository: DaoOrtRunRepository

    var organizationId = -1L
    var productId = -1L
    var repositoryId = -1L

    val jobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = true
        )
    )

    val resolvedJobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = false,
            skipExcluded = true
        )
    )

    val labelsMap = mapOf("label1" to "label1", "label2" to "label2")

    beforeEach {
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        organizationId = dbExtension.fixtures.organization.id
        productId = dbExtension.fixtures.product.id
        repositoryId = dbExtension.fixtures.repository.id
    }

    afterEach {
        unmockkAll()
    }

    "create" should {
        "create an entry in the database" {
            val revision = "revision"
            val path = "somePath"
            val jobConfigContext = "someConfigContext"
            val traceId = "trace-1234"
            val environmentConfigPath = "path/to/env.yml"

            val createdOrtRun = ortRunRepository.create(
                repositoryId,
                revision,
                path,
                jobConfigurations,
                jobConfigContext,
                labelsMap,
                traceId = traceId,
                environmentConfigPath
            )

            val dbEntry = ortRunRepository.get(createdOrtRun.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe OrtRun(
                id = createdOrtRun.id,
                index = createdOrtRun.id,
                organizationId = organizationId,
                productId = productId,
                repositoryId = repositoryId,
                revision = revision,
                path = path,
                createdAt = createdOrtRun.createdAt,
                jobConfigs = jobConfigurations,
                resolvedJobConfigs = null,
                jobConfigContext = jobConfigContext,
                resolvedJobConfigContext = null,
                status = OrtRunStatus.CREATED,
                finishedAt = null,
                labels = labelsMap,
                vcsId = null,
                vcsProcessedId = null,
                nestedRepositoryIds = emptyMap(),
                repositoryConfigId = null,
                issues = emptyList(),
                traceId = traceId,
                environmentConfigPath = environmentConfigPath
            )
        }

        "create sequential indexes for different repositories" {
            val otherRepository = dbExtension.fixtures.createRepository(url = "https://example.com/repo2.git")

            ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = null,
                null
            ).index shouldBe 1
            ortRunRepository.create(
                otherRepository.id,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = null,
                null
            ).index shouldBe 1
            ortRunRepository.create(
                otherRepository.id,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "some-trace-id",
                null
            ).index shouldBe 2
            ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = null,
                null
            ).index shouldBe 2
        }
    }

    "getByIndex" should {
            "return the correct run" {
                val ortRun = ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    jobConfigurations,
                    null,
                    labelsMap,
                    traceId = "some-trace-id",
                    null
                )

                ortRunRepository.getByIndex(repositoryId, ortRun.index) shouldBe ortRun
            }
    }

    "get" should {
        "return null" {
            ortRunRepository.get(1L).shouldBeNull()
        }

        "return the run" {
            val ortRun = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "some-trace-id",
                null
            )

            ortRunRepository.get(ortRun.id) shouldBe ortRun
        }
    }

    "list" should {
        "return all runs" {
            val ortRun1 = ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "the-first-trace-id",
                null
            )

            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "the-second-trace-id",
                null
            )

            ortRunRepository.list() shouldBe ListQueryResult(listOf(ortRun1, ortRun2), ListQueryParameters.DEFAULT, 2)
        }

        "apply query parameters" {
            ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "t",
                null
            )

            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "trace-id",
                null
            )

            val parameters = ListQueryParameters(
                sortFields = listOf(OrderField("revision", OrderDirection.DESCENDING)),
                limit = 1
            )

            ortRunRepository.list(parameters) shouldBe ListQueryResult(listOf(ortRun2), parameters, 2)
        }

        "apply filters" {
            ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "t",
                null
            )

            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "trace-id",
                null
            )

            val failedRun = ortRunRepository.update(ortRun2.id, status = OrtRunStatus.FAILED.asPresent())

            val filters = OrtRunFilters(
                status = FilterOperatorAndValue(
                    ComparisonOperator.IN,
                    setOf(OrtRunStatus.FAILED)
                )
            )

            ortRunRepository.list(
                ListQueryParameters.DEFAULT,
                filters
            ) shouldBe ListQueryResult(listOf(failedRun), ListQueryParameters.DEFAULT, 1)
        }

        "apply filters based on operator" {
            val ortRun1 = ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "t",
                null
            )

            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "trace-id",
                null
            )

            ortRunRepository.update(ortRun2.id, status = OrtRunStatus.FAILED.asPresent())

            val filters = OrtRunFilters(
                status = FilterOperatorAndValue(
                    ComparisonOperator.NOT_IN,
                    setOf(OrtRunStatus.FAILED)
                )
            )

            ortRunRepository.list(
                ListQueryParameters.DEFAULT,
                filters
            ) shouldBe ListQueryResult(listOf(ortRun1), ListQueryParameters.DEFAULT, 1)
        }
    }

    "listForRepositories" should {
        "return all runs for a repository" {
            val ortRun1 = ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "the-first-trace-id",
                null
            )
            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "the-second-trace-id",
                null
            )

            ortRunRepository.listForRepository(repositoryId) shouldBe
                    ListQueryResult(listOf(ortRun1, ortRun2), ListQueryParameters.DEFAULT, 2)
        }

        "apply query parameters" {
            ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "t",
                null
            )
            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "trace-id",
                null
            )

            val parameters = ListQueryParameters(
                sortFields = listOf(OrderField("revision", OrderDirection.DESCENDING)),
                limit = 1
            )

            ortRunRepository.listForRepository(repositoryId, parameters) shouldBe
                    ListQueryResult(listOf(ortRun2), parameters, 2)
        }
    }

    "listActiveRuns" should {
        "return all active runs" {
            val createdRun = ortRunRepository.create(
                repositoryId,
                "revision1",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "t",
                null
            )
            val activeRun = ortRunRepository.create(
                repositoryId,
                "revision2",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "trace-id",
                null
            )
            ortRunRepository.update(
                id = activeRun.id,
                status = OrtRunStatus.ACTIVE.asPresent()
            )

            setOf(OrtRunStatus.FAILED, OrtRunStatus.FINISHED, OrtRunStatus.FINISHED_WITH_ISSUES).forEach { status ->
                val run = ortRunRepository.create(
                    repositoryId,
                    "revision${status.name}",
                    null,
                    jobConfigurations,
                    null,
                    labelsMap,
                    traceId = "irrelevant",
                    null
                )
                ortRunRepository.update(run.id, status = status.asPresent())
            }

            val activeRuns = ortRunRepository.listActiveRuns()

            activeRuns shouldContainExactlyInAnyOrder listOf(
                ActiveOrtRun(createdRun.id, createdRun.createdAt, createdRun.traceId),
                ActiveOrtRun(activeRun.id, activeRun.createdAt, activeRun.traceId)
            )
        }
    }

    "findRunsBefore" should {
        "return all runs before a given time" {
            val refTime = Instant.parse("2025-01-15T08:34:48Z")
            val finishedTimes = listOf(
                refTime - 10.seconds,
                refTime - 1.seconds,
                refTime,
                refTime + 1.seconds
            )

            // Mock the clock to have defined finishedAt times for the test ORT runs.
            // For each run, the repository queries the clock twice: for the creation and the completion time.
            val mockTimes = buildList {
                finishedTimes.forEach {
                    add(it - 30.seconds)
                    add(it)
                }
            }
            mockkObject(Clock.System)
            every {
                Clock.System.now()
            } returnsMany mockTimes

            fun createFinishedRun(): OrtRun =
                ortRunRepository.create(
                    repositoryId,
                    "revision",
                    null,
                    jobConfigurations,
                    null,
                    labelsMap,
                    traceId = "irrelevant",
                    null
                ).run { ortRunRepository.update(id, status = OrtRunStatus.FINISHED.asPresent()) }

            val runs = List(4) { createFinishedRun() }
            ortRunRepository.create(
                repositoryId,
                "revision_active",
                null,
                jobConfigurations,
                null,
                labelsMap,
                traceId = "irrelevant",
                null
            )

            val runsBefore = ortRunRepository.findRunsBefore(refTime)

            val expectedIds = runs.take(2).map(OrtRun::id)
            runsBefore shouldContainExactlyInAnyOrder expectedIds
        }
    }

    "update" should {
        "update an entry in the database" {
            val identifier = dbExtension.db.dbQuery {
                IdentifierDao.getOrPut(Identifier("test", "ns", "name", "1.0"))
            }

            val issue1 = Issue(
                Instant.parse("2023-08-02T06:16:10Z"),
                "existing",
                "An initial issue",
                Severity.WARNING,
                "some/path",
                identifier.mapToModel(),
                "Analyzer"
            )
            val issue2 = Issue(
                Instant.parse("2023-08-02T06:17:16Z"),
                "new1",
                "An new issue",
                Severity.HINT,
                identifier = identifier.mapToModel()
            )
            val issue3 = Issue(
                Instant.parse("2023-08-02T06:17:36Z"),
                "new2",
                "Another new issue",
                Severity.ERROR,
                worker = "Reporter"
            )

            val label2Value = "new value for label2"
            val label3Value = "a newly added label"

            val ortRun = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                "theTraceId",
                null
            )

            val resolvedContext = "theResolvedConfigContext"
            val revision = "main"
            val resolvedRevision = "0123456789abcdef0123456789abcdef01234567"
            val updateStatus = OrtRunStatus.ACTIVE.asPresent()

            val updateResult = ortRunRepository.update(
                ortRun.id,
                updateStatus,
                jobConfigurations.asPresent(),
                resolvedJobConfigurations.asPresent(),
                resolvedContext.asPresent(),
                revision.asPresent(),
                resolvedRevision.asPresent(),
                listOf(issue1, issue2, issue3).asPresent(),
                mapOf("label2" to label2Value, "label3" to label3Value).asPresent()
            )

            val expectedResult = ortRun.copy(
                status = updateStatus.value,
                resolvedJobConfigs = resolvedJobConfigurations,
                resolvedJobConfigContext = resolvedContext,
                revision = revision,
                resolvedRevision = resolvedRevision,
                issues = listOf(issue1, issue2, issue3),
                labels = mapOf(
                    "label1" to labelsMap.getValue("label1"),
                    "label2" to label2Value,
                    "label3" to label3Value
                )
            )
            updateResult shouldBe expectedResult
            ortRunRepository.get(ortRun.id) shouldBe expectedResult
        }

        "add new issues to a run" {
            val identifier = dbExtension.db.dbQuery {
                IdentifierDao.getOrPut(Identifier("test", "ns", "name", "1.0"))
            }

            val issue1 = Issue(
                Instant.parse("2023-08-02T06:16:10Z"),
                "existing",
                "An initial issue",
                Severity.WARNING,
                "some/path",
                identifier.mapToModel(),
                "Analyzer"
            )
            val issue2 = Issue(
                Instant.parse("2023-08-02T06:17:16Z"),
                "new1",
                "An new issue",
                Severity.HINT,
                identifier = identifier.mapToModel()
            )
            val issue3 = Issue(
                Instant.parse("2023-08-02T06:17:36Z"),
                "new2",
                "Another new issue",
                Severity.ERROR,
                worker = "Reporter"
            )

            val ortRun = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                "theTraceId",
                null
            )

            ortRunRepository.update(
                ortRun.id,
                issues = listOf(issue1).asPresent()
            )
            val updateResult = ortRunRepository.update(
                ortRun.id,
                issues = listOf(issue2, issue3).asPresent()
            )
            val expectedResult = ortRun.copy(
                issues = listOf(issue1, issue2, issue3),
            )
            updateResult shouldBe expectedResult
            ortRunRepository.get(ortRun.id) shouldBe expectedResult
        }

        "deduplicate issues added to a run" {
            val identifier1 = dbExtension.db.dbQuery {
                IdentifierDao.getOrPut(Identifier("test", "ns", "name", "1.0"))
            }
            val identifier2 = dbExtension.db.dbQuery {
                IdentifierDao.getOrPut(Identifier("test", "ns", "name2", "2.0"))
            }

            val issue1 = Issue(
                Instant.parse("2024-10-18T09:35:41Z"),
                "test",
                "A test issue",
                Severity.WARNING,
                "test/path",
                identifier1.mapToModel(),
                "Analyzer"
            )
            val issue2 = Issue(
                Instant.parse("2024-10-18T09:36:02Z"),
                "test",
                "One more test issue",
                Severity.HINT,
                identifier = identifier1.mapToModel(),
                worker = "Analyzer"
            )
            val issue3 = issue1.copy(
                timestamp = Instant.parse("2024-10-18T09:36:22Z"),
                identifier = identifier2.mapToModel(),
                worker = "Test worker"
            )

            val ortRun1 = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                labelsMap,
                "theTraceId",
                null
            )
            val ortRun2 = ortRunRepository.create(
                repositoryId,
                "other_revision",
                null,
                jobConfigurations,
                null,
                emptyMap(),
                "otherTraceId",
                null
            )

            ortRunRepository.update(
                ortRun1.id,
                issues = listOf(issue1, issue2).asPresent()
            )
            ortRunRepository.update(
                ortRun2.id,
                issues = listOf(issue3).asPresent()
            )

            dbExtension.db.dbQuery {
                val issueTime = Instant.parse("2024-10-18T09:42:31Z")
                val expectedIssues = listOf(
                    issue1.copy(timestamp = issueTime, worker = null, identifier = null),
                    issue2.copy(timestamp = issueTime, worker = null, identifier = null)
                )

                val issues = IssueDao.all().map { it.mapToModel(issueTime, null, null) }
                issues shouldContainExactlyInAnyOrder expectedIssues
            }
        }

        "mark a finished run as completed" {
            val ortRun = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                emptyMap(),
                "theTraceId",
                null
            )

            val updateResult = ortRunRepository.update(ortRun.id, status = OrtRunStatus.FINISHED.asPresent())

            assertCurrentTime(updateResult.finishedAt)
            ortRunRepository.get(ortRun.id) shouldBe updateResult
        }

        "mark a failed run as completed" {
            val ortRun = ortRunRepository.create(
                repositoryId,
                "revision",
                null,
                jobConfigurations,
                null,
                emptyMap(),
                "traceIT",
                null
            )

            val updateResult = ortRunRepository.update(ortRun.id, status = OrtRunStatus.FAILED.asPresent())

            assertCurrentTime(updateResult.finishedAt)
            ortRunRepository.get(ortRun.id) shouldBe updateResult
        }
    }

    "delete" should {
        "delete the database entries" {
            with(dbExtension.fixtures) {
                createAnalyzerJob()
                createAdvisorJob()
                createScannerJob()
                createEvaluatorJob()
                createReporterJob()
                createNotifierJob()

                val issue = Issue(
                    Instant.parse("2024-11-12T14:49:35Z"),
                    "test",
                    "A test issue",
                    Severity.WARNING,
                    "test/path",
                    null,
                    "Analyzer"
                )
                ortRunRepository.update(ortRun.id, issues = listOf(issue).asPresent())

                ortRunRepository.delete(ortRun.id)

                ortRunRepository.listForRepository(repository.id).data shouldBe emptyList()

                analyzerJobRepository.getForOrtRun(ortRun.id) should beNull()
                advisorJobRepository.getForOrtRun(ortRun.id) should beNull()
                scannerJobRepository.getForOrtRun(ortRun.id) should beNull()
                evaluatorJobRepository.getForOrtRun(ortRun.id) should beNull()
                reporterJobRepository.getForOrtRun(ortRun.id) should beNull()
                notifierJobRepository.getForOrtRun(ortRun.id) should beNull()
            }

            dbExtension.db.dbQuery {
                val runIssues = OrtRunIssueDao.find {
                    OrtRunsIssuesTable.ortRunId eq dbExtension.fixtures.ortRun.id
                }.toList()
                runIssues should beEmpty()

                val runLabels = OrtRunsLabelsTable.select(OrtRunsLabelsTable.labelId)
                    .where { OrtRunsLabelsTable.ortRunId eq dbExtension.fixtures.ortRun.id }
                    .toList()
                runLabels should beEmpty()
            }
        }
    }
})

/**
 * Check whether the given [timestamp] is rather close to the current system time.
 */
private fun assertCurrentTime(timestamp: Instant?) {
    val time = timestamp.shouldNotBeNull()

    val delta = Clock.System.now() - time
    delta.inWholeMilliseconds shouldBeGreaterThan 0
    delta.inWholeMilliseconds shouldBeLessThan 3000
}
