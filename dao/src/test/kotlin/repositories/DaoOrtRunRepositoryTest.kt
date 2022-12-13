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

package org.ossreviewtoolkit.server.dao.test.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoOrtRunRepositoryTest : StringSpec() {
    private val ortRunRepository = DaoOrtRunRepository()

    private lateinit var fixtures: Fixtures
    private var repositoryId = -1L

    private val jobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = true
        )
    )

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
                repositoryId = fixtures.repository.id
            }
        )

        "create should create an entry in the database" {
            val revision = "revision"

            val createdOrtRun = ortRunRepository.create(repositoryId, revision, jobConfigurations)

            val dbEntry = ortRunRepository.get(createdOrtRun.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe OrtRun(
                id = createdOrtRun.id,
                index = createdOrtRun.id,
                repositoryId = repositoryId,
                revision = revision,
                createdAt = createdOrtRun.createdAt,
                jobs = jobConfigurations,
                status = OrtRunStatus.CREATED
            )
        }

        "create should create sequential indexes for different repositories" {
            val otherRepository = fixtures.createRepository(url = "https://example.com/repo2.git")

            ortRunRepository.create(repositoryId, "revision", jobConfigurations).index shouldBe 1
            ortRunRepository.create(otherRepository.id, "revision", jobConfigurations).index shouldBe 1
            ortRunRepository.create(otherRepository.id, "revision", jobConfigurations).index shouldBe 2
            ortRunRepository.create(repositoryId, "revision", jobConfigurations).index shouldBe 2
        }

        "getByIndex should return the correct run" {
            val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations)

            ortRunRepository.getByIndex(repositoryId, ortRun.index) shouldBe ortRun
        }

        "listForRepositories should return all runs for a repository" {
            val ortRun1 = ortRunRepository.create(repositoryId, "revision1", jobConfigurations)
            val ortRun2 = ortRunRepository.create(repositoryId, "revision2", jobConfigurations)

            ortRunRepository.listForRepository(repositoryId) shouldBe listOf(ortRun1, ortRun2)
        }

        "update should update an entry in the database" {
            val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations)

            val updateStatus = OrtRunStatus.ACTIVE.asPresent()

            val updateResult = ortRunRepository.update(ortRun.id, updateStatus)

            updateResult shouldBe ortRun.copy(status = updateStatus.value)
            ortRunRepository.get(ortRun.id) shouldBe ortRun.copy(status = updateStatus.value)
        }

        "delete should delete the database entry" {
            val ortRun = ortRunRepository.create(repositoryId, "revision", jobConfigurations)

            ortRunRepository.delete(ortRun.id)

            ortRunRepository.listForRepository(repositoryId) shouldBe emptyList()
        }
    }
}
