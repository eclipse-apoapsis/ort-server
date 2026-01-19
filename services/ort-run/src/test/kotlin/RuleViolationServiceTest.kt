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
import io.kotest.matchers.collections.shouldBeSingleton
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
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.PackageCurationProviderConfig
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolationFilters
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCuration
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution

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
                    licenseSources shouldBe setOf("CONCLUDED")
                    severity shouldBe Severity.WARNING
                    message shouldBe "Message-1"
                    howToFix shouldBe "How_to_fix-1"

                    with(id) {
                        this!!.type shouldBe "Maven"
                        namespace shouldBe "org.apache.logging.log4j"
                        name shouldBe "log4j-core"
                        version shouldBe "2.14.0"
                    }
                }

                with(results[1]) {
                    rule shouldBe "Rule-2"
                    license shouldBe "License-2"
                    licenseSources shouldBe setOf("DETECTED")
                    severity shouldBe Severity.ERROR
                    message shouldBe "Message-2"
                    howToFix shouldBe "How_to_fix-2"

                    with(id) {
                        this!!.type shouldBe "Maven"
                        namespace shouldBe "com.fasterxml.jackson.core"
                        name shouldBe "jackson-databind"
                        version shouldBe "2.9.6"
                    }
                }

                with(results[2]) {
                    rule shouldBe "Rule-3-no-id"
                    license shouldBe "License-3"
                    licenseSources shouldBe setOf("CONCLUDED", "DECLARED")
                    severity shouldBe Severity.HINT
                    message shouldBe "Message-3"
                    howToFix shouldBe "How_to_fix-3"
                    id shouldBe null
                }
            }

            "return filtered rule violations" {
                val ruleViolations = generateRuleViolations()
                val ortRun = createRuleViolationEntries(ruleViolations = ruleViolations)

                val resolution = RuleViolationResolution(
                    message = "Message-1",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "This is resolved"
                )

                // Match violations to resolutions by pattern and store
                val violationToResolutions = ruleViolations.associateWith { violation ->
                    listOf(resolution).filter { it.message.toRegex().containsMatchIn(violation.message) }
                }.filterValues { it.isNotEmpty() }

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(ruleViolations = violationToResolutions)
                )

                val resultsResolved = service.listForOrtRunId(
                    ortRunId = ortRun.id,
                    ruleViolationFilter = RuleViolationFilters(resolved = true)
                ).data

                val resultsUnresolved = service.listForOrtRunId(
                    ortRunId = ortRun.id,
                    ruleViolationFilter = RuleViolationFilters(resolved = false)
                ).data

                resultsResolved.shouldBeSingleton {
                    it.rule shouldBe "Rule-1"
                    it.resolutions.shouldBeSingleton { resolution ->
                        resolution.message shouldBe "Message-1"
                    }
                }

                resultsUnresolved shouldHaveSize 2
                resultsUnresolved[0].rule shouldBe "Rule-2"
                resultsUnresolved[1].rule shouldBe "Rule-3-no-id"
            }

            "return purl for rule violations that stemmed from packages" {
                val ortRun = fixtures.createOrtRun(fixtures.createRepository().id)

                val analyzerJob = fixtures.createAnalyzerJob(ortRun.id)

                val pkg1 = fixtures.generatePackage(
                    Identifier("Maven", "org.apache.logging.log4j", "log4j-core", "2.14.0")
                )
                val pkg2 = fixtures.generatePackage(
                    Identifier("Maven", "com.fasterxml.jackson.core", "jackson-databind", "2.9.6")
                )
                val proj = fixtures.getProject()

                fixtures.createAnalyzerRun(analyzerJob.id, setOf(proj), setOf(pkg1, pkg2))

                val curations = ResolvedPackageCurations(
                    provider = PackageCurationProviderConfig("test"),
                    curations = listOf(
                        PackageCuration(
                            id = pkg1.identifier,
                            data = PackageCurationData(purl = "curated")
                        )
                    )
                )

                fixtures.resolvedConfigurationRepository.addPackageCurations(
                    ortRun.id,
                    listOf(curations)
                )

                val ruleViolations = generateRuleViolations() + listOf(
                    RuleViolation(
                        "Rule-4-project-id",
                        proj.identifier,
                        "License-4",
                        setOf("DECLARED"),
                        Severity.WARNING,
                        "Message-4",
                        "How_to_fix-4"
                    )
                )

                val evaluatorJob = fixtures.createEvaluatorJob(
                    ortRunId = ortRun.id,
                    configuration = EvaluatorJobConfiguration()
                )

                fixtures.evaluatorRunRepository.create(
                    evaluatorJobId = evaluatorJob.id,
                    startTime = Clock.System.now().toDatabasePrecision(),
                    endTime = Clock.System.now().toDatabasePrecision(),
                    violations = ruleViolations
                )

                val results = service.listForOrtRunId(ortRun.id).data

                results shouldHaveSize 4

                results[0].rule shouldBe "Rule-1"
                results[0].purl shouldBe "curated"

                results[1].rule shouldBe "Rule-2"
                results[1].purl shouldBe pkg2.purl

                results[2].rule shouldBe "Rule-3-no-id"
                results[2].purl shouldBe null

                results[3].rule shouldBe "Rule-4-project-id"
                results[3].purl shouldBe null
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
                        RuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            setOf("CONCLUDED"),
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
                        RuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            setOf("CONCLUDED"),
                            Severity.HINT,
                            "Message-1",
                            "How_to_fix-1"
                        )
                    )
                ).id
                val ortRun2Id = createRuleViolationEntries(
                    repositoryId,
                    generateRuleViolations().plus(
                        RuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            setOf("CONCLUDED"),
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
                        RuleViolation(
                            "Rule-1",
                            Identifier(
                                "Maven",
                                "org.apache.logging.log4j",
                                "log4j-api",
                                "2.14.0"
                            ),
                            "License-1",
                            setOf("CONCLUDED"),
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

        "countUnresolvedForOrtRunIds" should {
            "return all violations when none are resolved" {
                val ortRun = createRuleViolationEntries()

                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 3
            }

            "return count of unresolved violations when some are resolved" {
                val repositoryId = fixtures.createRepository().id
                val violations = generateRuleViolations()
                val ortRun = createRuleViolationEntries(repositoryId, violations)

                // Total violations = 3
                service.countForOrtRunIds(ortRun.id) shouldBe 3

                // Resolve one violation
                val resolution = RuleViolationResolution(
                    message = "Message-1",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "This is resolved"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        ruleViolations = mapOf(violations[0] to listOf(resolution))
                    )
                )

                // Unresolved should be 2 (total 3 - resolved 1)
                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 2
            }

            "return zero when all violations are resolved" {
                val repositoryId = fixtures.createRepository().id
                val violations = generateRuleViolations()
                val ortRun = createRuleViolationEntries(repositoryId, violations)

                val resolution1 = RuleViolationResolution(
                    message = "Message-1",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )
                val resolution2 = RuleViolationResolution(
                    message = "Message-2",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )
                val resolution3 = RuleViolationResolution(
                    message = "Message-3",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        ruleViolations = mapOf(
                            violations[0] to listOf(resolution1),
                            violations[1] to listOf(resolution2),
                            violations[2] to listOf(resolution3)
                        )
                    )
                )

                service.countUnresolvedForOrtRunIds(ortRun.id) shouldBe 0
            }

            "return count of unresolved violations found in ORT runs" {
                val repositoryId = fixtures.createRepository().id

                val violations1 = generateRuleViolations()
                val violations2 = listOf(
                    RuleViolation(
                        "Rule-4",
                        Identifier("Maven", "org.example", "lib", "1.0"),
                        "License-4",
                        setOf("CONCLUDED"),
                        Severity.ERROR,
                        "Message-4",
                        "How_to_fix-4"
                    )
                )

                val ortRun1 = createRuleViolationEntries(repositoryId, violations1)
                val ortRun2 = createRuleViolationEntries(repositoryId, violations2)

                // Resolve one violation in ortRun1
                val resolution = RuleViolationResolution(
                    message = "Message-1",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun1.id,
                    ResolvedItemsResult(
                        ruleViolations = mapOf(violations1[0] to listOf(resolution))
                    )
                )

                // Total unresolved across both runs should be 3 (2 from ortRun1 + 1 from ortRun2)
                service.countUnresolvedForOrtRunIds(ortRun1.id, ortRun2.id) shouldBe 3
            }
        }

        "countUnresolvedBySeverityForOrtRunIds" should {
            "return counts by severity for unresolved violations" {
                val repositoryId = fixtures.createRepository().id
                val violations = generateRuleViolations()
                val ortRun = createRuleViolationEntries(repositoryId, violations)

                // Resolve the ERROR violation (Message-2)
                val resolution = RuleViolationResolution(
                    message = "Message-2",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        ruleViolations = mapOf(violations[1] to listOf(resolution))
                    )
                )

                val severitiesToCounts = service.countUnresolvedBySeverityForOrtRunIds(ortRun.id)

                severitiesToCounts.map.keys shouldContainExactlyInAnyOrder Severity.entries
                severitiesToCounts.getCount(Severity.ERROR) shouldBe 0
                severitiesToCounts.getCount(Severity.WARNING) shouldBe 1
                severitiesToCounts.getCount(Severity.HINT) shouldBe 1
            }

            "return sum that equals countUnresolvedForOrtRunIds" {
                val repositoryId = fixtures.createRepository().id
                val violations = generateRuleViolations()
                val ortRun = createRuleViolationEntries(repositoryId, violations)

                // Resolve the WARNING violation
                val resolution = RuleViolationResolution(
                    message = "Message-1",
                    reason = "CANT_FIX_EXCEPTION",
                    comment = "Resolved"
                )

                fixtures.resolvedConfigurationRepository.addResolutions(
                    ortRun.id,
                    ResolvedItemsResult(
                        ruleViolations = mapOf(violations[0] to listOf(resolution))
                    )
                )

                val severitiesToCounts = service.countUnresolvedBySeverityForOrtRunIds(ortRun.id)
                val unresolvedCount = service.countUnresolvedForOrtRunIds(ortRun.id)

                severitiesToCounts.map.values.sum() shouldBe unresolvedCount
            }
        }
    }

    private fun generateRuleViolations(): List<RuleViolation> =
        listOf(
            RuleViolation(
                "Rule-1",
                Identifier(
                    "Maven",
                    "org.apache.logging.log4j",
                    "log4j-core",
                    "2.14.0"
                ),
                "License-1",
                setOf("CONCLUDED"),
                Severity.WARNING,
                "Message-1",
                "How_to_fix-1"
            ),
            RuleViolation(
                "Rule-2",
                Identifier(
                    "Maven",
                    "com.fasterxml.jackson.core",
                    "jackson-databind",
                    "2.9.6"
                ),
                "License-2",
                setOf("DETECTED"),
                Severity.ERROR,
                "Message-2",
                "How_to_fix-2"
            ),
            RuleViolation(
                "Rule-3-no-id",
                null,
                "License-3",
                setOf("CONCLUDED", "DECLARED"),
                Severity.HINT,
                "Message-3",
                "How_to_fix-3"
            )
        )

    private fun createRuleViolationEntries(
        repositoryId: Long = fixtures.createRepository().id,
        ruleViolations: List<RuleViolation> = generateRuleViolations()
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

        ruleViolations.forEach { it.id?.let { identifier -> fixtures.createIdentifier(identifier) } }

        fixtures.evaluatorRunRepository.create(
            evaluatorJobId = evaluatorJob.id,
            startTime = Clock.System.now().toDatabasePrecision(),
            endTime = Clock.System.now().toDatabasePrecision(),
            violations = ruleViolations
        )

        return ortRun
    }
}
