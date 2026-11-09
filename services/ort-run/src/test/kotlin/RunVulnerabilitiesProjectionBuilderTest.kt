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

package org.eclipse.apoapsis.ortserver.services.ortrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.components.projections.ProjectionKey
import org.eclipse.apoapsis.ortserver.components.projections.ProjectionRegistry
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class RunVulnerabilitiesProjectionBuilderTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())
    val builder = RunVulnerabilitiesProjectionBuilder()

    /**
     * Create an ORT run with an advisor result for a single package that has vulnerabilities with the given external
     * IDs and severities. A severity of `null` creates a vulnerability without references.
     */
    suspend fun createRunWithVulnerabilities(vararg vulnerabilities: Pair<String, String?>): Long {
        val fixtures = dbExtension.fixtures
        val ortRun = fixtures.createOrtRun()
        val advisorJob = fixtures.createAdvisorJob(ortRun.id, AdvisorJobConfiguration())

        fixtures.createAdvisorRun(
            advisorJob.id,
            mapOf(
                Identifier("Maven", "com.example", "lib", "1.0") to listOf(
                    AdvisorResult(
                        advisorName = "advisor",
                        startTime = Clock.System.now(),
                        endTime = Clock.System.now(),
                        issues = emptyList(),
                        vulnerabilities = vulnerabilities.map { (externalId, severity) ->
                            Vulnerability(
                                externalId = externalId,
                                summary = null,
                                description = null,
                                references = listOfNotNull(
                                    severity?.let {
                                        VulnerabilityReference("https://example.com", null, it, null, null)
                                    }
                                )
                            )
                        }
                    )
                )
            )
        )

        return ortRun.id
    }

    /**
     * Create an ORT run with an advisor result for each of the given packages, each of which has a single vulnerability
     * with the given external ID.
     */
    suspend fun createRunWithVulnerablePackages(vararg packages: Pair<String, String>): Long {
        val fixtures = dbExtension.fixtures
        val ortRun = fixtures.createOrtRun()
        val advisorJob = fixtures.createAdvisorJob(ortRun.id, AdvisorJobConfiguration())

        fixtures.createAdvisorRun(
            advisorJob.id,
            packages.associate { (name, externalId) ->
                Identifier("Maven", "com.example", name, "1.0") to listOf(
                    AdvisorResult(
                        advisorName = "advisor",
                        startTime = Clock.System.now(),
                        endTime = Clock.System.now(),
                        issues = emptyList(),
                        vulnerabilities = listOf(
                            Vulnerability(
                                externalId = externalId,
                                summary = null,
                                description = null,
                                references = emptyList()
                            )
                        )
                    )
                )
            }
        )

        return ortRun.id
    }

    suspend fun buildProjection(
        ortRunId: Long,
        projectionBuilder: RunVulnerabilitiesProjectionBuilder = builder
    ) {
        ProjectionRegistry(dbExtension.db).requestBuild(
            ProjectionKey(ortRunId, RunVulnerabilitiesProjectionBuilder.TYPE),
            RunVulnerabilitiesProjectionBuilder.SCHEMA_VERSION
        )

        dbExtension.db.transaction { projectionBuilder.build(ortRunId) }
    }

    /** Return the external IDs and ratings of the projected vulnerabilities of the run with the given [ortRunId]. */
    suspend fun projectedRows(ortRunId: Long): List<Pair<String, Int>> =
        dbExtension.db.transaction {
            ProjectedRunVulnerabilitiesTable.selectAll()
                .where { ProjectedRunVulnerabilitiesTable.ortRunId eq ortRunId }
                .orderBy(ProjectedRunVulnerabilitiesTable.externalId)
                .map { it[ProjectedRunVulnerabilitiesTable.externalId] to it[ProjectedRunVulnerabilitiesTable.rating] }
        }

    "build" should {
        "replace the existing data of the run" {
            val ortRunId = createRunWithVulnerabilities("CVE-2021-1111" to "HIGH", "CVE-2021-2222" to "LOW")

            buildProjection(ortRunId)
            buildProjection(ortRunId)

            projectedRows(ortRunId).map { it.first } should containExactly("CVE-2021-1111", "CVE-2021-2222")
        }

        "not change the data of other runs" {
            val ortRunId = createRunWithVulnerabilities("CVE-2021-1111" to "HIGH")
            val otherOrtRunId = createRunWithVulnerabilities("CVE-2021-2222" to "LOW")

            buildProjection(ortRunId)
            buildProjection(otherOrtRunId)

            projectedRows(ortRunId).map { it.first } should containExactly("CVE-2021-1111")
            projectedRows(otherOrtRunId).map { it.first } should containExactly("CVE-2021-2222")
        }

        "store a rating of NONE for vulnerabilities without references" {
            val ortRunId = createRunWithVulnerabilities("CVE-2021-1111" to null)

            buildProjection(ortRunId)

            projectedRows(ortRunId) should containExactly("CVE-2021-1111" to VulnerabilityRating.NONE.ordinal)
        }

        "build the projection in chunks of packages" {
            val ortRunId = createRunWithVulnerablePackages(
                "lib-a" to "CVE-2021-1111",
                "lib-b" to "CVE-2021-2222",
                "lib-c" to "CVE-2021-3333"
            )

            buildProjection(ortRunId, RunVulnerabilitiesProjectionBuilder(chunkSize = 2))

            projectedRows(ortRunId).map { it.first } should
                containExactly("CVE-2021-1111", "CVE-2021-2222", "CVE-2021-3333")
        }

        "store data that is deleted together with the registry entry" {
            val ortRunId = createRunWithVulnerabilities("CVE-2021-1111" to "HIGH")
            buildProjection(ortRunId)

            dbExtension.fixtures.ortRunRepository.delete(ortRunId)

            projectedRows(ortRunId) should beEmpty()
        }
    }
})
