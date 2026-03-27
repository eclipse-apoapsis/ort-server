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

package org.eclipse.apoapsis.ortserver.components.resolutions.issues

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.types.beOfType

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.resolutions.beOk
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeErr
import org.eclipse.apoapsis.ortserver.components.resolutions.shouldBeOk
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.calculateResolutionMessageHash
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolutionReason
import org.eclipse.apoapsis.ortserver.model.runs.repository.ResolutionSource
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.utils.test.Integration

class IssueResolutionServiceIntegrationTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var service: IssueResolutionService
    var repositoryId = RepositoryId(-1)

    beforeEach {
        service = IssueResolutionService(
            db = dbExtension.db,
            eventStore = IssueResolutionEventStore(dbExtension.db),
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
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    IssueResolution(
                        message = "Analyzer failed to parse manifest.",
                        messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                        reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                        comment = "Tracked upstream.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.createResolution(
                repositoryId = RepositoryId(-999),
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.RepositoryNotFound>()
            }

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should beEmpty()
            }
        }

        "fail if there is already a resolution for the given message" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.createResolution(
                repositoryId = repositoryId,
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.InvalidState>()
            }
        }

        "create and retrieve a resolution with a very long message" {
            val longMessage = "Very long analyzer failure: " + "x".repeat(10_000)

            service.createResolution(
                repositoryId = repositoryId,
                message = longMessage,
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    IssueResolution(
                        message = longMessage,
                        messageHash = calculateResolutionMessageHash(longMessage),
                        reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                        comment = "Tracked upstream.",
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
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.deleteResolutionByHash(
                repositoryId,
                calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should beEmpty()
            }
        }

        "fail if the repository does not exist" {
            service.deleteResolutionByHash(
                RepositoryId(-999),
                calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.RepositoryNotFound>()
            }
        }

        "fail if the resolution does not exist" {
            service.deleteResolutionByHash(
                repositoryId,
                calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.ResolutionNotFound>()
            }
        }

        "fail if the resolution is already deleted" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            val hash = calculateResolutionMessageHash("Analyzer failed to parse manifest.")

            service.deleteResolutionByHash(repositoryId, hash, "user") should beOk()

            service.deleteResolutionByHash(repositoryId, hash, "user").shouldBeErr {
                it should beOfType<IssueResolutionError.ResolutionNotFound>()
            }
        }
    }

    "getResolutionsForRepository()" should {
        "return all resolutions for a repository" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.createResolution(
                repositoryId = repositoryId,
                message = "Scanner produced a false positive.",
                reason = IssueResolutionReason.SCANNER_ISSUE,
                comment = "Waiting for scanner fix.",
                createdBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    IssueResolution(
                        message = "Analyzer failed to parse manifest.",
                        messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                        reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                        comment = "Tracked upstream.",
                        source = ResolutionSource.SERVER
                    ),
                    IssueResolution(
                        message = "Scanner produced a false positive.",
                        messageHash = calculateResolutionMessageHash("Scanner produced a false positive."),
                        reason = IssueResolutionReason.SCANNER_ISSUE,
                        comment = "Waiting for scanner fix.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.getResolutionsForRepository(RepositoryId(-999)).shouldBeErr {
                it should beOfType<IssueResolutionError.RepositoryNotFound>()
            }
        }
    }

    "updateResolutionByHash()" should {
        "update an existing resolution" {
            service.createResolution(
                repositoryId = repositoryId,
                message = "Analyzer failed to parse manifest.",
                reason = IssueResolutionReason.BUILD_TOOL_ISSUE,
                comment = "Tracked upstream.",
                createdBy = "user"
            ) should beOk()

            service.updateResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                reason = IssueResolutionReason.SCANNER_ISSUE,
                comment = "Scanner fix pending.",
                updatedBy = "user"
            ) should beOk()

            service.getResolutionsForRepository(repositoryId).shouldBeOk {
                it should containExactly(
                    IssueResolution(
                        message = "Analyzer failed to parse manifest.",
                        messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                        reason = IssueResolutionReason.SCANNER_ISSUE,
                        comment = "Scanner fix pending.",
                        source = ResolutionSource.SERVER
                    )
                )
            }
        }

        "fail if the repository does not exist" {
            service.updateResolutionByHash(
                repositoryId = RepositoryId(-999),
                messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                reason = IssueResolutionReason.SCANNER_ISSUE,
                comment = "Scanner fix pending.",
                updatedBy = "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.RepositoryNotFound>()
            }
        }

        "fail if the resolution does not exist" {
            service.updateResolutionByHash(
                repositoryId = repositoryId,
                messageHash = calculateResolutionMessageHash("Analyzer failed to parse manifest."),
                reason = IssueResolutionReason.SCANNER_ISSUE,
                comment = "Scanner fix pending.",
                updatedBy = "user"
            ).shouldBeErr {
                it should beOfType<IssueResolutionError.ResolutionNotFound>()
            }
        }
    }
})
