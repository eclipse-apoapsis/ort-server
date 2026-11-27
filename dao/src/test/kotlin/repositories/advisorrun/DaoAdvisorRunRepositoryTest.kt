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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.random.Random
import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Criticality
import org.eclipse.apoapsis.ortserver.model.runs.advisor.HealthMetric
import org.eclipse.apoapsis.ortserver.model.runs.advisor.HealthSummary
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.jetbrains.exposed.v1.jdbc.selectAll

class DaoAdvisorRunRepositoryTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    lateinit var advisorRunRepository: DaoAdvisorRunRepository
    lateinit var fixtures: Fixtures

    var advisorJobId = -1L

    beforeEach {
        advisorRunRepository = dbExtension.fixtures.advisorRunRepository
        fixtures = Fixtures(dbExtension.db)

        advisorJobId = fixtures.advisorJob.id
    }

    "create" should {
        "create an entry in the database" {
            val createdAdvisorRun = advisorRunRepository.create(advisorJobId, advisorRun)

            val dbEntry = advisorRunRepository.get(createdAdvisorRun.id)

            dbEntry shouldBe advisorRun.copy(id = createdAdvisorRun.id, advisorJobId = advisorJobId)
        }

        "be able to store multiple runs" {
            val ortRun2 = fixtures.createOrtRun(fixtures.repository.id)
            val ortRun3 = fixtures.createOrtRun(fixtures.repository.id)

            val advisorJob2 = fixtures.createAdvisorJob(ortRun2.id)
            val advisorJob3 = fixtures.createAdvisorJob(ortRun3.id)

            val createdAdvisorRun1 = advisorRunRepository.create(advisorJobId, advisorRun)
            val createdAdvisorRun2 = advisorRunRepository.create(advisorJob2.id, advisorRun)
            val createdAdvisorRun3 = advisorRunRepository.create(advisorJob3.id, advisorRun)

            createdAdvisorRun1 shouldBe advisorRun.copy(id = createdAdvisorRun1.id, advisorJobId = advisorJobId)
            createdAdvisorRun2 shouldBe advisorRun.copy(id = createdAdvisorRun2.id, advisorJobId = advisorJob2.id)
            createdAdvisorRun3 shouldBe advisorRun.copy(id = createdAdvisorRun3.id, advisorJobId = advisorJob3.id)
        }

        "store health metrics and summaries" {
            val createdAdvisorRun = advisorRunRepository.create(advisorJobId, healthAdvisorRun)

            advisorRunRepository.get(createdAdvisorRun.id) shouldBe
                    healthAdvisorRun.copy(id = createdAdvisorRun.id, advisorJobId = advisorJobId)
        }

        "store the definition of a metric only once" {
            val advisorJob2 = fixtures.createAdvisorJob(fixtures.createOrtRun(fixtures.repository.id).id)
            val otherValues = healthAdvisorRun.copy(
                results = mapOf(
                    identifier to listOf(
                        scorecardResult.copy(
                            healthMetrics = listOf(
                                codeReviewMetric.copy(value = 3.0, criticality = Criticality.CRITICAL),
                                elephantFactorMetric.copy(value = 1.0, referenceValue = 2.0)
                            ),
                            healthSummary = null
                        )
                    )
                )
            )

            advisorRunRepository.create(advisorJobId, healthAdvisorRun)
            advisorRunRepository.create(advisorJob2.id, otherValues)

            dbExtension.db.transaction { HealthMetricDefinitionsTable.selectAll().count() } shouldBe 4
        }
    }

    "get" should {
        "return null if the advisor run does not exist" {
            advisorRunRepository.get(advisorJobId) should beNull()
        }
    }

    "getByJobId" should {
        "return the correct advisor run" {
            val createdAdvisorRun = advisorRunRepository.create(advisorJobId, advisorRun)

            advisorRunRepository.getByJobId(advisorJobId) shouldBe
                    advisorRun.copy(id = createdAdvisorRun.id, advisorJobId = advisorJobId)
        }

        "return null if there is no run for the job id" {
            advisorRunRepository.getByJobId(advisorJobId) should beNull()
        }
    }
})

private suspend fun DaoAdvisorRunRepository.create(advisorJobId: Long, advisorRun: AdvisorRun) = create(
    advisorJobId = advisorJobId,
    startTime = advisorRun.startTime,
    endTime = advisorRun.endTime,
    environment = advisorRun.environment,
    config = advisorRun.config,
    providerIssues = advisorRun.providerIssues,
    results = advisorRun.results.mapValues { (_, results) -> results.map { it.withPlainIssues() } }
)

private val variables = mapOf(
    "SHELL" to "/bin/bash",
    "TERM" to "xterm-256color"
)

private val environment = Environment(
    ortVersion = "1.0",
    javaVersion = "11.0.16",
    os = "Linux",
    processors = 8,
    maxMemory = 8321499136,
    variables = variables
)

private val advisorConfiguration = AdvisorConfiguration(
    config = mapOf(
        "NexusIQ" to PluginConfig(
            options = mapOf(
                "serverUrl" to "https://example.org/nexus",
                "browseUrl" to "https://example.org/nexus/browse"
            ),
            secrets = mapOf(
                "username" to "user",
                "password" to "pass"
            )
        ),
        "OSV" to PluginConfig(
            options = mapOf("serverUrl" to "https://google.com/osv"),
            secrets = emptyMap()
        ),
        "VulnerableCode" to PluginConfig(
            options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
            secrets = mapOf("apiKey" to "key")
        )
    )
)

private val identifier = Identifier(
    type = "type",
    namespace = "namespace",
    name = "name",
    version = "version"
)

private val otherIdentifier = Identifier(
    type = "otherType",
    namespace = "otherNamespace",
    name = "otherName",
    version = "otherVersion"
)

private val issue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "NexusIq",
    message = "message",
    severity = Severity.ERROR,
    identifier = identifier,
    worker = "advisor"
)

private val otherIssue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "OSV",
    message = "otherMessage",
    severity = Severity.ERROR,
    identifier = otherIdentifier,
    worker = "advisor"
)

private val providerIssue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "Advisor",
    message = "Failed to create provider 'OSS Index'",
    severity = Severity.ERROR,
    worker = "advisor"
)

private val vulnerability = Vulnerability(
    externalId = "external-id",
    summary = "summary",
    description = "description",
    references = listOf(
        VulnerabilityReference(
            url = "url",
            scoringSystem = "scoring-system",
            severity = "ERROR",
            score = 8.3f,
            vector = "vector"
        )
    ),
    firstFixedVersions = setOf("fixedVersion")
)

private val advisorResult = AdvisorResult(
    advisorName = "NexusIQ",
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    issues = listOf(issue),
    vulnerabilities = listOf(vulnerability)
)

private val otherAdvisorResult = AdvisorResult(
    advisorName = "OSV",
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    issues = listOf(otherIssue),
    vulnerabilities = emptyList()
)

private val advisorRun = AdvisorRun(
    id = -1L,
    advisorJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = advisorConfiguration,
    providerIssues = setOf(providerIssue),
    results = mapOf(identifier to listOf(advisorResult), otherIdentifier to listOf(otherAdvisorResult))
)

private val codeReviewMetric = HealthMetric(
    name = "Code-Review",
    documentation = "Determines if the project requires human code review before pull requests are merged.",
    documentationLink = "https://example.org/checks#code-review",
    minValue = 0.0,
    maxValue = 10.0,
    higherIsBetter = true,
    value = 8.0,
    criticality = Criticality.LOW,
    reason = "Found 8/10 approved changesets.",
    details = listOf("Info: first detail", "Warn: second detail")
)

private val packagingMetric = HealthMetric(
    name = "Packaging",
    // Longer than PostgreSQL allows for an index entry, which the hash in the unique index of the definitions avoids.
    documentation = Random(0).nextBytes(16384).toHexString(),
    minValue = 0.0,
    maxValue = 10.0,
    higherIsBetter = true,
    value = null,
    reason = "packaging workflow not detected"
)

private val elephantFactorMetric = HealthMetric(
    name = "Elephant Factor",
    minValue = 0.0,
    higherIsBetter = true,
    value = 3.0,
    referenceValue = 2.5,
    criticality = Criticality.LOW
)

private val scorecardResult = AdvisorResult(
    advisorName = "Scorecard",
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    issues = emptyList(),
    vulnerabilities = emptyList(),
    healthMetrics = listOf(codeReviewMetric, packagingMetric),
    healthSummary = HealthSummary(
        overallScore = HealthMetric(
            name = "Aggregate-Score",
            minValue = 0.0,
            maxValue = 10.0,
            higherIsBetter = true,
            value = 4.2,
            criticality = Criticality.HIGH
        ),
        analyzedAt = Clock.System.now().toDatabasePrecision(),
        analyzedRevision = "479dbae",
        toolVersion = "v5.7.0"
    )
)

private val healthAdvisorRun = advisorRun.copy(
    results = mapOf(
        identifier to listOf(
            scorecardResult,
            scorecardResult.copy(
                advisorName = "Crossd",
                healthMetrics = listOf(elephantFactorMetric),
                healthSummary = null
            )
        ),
        otherIdentifier to listOf(
            scorecardResult.copy(healthMetrics = listOf(codeReviewMetric), healthSummary = HealthSummary())
        )
    )
)

/** Return a copy of this [AdvisorResult] with the issues converted to plain issues. */
private fun AdvisorResult.withPlainIssues(): AdvisorResult =
    copy(issues = issues.map { it.toPlain() })

/** Return an [Issue] derived from this one with the fields removed that are set when the entity is created. */
private fun Issue.toPlain(): Issue =
    copy(identifier = null, worker = null)
