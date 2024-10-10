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

package org.eclipse.apoapsis.ortserver.dao.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.tables.runs.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
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

class DaoOrtRunRepositoryTest : StringSpec({
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

    "create should create an entry in the database" {
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

    "create should create sequential indexes for different repositories" {
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

    "getByIndex should return the correct run" {
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

    "get should return null" {
        ortRunRepository.get(1L).shouldBeNull()
    }

    "get should return the run" {
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

    "list should return all runs" {
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

    "list should apply query parameters" {
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

    "list should apply filters" {
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

    "list should apply filters based on operator" {
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

    "listForRepositories should return all runs for a repository" {
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

    "listForRepositories should apply query parameters" {
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

    "update should update an entry in the database" {
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
        val updateStatus = OrtRunStatus.ACTIVE.asPresent()

        val updateResult = ortRunRepository.update(
            ortRun.id,
            updateStatus,
            jobConfigurations.asPresent(),
            resolvedJobConfigurations.asPresent(),
            resolvedContext.asPresent(),
            listOf(issue1, issue2, issue3).asPresent(),
            mapOf("label2" to label2Value, "label3" to label3Value).asPresent()
        )

        val expectedResult = ortRun.copy(
            status = updateStatus.value,
            resolvedJobConfigs = resolvedJobConfigurations,
            resolvedJobConfigContext = resolvedContext,
            issues = listOf(issue1, issue2, issue3),
            labels = mapOf("label1" to labelsMap.getValue("label1"), "label2" to label2Value, "label3" to label3Value)
        )
        updateResult shouldBe expectedResult
        ortRunRepository.get(ortRun.id) shouldBe expectedResult
    }

    "update should add new issues to a run" {
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

    "update should mark a finished run as completed" {
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

    "update should mark a failed run as completed" {
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

    "delete should delete the database entry" {
        val ortRun = ortRunRepository.create(
            repositoryId,
            "revision",
            null,
            jobConfigurations,
            null,
            labelsMap,
            traceId = "delete-without-trace",
            null
        )

        ortRunRepository.delete(ortRun.id)

        ortRunRepository.listForRepository(repositoryId).data shouldBe emptyList()
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
