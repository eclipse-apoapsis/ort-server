/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
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

    val labelsMap = mapOf("label1" to "label1", "label2" to "label2")

    beforeEach {
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        repositoryId = dbExtension.fixtures.repository.id
    }

    "create should create an entry in the database" {
        val revision = "revision"

        val createdOrtRun = ortRunRepository.create(repositoryId, revision, jobConfigurations, labelsMap)

        val dbEntry = ortRunRepository.get(createdOrtRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe OrtRun(
            id = createdOrtRun.id,
            index = createdOrtRun.id,
            repositoryId = repositoryId,
            revision = revision,
            createdAt = createdOrtRun.createdAt,
            config = jobConfigurations,
            status = OrtRunStatus.CREATED,
            labelsMap,
            null,
            null,
            emptyMap()
        )
    }

    "create should create sequential indexes for different repositories" {
        val otherRepository = dbExtension.fixtures.createRepository(url = "https://example.com/repo2.git")

        ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap).index shouldBe 1
        ortRunRepository.create(otherRepository.id, "revision", jobConfigurations, labelsMap).index shouldBe 1
        ortRunRepository.create(otherRepository.id, "revision", jobConfigurations, labelsMap).index shouldBe 2
        ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap).index shouldBe 2
    }

    "getByIndex should return the correct run" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap)

        ortRunRepository.getByIndex(repositoryId, ortRun.index) shouldBe ortRun
    }

    "get should return null" {
        ortRunRepository.get(1L).shouldBeNull()
    }

    "get should return the run" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap)

        ortRunRepository.get(ortRun.id) shouldBe ortRun
    }

    "listForRepositories should return all runs for a repository" {
        val ortRun1 = ortRunRepository.create(repositoryId, "revision1", jobConfigurations, labelsMap)
        val ortRun2 = ortRunRepository.create(repositoryId, "revision2", jobConfigurations, labelsMap)

        ortRunRepository.listForRepository(repositoryId) shouldBe listOf(ortRun1, ortRun2)
    }

    "listForRepositories should apply query parameters" {
        ortRunRepository.create(repositoryId, "revision1", jobConfigurations, labelsMap)
        val ortRun2 = ortRunRepository.create(repositoryId, "revision2", jobConfigurations, labelsMap)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("revision", OrderDirection.DESCENDING)),
            limit = 1
        )

        ortRunRepository.listForRepository(repositoryId, parameters) shouldBe listOf(ortRun2)
    }

    "update should update an entry in the database" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap)

        val updateStatus = OrtRunStatus.ACTIVE.asPresent()

        val updateResult = ortRunRepository.update(ortRun.id, updateStatus)

        updateResult shouldBe ortRun.copy(status = updateStatus.value)
        ortRunRepository.get(ortRun.id) shouldBe ortRun.copy(status = updateStatus.value)
    }

    "delete should delete the database entry" {
        val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations, labelsMap)

        ortRunRepository.delete(ortRun.id)

        ortRunRepository.listForRepository(repositoryId) shouldBe emptyList()
    }
})
