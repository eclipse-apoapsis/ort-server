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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation

import org.jetbrains.exposed.sql.Database

class RuleViolationServiceTest : WordSpec() {

    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures
    private lateinit var service: RuleViolationService

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures

            val ortRunService = OrtRunService(
                db,
                fixtures.advisorJobRepository,
                fixtures.advisorRunRepository,
                fixtures.analyzerJobRepository,
                fixtures.analyzerRunRepository,
                fixtures.evaluatorJobRepository,
                fixtures.evaluatorRunRepository,
                fixtures.ortRunRepository,
                fixtures.reporterJobRepository,
                fixtures.reporterRunRepository,
                fixtures.notifierJobRepository,
                fixtures.notifierRunRepository,
                fixtures.repositoryConfigurationRepository,
                fixtures.repositoryRepository,
                fixtures.resolvedConfigurationRepository,
                fixtures.scannerJobRepository,
                fixtures.scannerRunRepository,
                mockk(),
                mockk()
            )

            service = RuleViolationService(db, ortRunService)
        }

        "listForOrtRunId" should {
            "return the rule violations for the given ORT run ID" {
                val ortRun = createRuleViolationEntries()
                val results = service.listForOrtRunId(ortRun.id).data

                results shouldHaveSize 3

                with(results[0]) {

                    rule shouldBe "Rule-1"
                    license shouldBe "License-1"
                    licenseSource shouldBe "CONCLUDED"
                    severity shouldBe Severity.WARNING
                    message shouldBe "Message-1"
                    howToFix shouldBe "How_to_fix-1"

                    with(packageId) {
                        this!!.type shouldBe "Maven"
                        namespace shouldBe "org.apache.logging.log4j"
                        name shouldBe "log4j-core"
                        version shouldBe "2.14.0"
                    }
                }

                with(results[1]) {
                    rule shouldBe "Rule-2"
                    license shouldBe "License-2"
                    licenseSource shouldBe "DETECTED"
                    severity shouldBe Severity.ERROR
                    message shouldBe "Message-2"
                    howToFix shouldBe "How_to_fix-2"

                    with(packageId) {
                        this!!.type shouldBe "Maven"
                        namespace shouldBe "com.fasterxml.jackson.core"
                        name shouldBe "jackson-databind"
                        version shouldBe "2.9.6"
                    }
                }

                with(results[2]) {
                    rule shouldBe "Rule-3-no-id"
                    license shouldBe "License-3"
                    licenseSource shouldBe "DETECTED"
                    severity shouldBe Severity.HINT
                    message shouldBe "Message-3"
                    howToFix shouldBe "How_to_fix-3"
                    packageId shouldBe null
                }
            }
        }

        "countForOrtRunId" should {
            "return count for rule violations found in an ORT run" {
                val ortRun = createRuleViolationEntries()

                service.countForOrtRunIds(ortRun.id) shouldBe 3
            }

            "return count for rule violations found in ORT runs" {
                val repositoryId = fixtures.createRepository().id

                val ortRun1Id = createRuleViolationEntries(repositoryId).id
                val ortRun2Id = createRuleViolationEntries(
                    repositoryId,
                    generateRuleViolations().plus(
                        OrtRuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-1",
                            "How_to_fix-1"
                        )
                    )
                ).id

                service.countForOrtRunIds(ortRun1Id, ortRun2Id) shouldBe 4
            }
        }

        "countBySeverityForOrtRunIds" should {
            "return the counts per severity for rule violations found in ORT runs" {
                val repositoryId = fixtures.createRepository().id

                val ortRun1Id = createRuleViolationEntries(
                    repositoryId,
                    generateRuleViolations().plus(
                        OrtRuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            "CONCLUDED",
                            Severity.HINT,
                            "Message-1",
                            "How_to_fix-1"
                        )
                    )
                ).id
                val ortRun2Id = createRuleViolationEntries(
                    repositoryId,
                    generateRuleViolations().plus(
                        OrtRuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-1",
                            "How_to_fix-1"
                        )
                    )
                ).id

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRun1Id, ortRun2Id)

                severitiesToCounts.map.size shouldBe Severity.entries.size
                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.getCount(Severity.HINT) shouldBe 2
                severitiesToCounts.getCount(Severity.WARNING) shouldBe 2
                severitiesToCounts.getCount(Severity.ERROR) shouldBe 1
            }

            "return counts by severity that sum up to the count returned by countForOrtRunIds" {
                val repositoryId = fixtures.createRepository().id

                val ortRun1Id = createRuleViolationEntries(repositoryId).id
                val ortRun2Id = createRuleViolationEntries(
                    repositoryId,
                    generateRuleViolations().plus(
                        OrtRuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            "CONCLUDED",
                            Severity.WARNING,
                            "Message-1",
                            "How_to_fix-1"
                        )
                    )
                ).id

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRun1Id, ortRun2Id)
                val count = service.countForOrtRunIds(ortRun1Id, ortRun2Id)

                severitiesToCounts.map.values.sum() shouldBe count
            }

            "include counts of 0 for severities that are not found in rule violations" {
                val repositoryId = fixtures.createRepository().id
                val ortRunId = fixtures.createOrtRun(repositoryId).id

                val severitiesToCounts = service.countBySeverityForOrtRunIds(ortRunId)

                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.map.values.sum() shouldBe 0
            }
        }
    }

    private fun generateRuleViolations(): List<OrtRuleViolation> =
        listOf(
            OrtRuleViolation(
                "Rule-1",
                Identifier(
                    "Maven",
                    "org.apache.logging.log4j",
                    "log4j-core",
                    "2.14.0"
                ),
                "License-1",
                "CONCLUDED",
                Severity.WARNING,
                "Message-1",
                "How_to_fix-1"
            ),
            OrtRuleViolation(
                "Rule-2",
                Identifier(
                    "Maven",
                    "com.fasterxml.jackson.core",
                    "jackson-databind",
                    "2.9.6"
                ),
                "License-2",
                "DETECTED",
                Severity.ERROR,
                "Message-2",
                "How_to_fix-2"
            ),
            OrtRuleViolation(
                "Rule-3-no-id",
                null,
                "License-3",
                "DETECTED",
                Severity.HINT,
                "Message-3",
                "How_to_fix-3"
            )
        )

    private fun createRuleViolationEntries(
        repositoryId: Long = fixtures.createRepository().id,
        ruleViolations: List<OrtRuleViolation> = this.generateRuleViolations()
    ): OrtRun {
        val ortRun = fixtures.createOrtRun(
            repositoryId = repositoryId,
            revision = "revision",
            jobConfigurations = JobConfigurations()
        )

        val evaluatorJob = fixtures.createEvaluatorJob(
            ortRunId = ortRun.id,
            configuration = EvaluatorJobConfiguration()
        )

        ruleViolations.forEach { it.packageId?.let { identifier -> fixtures.createIdentifier(identifier) } }

        fixtures.evaluatorRunRepository.create(
            evaluatorJobId = evaluatorJob.id,
            startTime = Clock.System.now().toDatabasePrecision(),
            endTime = Clock.System.now().toDatabasePrecision(),
            violations = ruleViolations
        )

        return ortRun
    }
}
