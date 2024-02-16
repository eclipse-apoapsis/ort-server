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

package org.ossreviewtoolkit.server.dao.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OrderDirection
import org.ossreviewtoolkit.server.model.util.OrderField
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoOrtRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var ortRunRepository: DaoOrtRunRepository

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
        repositoryId = dbExtension.fixtures.repository.id
    }

    "create should create an entry in the database" {
        val revision = "revision"
        val jobConfigContext = "someConfigContext"

        val createdOrtRun =
            ortRunRepository.create(repositoryId, revision, jobConfigurations, jobConfigContext, labelsMap)

        val dbEntry = ortRunRepository.get(createdOrtRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe OrtRun(
            id = createdOrtRun.id,
            index = createdOrtRun.id,
            repositoryId = repositoryId,
            revision = revision,
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
            issues = emptyList()
        )
    }

    "create should create sequential indexes for different repositories" {
        val otherRepository = dbExtension.fixtures.createRepository(url = "https://example.com/repo2.git")

        ortRunRepository.create(repositoryId, "revision", jobConfigurations, null, labelsMap).index shouldBe 1
        ortRunRepository.create(otherRepository.id, "revision", jobConfigurations, null, labelsMap).index shouldBe 1
        ortRunRepository.create(otherRepository.id, "revision", jobConfigurations, null, labelsMap).index shouldBe 2
        ortRunRepository.create(repositoryId, "revision", jobConfigurations, null, labelsMap).index shouldBe 2
    }

    "getByIndex should return the correct run" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, null, labelsMap)

        ortRunRepository.getByIndex(repositoryId, ortRun.index) shouldBe ortRun
    }

    "get should return null" {
        ortRunRepository.get(1L).shouldBeNull()
    }

    "get should return the run" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, null, labelsMap)

        ortRunRepository.get(ortRun.id) shouldBe ortRun
    }

    "listForRepositories should return all runs for a repository" {
        val ortRun1 = ortRunRepository.create(repositoryId, "revision1", jobConfigurations, null, labelsMap)
        val ortRun2 = ortRunRepository.create(repositoryId, "revision2", jobConfigurations, null, labelsMap)

        ortRunRepository.listForRepository(repositoryId) shouldBe listOf(ortRun1, ortRun2)
    }

    "listForRepositories should apply query parameters" {
        ortRunRepository.create(repositoryId, "revision1", jobConfigurations, null, labelsMap)
        val ortRun2 = ortRunRepository.create(repositoryId, "revision2", jobConfigurations, null, labelsMap)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("revision", OrderDirection.DESCENDING)),
            limit = 1
        )

        ortRunRepository.listForRepository(repositoryId, parameters) shouldBe listOf(ortRun2)
    }

    "update should update an entry in the database" {
        val issue1 = OrtIssue(Instant.parse("2023-08-02T06:16:10Z"), "existing", "An initial issue", "WARNING")
        val issue2 = OrtIssue(Instant.parse("2023-08-02T06:17:16Z"), "new1", "An new issue", "HINT")
        val issue3 = OrtIssue(Instant.parse("2023-08-02T06:17:36Z"), "new2", "Another new issue", "ERROR")

        val label2Value = "new value for label2"
        val label3Value = "a newly added label"

        val ortRun = ortRunRepository.create(
            repositoryId,
            "revision",
            jobConfigurations,
            null,
            labelsMap,
            listOf(issue1)
        )

        val resolvedContext = "theResolvedConfigContext"
        val updateStatus = OrtRunStatus.ACTIVE.asPresent()

        val updateResult = ortRunRepository.update(
            ortRun.id,
            updateStatus,
            resolvedJobConfigurations.asPresent(),
            resolvedContext.asPresent(),
            listOf(issue2, issue3).asPresent(),
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

    "update should mark a finished run as completed" {
        val ortRun = ortRunRepository.create(
            repositoryId,
            "revision",
            jobConfigurations,
            null,
            emptyMap(),
            emptyList()
        )

        val updateResult = ortRunRepository.update(ortRun.id, status = OrtRunStatus.FINISHED.asPresent())

        assertCurrentTime(updateResult.finishedAt)
        ortRunRepository.get(ortRun.id) shouldBe updateResult
    }

    "update should mark a failed run as completed" {
        val ortRun = ortRunRepository.create(
            repositoryId,
            "revision",
            jobConfigurations,
            null,
            emptyMap(),
            emptyList()
        )

        val updateResult = ortRunRepository.update(ortRun.id, status = OrtRunStatus.FAILED.asPresent())

        assertCurrentTime(updateResult.finishedAt)
        ortRunRepository.get(ortRun.id) shouldBe updateResult
    }

    "delete should delete the database entry" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, null, labelsMap)

        ortRunRepository.delete(ortRun.id)

        ortRunRepository.listForRepository(repositoryId) shouldBe emptyList()
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
