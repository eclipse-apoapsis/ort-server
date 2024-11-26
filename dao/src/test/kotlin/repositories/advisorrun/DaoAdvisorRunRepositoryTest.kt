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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Defect
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference

class DaoAdvisorRunRepositoryTest : WordSpec({
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

            dbEntry.shouldNotBeNull()
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
    }

    "get" should {
        "return null if the advisor run does not exist" {
            advisorRunRepository.get(advisorJobId).shouldBeNull()
        }
    }

    "getByJobId" should {
        "return the correct advisor run" {
            val createdAdvisorRun = advisorRunRepository.create(advisorJobId, advisorRun)

            advisorRunRepository.getByJobId(advisorJobId) shouldBe
                    advisorRun.copy(id = createdAdvisorRun.id, advisorJobId = advisorJobId)
        }

        "return null if there is no run for the job id" {
            advisorRunRepository.getByJobId(advisorJobId).shouldBeNull()
        }
    }
})

fun DaoAdvisorRunRepository.create(advisorJobId: Long, advisorRun: AdvisorRun) = create(
    advisorJobId = advisorJobId,
    startTime = advisorRun.startTime,
    endTime = advisorRun.endTime,
    environment = advisorRun.environment,
    config = advisorRun.config,
    results = advisorRun.results.mapValues { (_, results) -> results.map { it.withPlainIssues() } }
)

val variables = mapOf(
    "SHELL" to "/bin/bash",
    "TERM" to "xterm-256color"
)

val toolVersions = mapOf(
    "Conan" to "1.53.0",
    "NPM" to "8.15.1"
)

val environment = Environment(
    ortVersion = "1.0",
    javaVersion = "11.0.16",
    os = "Linux",
    processors = 8,
    maxMemory = 8321499136,
    variables = variables,
    toolVersions = toolVersions
)

val advisorConfiguration = AdvisorConfiguration(
    config = mapOf(
        "GitHubDefects" to PluginConfiguration(
            options = mapOf(
                "endpointUrl" to "https://github.com/defects",
                "labelFilter" to "!any",
                "maxNumberOfIssuesPerRepository" to "5",
                "parallelRequests" to "2"
            ),
            secrets = mapOf("token" to "tokenValue")
        ),
        "NexusIQ" to PluginConfiguration(
            options = mapOf(
                "serverUrl" to "https://example.org/nexus",
                "browseUrl" to "https://example.org/nexus/browse"
            ),
            secrets = mapOf(
                "username" to "user",
                "password" to "pass"
            )
        ),
        "OSV" to PluginConfiguration(
            options = mapOf("serverUrl" to "https://google.com/osv"),
            secrets = emptyMap()
        ),
        "VulnerableCode" to PluginConfiguration(
            options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
            secrets = mapOf("apiKey" to "key")
        )
    )
)

val identifier = Identifier(
    type = "type",
    namespace = "namespace",
    name = "name",
    version = "version"
)

val otherIdentifier = Identifier(
    type = "otherType",
    namespace = "otherNamespace",
    name = "otherName",
    version = "otherVersion"
)

val issue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "NexusIq",
    message = "message",
    severity = Severity.ERROR,
    identifier = identifier,
    worker = "advisor"
)

val otherIssue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "GitHubDefects",
    message = "otherMessage",
    severity = Severity.ERROR,
    identifier = otherIdentifier,
    worker = "advisor"
)

val defect = Defect(
    externalId = "external-id",
    url = "https://example.com/external-id",
    title = "title",
    state = "state",
    severity = "ERROR",
    description = "description",
    creationTime = Clock.System.now().toDatabasePrecision(),
    modificationTime = Clock.System.now().toDatabasePrecision(),
    closingTime = Clock.System.now().toDatabasePrecision(),
    fixReleaseVersion = "version",
    fixReleaseUrl = "url",
    labels = mapOf("key" to "value")
)

val vulnerability = Vulnerability(
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
    )
)

val advisorResult = AdvisorResult(
    advisorName = "NexusIQ",
    capabilities = emptyList(),
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    issues = listOf(issue),
    defects = emptyList(),
    vulnerabilities = listOf(vulnerability)
)

val otherAdvisorResult = AdvisorResult(
    advisorName = "GitHubDefects",
    capabilities = emptyList(),
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    issues = listOf(otherIssue),
    defects = listOf(defect),
    vulnerabilities = emptyList()
)

val advisorRun = AdvisorRun(
    id = -1L,
    advisorJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = advisorConfiguration,
    results = mapOf(identifier to listOf(advisorResult), otherIdentifier to listOf(otherAdvisorResult))
)

/**
 * Return a copy of this [AdvisorResult] with the issues converted to plain issues.
 */
private fun AdvisorResult.withPlainIssues(): AdvisorResult =
    copy(issues = issues.map { it.toPlain() })

/**
 * Return an [Issue] derived from this one with the fields removed that are set when the entity is created.
 */
private fun Issue.toPlain(): Issue =
    copy(identifier = null, worker = null)
