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

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorResult
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun
import org.ossreviewtoolkit.server.model.runs.advisor.Defect
import org.ossreviewtoolkit.server.model.runs.advisor.GithubDefectsConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.NexusIqConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.OsvConfiguration
import org.ossreviewtoolkit.server.model.runs.advisor.Vulnerability
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerabilityReference
import org.ossreviewtoolkit.server.model.runs.advisor.VulnerableCodeConfiguration

class DaoAdvisorRunRepositoryTest : StringSpec({
    val advisorRunRepository = DaoAdvisorRunRepository()

    lateinit var fixtures: Fixtures
    var advisorJobId = -1L

    extension(
        DatabaseTestExtension {
            fixtures = Fixtures()
            advisorJobId = fixtures.advisorJob.id
        }
    )

    "create should create an entry in the database" {
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
            githubDefectsConfiguration = GithubDefectsConfiguration(
                endpointUrl = "https://github.com/defects",
                labelFilter = listOf("!any"),
                maxNumberOfIssuesPerRepository = 5,
                parallelRequests = 2
            ),
            nexusIqConfiguration = NexusIqConfiguration(
                serverUrl = "https://nexus-iq.com",
                browseUrl = "https://nexus-iq.com/browse"
            ),
            osvConfiguration = OsvConfiguration(
                serverUrl = "https://google.com/osv"
            ),
            vulnerableCodeConfiguration = VulnerableCodeConfiguration(
                serverUrl = "https://vulnerable-code.com"
            ),
            options = mapOf("config-key1" to "config-value1")
        )

        val identifier = Identifier(
            type = "type",
            namespace = "namespace",
            name = "name",
            version = "version"
        )

        val issue = OrtIssue(
            timestamp = Clock.System.now(),
            source = "source",
            message = "message",
            severity = "ERROR"
        )

        val defect = Defect(
            externalId = "external-id",
            url = "https://example.com/external-id",
            title = "title",
            state = "state",
            severity = "ERROR",
            description = "description",
            creationTime = Clock.System.now(),
            modificationTime = Clock.System.now(),
            closingTime = Clock.System.now(),
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
                    severity = "ERROR"
                )
            )
        )

        val advisorResult = AdvisorResult(
            advisorName = "NexusIQ",
            capabilities = emptyList(),
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            issues = listOf(issue),
            defects = listOf(defect),
            vulnerabilities = listOf(vulnerability)
        )

        val createdAdvisorRun = advisorRunRepository.create(
            advisorJobId = advisorJobId,
            startTime = Clock.System.now(),
            endTime = Clock.System.now(),
            environment = environment,
            config = advisorConfiguration,
            advisorRecords = mapOf(identifier to listOf(advisorResult))
        )

        val dbEntry = advisorRunRepository.get(createdAdvisorRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe AdvisorRun(
            id = createdAdvisorRun.id,
            advisorJobId = advisorJobId,
            startTime = createdAdvisorRun.startTime,
            endTime = createdAdvisorRun.endTime,
            environment = environment,
            config = advisorConfiguration,
            advisorRecords = mapOf(
                identifier to listOf(
                    advisorResult.copy(
                        startTime = advisorResult.startTime.toDatabasePrecision(),
                        endTime = advisorResult.endTime.toDatabasePrecision(),
                        issues = listOf(issue.copy(timestamp = issue.timestamp.toDatabasePrecision())),
                        defects = listOf(
                            defect.copy(
                                creationTime = defect.creationTime?.toDatabasePrecision(),
                                modificationTime = defect.modificationTime?.toDatabasePrecision(),
                                closingTime = defect.modificationTime?.toDatabasePrecision()
                            )
                        )
                    )
                ).toList()
            )
        )
    }

    "get should return null" {
        advisorRunRepository.get(1L).shouldBeNull()
    }
})
