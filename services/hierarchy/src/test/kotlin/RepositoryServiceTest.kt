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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database

class RepositoryServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
    }

    fun createService(authorizationService: AuthorizationService = mockk()) = RepositoryService(
        db,
        dbExtension.fixtures.ortRunRepository,
        dbExtension.fixtures.repositoryRepository,
        dbExtension.fixtures.analyzerJobRepository,
        dbExtension.fixtures.advisorJobRepository,
        dbExtension.fixtures.scannerJobRepository,
        dbExtension.fixtures.evaluatorJobRepository,
        dbExtension.fixtures.reporterJobRepository,
        dbExtension.fixtures.notifierJobRepository,
        authorizationService
    )

    "deleteRepository" should {
        "delete Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { deleteRepositoryPermissions(any()) } just runs
                coEvery { deleteRepositoryRoles(any()) } just runs
            }
            val service = createService(authorizationService)

            service.deleteRepository(fixtures.repository.id)

            coVerify(exactly = 1) {
                authorizationService.deleteRepositoryPermissions(fixtures.repository.id)
                authorizationService.deleteRepositoryRoles(fixtures.repository.id)
            }
        }
    }

    "getJobs" should {
        "return the existing jobs" {
            val service = createService()

            service.getJobs(fixtures.repository.id, fixtures.ortRun.index) shouldNotBeNull {
                analyzer should beNull()
                advisor should beNull()
                scanner should beNull()
                evaluator should beNull()
                reporter should beNull()
            }

            val analyzerJob = fixtures.createAnalyzerJob()
            val advisorJob = fixtures.createAdvisorJob()

            service.getJobs(fixtures.repository.id, fixtures.ortRun.index).let { jobs ->
                jobs.shouldNotBeNull()
                jobs.analyzer shouldBe analyzerJob
                jobs.advisor shouldBe advisorJob
                jobs.scanner should beNull()
                jobs.evaluator should beNull()
                jobs.reporter should beNull()
            }

            val scannerJob = fixtures.createScannerJob()
            val evaluatorJob = fixtures.createEvaluatorJob()
            val reporterJob = fixtures.createReporterJob()

            service.getJobs(fixtures.repository.id, fixtures.ortRun.index) shouldNotBeNull {
                analyzer shouldBe analyzerJob
                advisor shouldBe advisorJob
                scanner shouldBe scannerJob
                evaluator shouldBe evaluatorJob
                reporter shouldBe reporterJob
            }
        }

        "return null if the ORT run does not exist" {
            val service = createService()

            service.getJobs(fixtures.repository.id, -1L) should beNull()
        }
    }

    "addUserToGroup" should {
        "throw an exception if the organization does not exist" {
            val service = createService()

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                createService(authorizationService)
            ) {
                coEvery { getRepository(any()) } returns Repository(1, 1, 1, RepositoryType.GIT, "url")
            }

            shouldThrow<ResourceNotFoundException> {
                service.addUserToGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                createService(authorizationService)
            ) {
                coEvery { getRepository(any()) } returns Repository(1, 1, 1, RepositoryType.GIT, "url")
            }

            service.addUserToGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.addUserToGroup(
                    "username",
                    "REPOSITORY_1_READERS"
                )
            }
        }
    }

    "removeUsersFromGroup" should {
        "throw an exception if the organization does not exist" {
            val service = createService()

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "readers")
            }
        }

        "throw an exception if the group does not exist" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { addUserToGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                createService(authorizationService)
            ) {
                coEvery { getRepository(any()) } returns Repository(1, 1, 1, RepositoryType.GIT, "url")
            }

            shouldThrow<ResourceNotFoundException> {
                service.removeUserFromGroup("username", 1, "viewers")
            }
        }

        "generate the Keycloak group name" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { removeUserFromGroup(any(), any()) } just runs
            }

            // Create a spy of the service to partially mock it
            val service = spyk(
                createService(authorizationService)
            ) {
                coEvery { getRepository(any()) } returns Repository(1, 1, 1, RepositoryType.GIT, "url")
            }

            service.removeUserFromGroup("username", 1, "readers")

            coVerify(exactly = 1) {
                authorizationService.removeUserFromGroup(
                    "username",
                    "REPOSITORY_1_READERS"
                )
            }
        }
    }

    "getLatestOrtRunIdWithAnalyzerJobInFinalState" should {
        "return the ID of the latest ORT run of the repository where the analyzer job has finished or failed" {
            val service = createService()

            val repoId = fixtures.createRepository().id

            val run1Id = fixtures.createOrtRun(repoId).id
            val analyzerJob1Id = fixtures.createAnalyzerJob(run1Id).id
            fixtures.analyzerJobRepository.update(
                id = analyzerJob1Id,
                status = JobStatus.FINISHED.asPresent()
            )

            val run2Id = fixtures.createOrtRun(repoId).id
            val analyzerJob2Id = fixtures.createAnalyzerJob(run2Id).id
            fixtures.analyzerJobRepository.update(
                id = analyzerJob2Id,
                status = JobStatus.FAILED.asPresent()
            )

            val run3Id = fixtures.createOrtRun(repoId).id
            fixtures.createAnalyzerJob(run3Id).id

            val ortRunId = service.getLatestOrtRunIdWithAnalyzerJobInFinalState(repoId)

            ortRunId shouldBe run2Id
        }
    }

    "getLatestOrtRunIdWithSuccessfulAnalyzerJob" should {
        "return the ID of the latest ORT run of the repository that has a successful analyzer job" {
            val service = createService()

            val repoId = fixtures.createRepository().id

            val run1Id = fixtures.createOrtRun(repoId).id
            val analyzerJob1Id = fixtures.createAnalyzerJob(run1Id).id
            fixtures.analyzerJobRepository.update(
                id = analyzerJob1Id,
                status = JobStatus.FINISHED.asPresent()
            )

            val run2Id = fixtures.createOrtRun(repoId).id
            val analyzerJob2Id = fixtures.createAnalyzerJob(run2Id).id
            fixtures.analyzerJobRepository.update(
                id = analyzerJob2Id,
                status = JobStatus.FINISHED_WITH_ISSUES.asPresent()
            )

            val run3Id = fixtures.createOrtRun(repoId).id
            val analyzerJob3Id = fixtures.createAnalyzerJob(run3Id).id
            fixtures.analyzerJobRepository.update(
                id = analyzerJob3Id,
                status = JobStatus.FAILED.asPresent()
            )

            val ortRunId = service.getLatestOrtRunIdWithSuccessfulAnalyzerJob(repoId)

            ortRunId shouldBe run2Id
        }
    }

    "getLatestOrtRunIdWithSuccessfulAdvisorJob" should {
        "return the ID of the latest ORT run of the repository that has a successful advisor job" {
            val service = createService()

            val repoId = fixtures.createRepository().id

            val run1Id = fixtures.createOrtRun(repoId).id
            val advisorJob1Id = fixtures.createAdvisorJob(run1Id).id
            fixtures.advisorJobRepository.update(
                id = advisorJob1Id,
                status = JobStatus.FINISHED_WITH_ISSUES.asPresent()
            )

            val run2Id = fixtures.createOrtRun(repoId).id
            val advisorJob2Id = fixtures.createAdvisorJob(run2Id).id
            fixtures.advisorJobRepository.update(
                id = advisorJob2Id,
                status = JobStatus.FINISHED.asPresent()
            )

            val run3Id = fixtures.createOrtRun(repoId).id
            val advisorJob3Id = fixtures.createAdvisorJob(run3Id).id
            fixtures.advisorJobRepository.update(
                id = advisorJob3Id,
                status = JobStatus.FAILED.asPresent()
            )

            val ortRunId = service.getLatestOrtRunIdWithSuccessfulAdvisorJob(repositoryId = repoId)
            ortRunId shouldBe run2Id
        }
    }

    "getLatestOrtRunIdWithSuccessfulEvaluatorJob" should {
        "return the ID of the latest ORT run of the repository that has a successful evaluator job" {
            val service = createService()

            val repoId = fixtures.createRepository().id

            val run1Id = fixtures.createOrtRun(repoId).id
            val evaluatorJob1Id = fixtures.createEvaluatorJob(run1Id).id
            fixtures.evaluatorJobRepository.update(
                id = evaluatorJob1Id,
                status = JobStatus.FINISHED.asPresent()
            )

            val run2Id = fixtures.createOrtRun(repoId).id
            val evaluatorJob2Id = fixtures.createEvaluatorJob(run2Id).id
            fixtures.evaluatorJobRepository.update(
                id = evaluatorJob2Id,
                status = JobStatus.FAILED.asPresent()
            )

            val run3Id = fixtures.createOrtRun(repoId).id
            fixtures.createEvaluatorJob(run3Id).id

            val ortRunId = service.getLatestOrtRunIdWithSuccessfulEvaluatorJob(repositoryId = repoId)
            ortRunId shouldBe run1Id
        }
    }
})
