/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.resolutions.ruleviolations

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.resolutions.beOk
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeErr
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeOk
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolutionReason
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class RuleViolationResolutionServiceIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var service: RuleViolationResolutionService
    var repositoryId = RepositoryId(-1)

    beforeEach {
        service = RuleViolationResolutionService(
            db = dbExtension.db,
            eventStore = RuleViolationResolutionEventStore(dbExtension.db),
            repositoryService = RepositoryService(
                db = dbExtension.db,
                ortRunRepository = dbExtension.fixtures.ortRunRepository,
                repositoryRepository = dbExtension.fixtures.repositoryRepository,
                analyzerJobRepository = dbExtension.fixtures.analyzerJobRepository,
                advisorJobRepository = dbExtension.fixtures.advisorJobRepository,
                scannerJobRepository = dbExtension.fixtures.scannerJobRepository,
                evaluatorJobRepository = dbExtension.fixtures.evaluatorJobRepository,
                reporterJobRepository = dbExtension.fixtures.reporterJobRepository,
                notifierJobRepository = dbExtension.fixtures.notifierJobRepository,
                authorizationService = mockk()
            )
        )

        repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
    }

    "createResolution()" should {
        "create a resolution for a repository" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ).shouldBeOk {
                it.repositoryId shouldBe repositoryId
                it.message shouldBe "License finding is covered by an exception."
                it.reason shouldBe RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION
                it.comment shouldBe "Reviewed by legal."
                it.isDeleted shouldBe false
                it.version shouldBe 1
            }

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    RuleViolationResolution(
                        message = "License finding is covered by an exception.",
                        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                        comment = "Reviewed by legal.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.createResolution(
                repositoryId = RepositoryId(-999),
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.RepositoryNotFound>()
            }

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should beEmpty()
            }
        }

        "fail if there is already a resolution for the given message" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.InvalidState>()
            }
        }

        "create and retrieve a resolution with a very long message" {
            val longMessage = "Very long rule violation: " + "x".repeat(10_000)

            service.createResolution(
                repositoryId = repositoryId,
                message = longMessage,
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    RuleViolationResolution(
                        message = longMessage,
                        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                        comment = "Reviewed by legal.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }
    }

    "deleteResolutionByHash()" should {
        "delete an existing resolution" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            service.deleteResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                deletedBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should beEmpty()
            }
        }

        "fail if the repository does not exist" {
            service.deleteResolutionByHash(
                repositoryId = RepositoryId(-999),
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                deletedBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.RepositoryNotFound>()
            }
        }

        "fail if the resolution does not exist" {
            service.deleteResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                deletedBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.ResolutionNotFound>()
            }
        }

        "fail if the resolution is already deleted" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            val hash = calculateResolutionMessageHash("License finding is covered by an exception.")

            service.deleteResolutionByHash(repositoryId, hash, "user") should beOk()

            service.deleteResolutionByHash(repositoryId, hash, "user").shouldBeErr {
                it should beOfType<RuleViolationResolutionError.ResolutionNotFound>()
            }
        }
    }

    "getResolutionsForRepository()" should {
        "return all resolutions for a repository" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            service.createResolution(
                repositoryId = repositoryId,
                message = "Artifact is dynamically linked only.",
                reason = RuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION,
                comment = "Confirmed by packaging review.",
                createdBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    RuleViolationResolution(
                        message = "License finding is covered by an exception.",
                        reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                        comment = "Reviewed by legal.",
                        source = ResolutionSource.SERVER
                    ),
                    RuleViolationResolution(
                        message = "Artifact is dynamically linked only.",
                        reason = RuleViolationResolutionReason.DYNAMIC_LINKAGE_EXCEPTION,
                        comment = "Confirmed by packaging review.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.getResolutionsForRepository(RepositoryId(-999)).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.RepositoryNotFound>()
            }
        }
    }

    "updateResolutionByHash()" should {
        "update an existing resolution" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "License finding is covered by an exception.",
                reason = RuleViolationResolutionReason.EXAMPLE_OF_EXCEPTION,
                comment = "Reviewed by legal.",
                createdBy = "user"
            ) should beOk()

            service.updateResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                reason = RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION,
                comment = "License proof archived.",
                updatedBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    RuleViolationResolution(
                        message = "License finding is covered by an exception.",
                        reason = RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION,
                        comment = "License proof archived.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.updateResolutionByHash(
                repositoryId = RepositoryId(-999),
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                reason = RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION,
                comment = "License proof archived.",
                updatedBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.RepositoryNotFound>()
            }
        }

        "fail if the resolution does not exist" {
            service.updateResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("License finding is covered by an exception."),
                reason = RuleViolationResolutionReason.LICENSE_ACQUIRED_EXCEPTION,
                comment = "License proof archived.",
                updatedBy = "user"
            ).shouldBeErr {
                it should beOfType<RuleViolationResolutionError.ResolutionNotFound>()
            }
        }
    }
})
