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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphRoot
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.sql.selectAll

class DaoAnalyzerRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var analyzerRunRepository: DaoAnalyzerRunRepository

    var analyzerJobId = -1L

    beforeEach {
        analyzerRunRepository = dbExtension.fixtures.analyzerRunRepository

        analyzerJobId = dbExtension.fixtures.analyzerJob.id
    }

    "create should create an entry in the database" {
        val createdAnalyzerRun = analyzerRunRepository.create(analyzerJobId, analyzerRun)

        val dbEntry = analyzerRunRepository.get(createdAnalyzerRun.id)

        dbEntry.shouldNotBeNull()
        val expectedRun = analyzerRun.copy(
            id = createdAnalyzerRun.id,
            analyzerJobId = analyzerJobId,
            issues = analyzerRun.issues.map { it.copy(worker = AnalyzerRunDao.ISSUE_WORKER_TYPE) }
        )
        dbEntry shouldBe expectedRun
    }

    "create should deduplicate packages with empty metadata" {
        val pkg = Package(
            identifier = Identifier("", "", "", ""),
            purl = "",
            cpe = null,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            processedDeclaredLicense = ProcessedDeclaredLicense(null, emptyMap(), emptySet()),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact("", "", ""),
            sourceArtifact = RemoteArtifact("", "", ""),
            vcs = VcsInfo(RepositoryType.UNKNOWN, "", "", ""),
            vcsProcessed = VcsInfo(RepositoryType.UNKNOWN, "", "", "")
        )

        val analyzerRunWithEmptyPackage = analyzerRun.copy(packages = setOf(pkg))

        analyzerRunRepository.create(analyzerJobId, analyzerRunWithEmptyPackage)
        analyzerRunRepository.create(analyzerJobId, analyzerRunWithEmptyPackage)

        dbExtension.db.dbQuery { PackagesTable.selectAll().count() } shouldBe 1
    }

    "create should deduplicate packages with full metadata" {
        analyzerRunRepository.create(analyzerJobId, analyzerRun)
        analyzerRunRepository.create(analyzerJobId, analyzerRun)

        dbExtension.db.dbQuery { PackagesTable.selectAll().count() } shouldBe 1
    }

    "create should deduplicate projects with empty metadata" {
        val project = Project(
            identifier = Identifier("", "", "", ""),
            cpe = null,
            definitionFilePath = "",
            authors = emptySet(),
            declaredLicenses = emptySet(),
            processedDeclaredLicense = ProcessedDeclaredLicense(null, emptyMap(), emptySet()),
            vcs = VcsInfo(RepositoryType.UNKNOWN, "", "", ""),
            vcsProcessed = VcsInfo(RepositoryType.UNKNOWN, "", "", ""),
            description = "",
            homepageUrl = "",
            scopeNames = emptySet()
        )

        val analyzerRunWithEmptyProject = analyzerRun.copy(projects = setOf(project))

        analyzerRunRepository.create(analyzerJobId, analyzerRunWithEmptyProject)
        analyzerRunRepository.create(analyzerJobId, analyzerRunWithEmptyProject)

        dbExtension.db.dbQuery { ProjectsTable.selectAll().count() } shouldBe 1
    }

    "create should deduplicate projects with full metadata" {
        analyzerRunRepository.create(analyzerJobId, analyzerRun)
        analyzerRunRepository.create(analyzerJobId, analyzerRun)

        dbExtension.db.dbQuery { ProjectsTable.selectAll().count() } shouldBe 1
    }

    "create should handle unique constraint violations" {
        val txCount = 64
        withContext(Dispatchers.IO) {
            (1..txCount).map {
                async {
                    val run = analyzerRun.copy(packages = setOf(createPackage(it)))
                    analyzerRunRepository.create(analyzerJobId, run)
                }
            }
        }.awaitAll()
    }

    "create should save shortest dependency path for package" {
        val shortestPaths = mapOf(
            pkg.identifier to listOf(
                ShortestDependencyPath(
                    projectIdentifier = project.identifier,
                    scope = "compile",
                    path = emptyList()
                )
            )
        )

        analyzerRunRepository.create(analyzerJobId, analyzerRun, shortestPaths)

        val result = dbExtension.db.dbQuery {
            ShortestDependencyPathsTable
                .selectAll()
                .map { ShortestDependencyPathDao.wrapRow(it).mapToModel() }
        }

        result.shouldBeSingleton {
            it.projectIdentifier shouldBe project.identifier
            it.scope shouldBe "compile"
            it.path shouldBe emptyList()
        }
    }

    "get should return null" {
        analyzerRunRepository.get(1L).shouldBeNull()
    }
})

private fun DaoAnalyzerRunRepository.create(
    analyzerJobId: Long,
    analyzerRun: AnalyzerRun,
    shortestDependencyPaths: Map<Identifier, List<ShortestDependencyPath>> = emptyMap()
) = create(
    analyzerJobId = analyzerJobId,
    startTime = analyzerRun.startTime,
    endTime = analyzerRun.endTime,
    environment = analyzerRun.environment,
    config = analyzerRun.config,
    projects = analyzerRun.projects,
    packages = analyzerRun.packages,
    issues = analyzerRun.issues,
    dependencyGraphs = analyzerRun.dependencyGraphs,
    shortestDependencyPaths = shortestDependencyPaths
)

private val analyzerConfiguration = AnalyzerConfiguration(
    allowDynamicVersions = true,
    enabledPackageManagers = listOf("Gradle", "NPM", "Yarn"),
    disabledPackageManagers = listOf("Maven", "Pub"),
    packageManagers = mapOf(
        "Gradle" to PackageManagerConfiguration(
            mustRunAfter = listOf("NPM")
        ),
        "NPM" to PackageManagerConfiguration(
            options = mapOf("legacyPeerDeps" to "true")
        ),
        "Yarn" to PackageManagerConfiguration(
            options = emptyMap()
        )
    ),
    skipExcluded = false
)

private val project = Project(
    identifier = Identifier(
        type = "type",
        namespace = "namespace",
        name = "project",
        version = "version"
    ),
    cpe = "cpe",
    definitionFilePath = "definitionFilePath",
    authors = setOf("author1", "author2"),
    declaredLicenses = setOf(
        "LicenseRef-declared",
        "LicenseRef-toBeMapped1",
        "LicenseRef-toBeMapped2",
        "LicenseRef-unmapped1",
        "LicenseRef-unmapped2"
    ),
    processedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = "LicenseRef-declared OR LicenseRef-mapped1 OR LicenseRef-mapped2",
        mappedLicenses = mapOf(
            "LicenseRef-toBeMapped1" to "LicenseRef-mapped1",
            "LicenseRef-toBeMapped2" to "LicenseRef-mapped2"
        ),
        unmappedLicenses = setOf("LicenseRef-unmapped1", "LicenseRef-unmapped2")
    ),
    vcs = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/project.git",
        revision = "revision",
        path = "path"
    ),
    vcsProcessed = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/projectProcessed.git",
        revision = "revisionProcessed",
        path = "pathProcessed"
    ),
    description = "description",
    homepageUrl = "https://example.com",
    scopeNames = setOf("compile")
)

private val pkg = createPackage(1)

private fun createPackage(index: Int) = Package(
    identifier = Identifier(
        type = "type",
        namespace = "namespace",
        name = "package$index",
        version = "version"
    ),
    purl = "purl",
    cpe = "cpe",
    authors = setOf("author1", "author2"),
    declaredLicenses = setOf(
        "LicenseRef-declared",
        "LicenseRef-toBeMapped1",
        "LicenseRef-toBeMapped2",
        "LicenseRef-unmapped1",
        "LicenseRef-unmapped2"
    ),
    processedDeclaredLicense = ProcessedDeclaredLicense(
        spdxExpression = "LicenseRef-declared OR LicenseRef-mapped1 OR LicenseRef-mapped2",
        mappedLicenses = mapOf(
            "LicenseRef-toBeMapped1" to "LicenseRef-mapped1",
            "LicenseRef-toBeMapped2" to "LicenseRef-mapped2"
        ),
        unmappedLicenses = setOf("LicenseRef-unmapped1", "LicenseRef-unmapped2")
    ),
    description = "description",
    homepageUrl = "https://example.com",
    binaryArtifact = RemoteArtifact(
        url = "https://example.com/binary$index.zip",
        hashValue = "",
        hashAlgorithm = ""
    ),
    sourceArtifact = RemoteArtifact(
        url = "https://example.com/source$index.zip",
        hashValue = "0123456789abcdef0123456789abcdef01234567",
        hashAlgorithm = "SHA-1"
    ),
    vcs = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/package$index.git",
        revision = "revision",
        path = "path"
    ),
    vcsProcessed = VcsInfo(
        type = RepositoryType.GIT,
        url = "https://example.com/package${index}Processed.git",
        revision = "revisionProcessed",
        path = "pathProcessed"
    )
)

private val dependencyGraphs = mapOf(
    "Maven" to DependencyGraph(
        packages = listOf(
            Identifier(
                type = "type",
                namespace = "namespace",
                name = "package",
                version = "version"
            ),
            Identifier(
                type = "type",
                namespace = "namespace",
                name = "project",
                version = "version"
            )
        ),
        nodes = listOf(
            DependencyGraphNode(
                pkg = 0,
                fragment = 0,
                linkage = "DYNAMIC",
                emptyList()
            ),
            DependencyGraphNode(
                pkg = 1,
                fragment = 0,
                linkage = "DYNAMIC",
                emptyList()
            )
        ),
        edges = setOf(
            DependencyGraphEdge(
                from = 1,
                to = 0
            )
        ),
        scopes = mapOf(
            "compile" to listOf(
                DependencyGraphRoot(
                    root = 1,
                    fragment = 0
                )
            )
        )
    )
)

private val variables = mapOf(
    "SHELL" to "/bin/bash",
    "TERM" to "xterm-256color"
)

private val toolVersions = mapOf(
    "Conan" to "1.53.0",
    "NPM" to "8.15.1"
)

private val environment = Environment(
    ortVersion = "1.0",
    javaVersion = "11.0.16",
    os = "Linux",
    processors = 8,
    maxMemory = 8321499136,
    variables = variables,
    toolVersions = toolVersions
)

private val identifier = Identifier(
    type = "type",
    namespace = "namespace",
    name = "name",
    version = "version"
)

private val issue = Issue(
    timestamp = Clock.System.now().toDatabasePrecision(),
    source = "NexusIq",
    message = "message",
    severity = Severity.ERROR,
    identifier = identifier,
    worker = "advisor"
)

private val analyzerRun = AnalyzerRun(
    id = -1L,
    analyzerJobId = -1L,
    startTime = Clock.System.now().toDatabasePrecision(),
    endTime = Clock.System.now().toDatabasePrecision(),
    environment = environment,
    config = analyzerConfiguration,
    projects = setOf(project),
    packages = setOf(pkg),
    issues = listOf(issue.copy(identifier = pkg.identifier)),
    dependencyGraphs = dependencyGraphs
)
