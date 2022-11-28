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

import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.migrate
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.utils.toDatabasePrecision
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.Environment
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.Package
import org.ossreviewtoolkit.server.model.runs.PackageManagerConfiguration
import org.ossreviewtoolkit.server.model.runs.Project
import org.ossreviewtoolkit.server.model.runs.RemoteArtifact
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class DaoAnalyzerRunRepositoryTest : DatabaseTest() {
    private val analyzerRunRepository = DaoAnalyzerRunRepository()

    private lateinit var fixtures: Fixtures
    private var analyzerJobId = -1L
    private lateinit var environment: Environment

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()
        dataSource.migrate()

        fixtures = Fixtures()
        analyzerJobId = fixtures.analyzerJob.id
        environment = fixtures.environment
    }

    init {
        test("create should create an entry in the database") {
            val analyzerConfiguration = AnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = listOf("Gradle", "NPM"),
                disabledPackageManagers = listOf("Maven", "Pub"),
                packageManagers = mapOf(
                    "Gradle" to PackageManagerConfiguration(
                        mustRunAfter = listOf("NPM")
                    ),
                    "NPM" to PackageManagerConfiguration(
                        options = mapOf("legacyPeerDeps" to "true")
                    )
                )
            )

            val project = Project(
                identifier = Identifier(
                    type = "type",
                    namespace = "namespace",
                    name = "project",
                    version = "version"
                ),
                cpe = "cpe",
                definitionFilePath = "definitionFilePath",
                authors = setOf("author1", "author2"),
                declaredLicenses = setOf("license1", "license2"),
                vcs = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://example.com/project.git",
                    revision = "",
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://example.com/project.git",
                    revision = "main",
                    path = ""
                ),
                homepageUrl = "https://example.com",
                scopeNames = emptySet()
            )

            val pkg = Package(
                identifier = Identifier(
                    type = "type",
                    namespace = "namespace",
                    name = "package",
                    version = "version"
                ),
                purl = "purl",
                cpe = "cpe",
                authors = setOf("author1", "author2"),
                declaredLicenses = setOf("license1", "license2"),
                description = "description",
                homepageUrl = "https://example.com",
                binaryArtifact = RemoteArtifact(
                    url = "https://example.com/binary.zip",
                    hashValue = "",
                    hashAlgorithm = ""
                ),
                sourceArtifact = RemoteArtifact(
                    url = "https://example.com/source.zip",
                    hashValue = "",
                    hashAlgorithm = ""
                ),
                vcs = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://example.com/package.git",
                    revision = "",
                    path = ""
                ),
                vcsProcessed = VcsInfo(
                    type = RepositoryType.GIT,
                    url = "https://example.com/package.git",
                    revision = "main",
                    path = ""
                )
            )

            val issue = OrtIssue(
                timestamp = Clock.System.now(),
                source = "source",
                message = "message",
                severity = "severity"
            )

            val createdAnalyzerRun = analyzerRunRepository.create(
                analyzerJobId = analyzerJobId,
                environmentId = environment.id,
                startTime = Clock.System.now(),
                endTime = Clock.System.now(),
                config = analyzerConfiguration,
                projects = setOf(project),
                packages = setOf(pkg),
                issues = mapOf(pkg.identifier to listOf(issue))
            )

            val dbEntry = analyzerRunRepository.get(createdAnalyzerRun.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe AnalyzerRun(
                id = createdAnalyzerRun.id,
                analyzerJobId = analyzerJobId,
                startTime = createdAnalyzerRun.startTime,
                endTime = createdAnalyzerRun.endTime,
                environment = environment,
                config = createdAnalyzerRun.config,
                projects = setOf(project),
                packages = setOf(pkg),
                issues = mapOf(pkg.identifier to listOf(issue.copy(timestamp = issue.timestamp.toDatabasePrecision())))
            )
        }
    }
}
