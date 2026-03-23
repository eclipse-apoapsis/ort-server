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

package org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue

class AnalyzerRunDaoTest : WordSpec({
    "collectDependencyGraphIssues" should {
        "return an empty set when the dependency graphs map is empty" {
            val result = AnalyzerRunDao.collectDependencyGraphIssues(emptyMap())

            result should beEmpty()
        }

        "collect issues from multiple dependency graphs" {
            val timestamp = Instant.parse("2026-03-23T12:32:30Z")
            val mavenIdentifier = Identifier("Maven", "com.example", "maven-lib", "1.0.0")
            val npmIdentifier = Identifier("NPM", "example", "npm-lib", "2.0.0")

            val mavenIssue = Issue(
                timestamp = timestamp,
                source = "analyzer",
                message = "Maven issue",
                severity = Severity.ERROR,
                identifier = null,
                worker = null
            )

            val npmIssue1 = Issue(
                timestamp = timestamp,
                source = "analyzer",
                message = "NPM issue",
                severity = Severity.WARNING,
                identifier = null,
                worker = null
            )

            val npmIssue2 = Issue(
                timestamp = timestamp,
                source = "analyzer",
                message = "Other NPM issue",
                severity = Severity.ERROR,
                identifier = null,
                worker = null
            )

            val mavenGraph = DependencyGraph(
                packages = listOf(mavenIdentifier),
                nodes = listOf(
                    DependencyGraphNode(
                        pkg = 0,
                        fragment = 0,
                        linkage = "STATIC",
                        issues = listOf(mavenIssue)
                    )
                ),
                edges = emptySet(),
                scopes = emptyMap()
            )

            val npmGraph = DependencyGraph(
                packages = listOf(npmIdentifier),
                nodes = listOf(
                    DependencyGraphNode(
                        pkg = 0,
                        fragment = 0,
                        linkage = "STATIC",
                        issues = listOf(npmIssue1, npmIssue2)
                    )
                ),
                edges = emptySet(),
                scopes = emptyMap()
            )

            val result = AnalyzerRunDao.collectDependencyGraphIssues(
                mapOf("maven" to mavenGraph, "npm" to npmGraph)
            )

            val expectedIssues = listOf(
                mavenIssue.copy(identifier = mavenIdentifier, worker = AnalyzerRunDao.ISSUE_WORKER_TYPE),
                npmIssue1.copy(identifier = npmIdentifier, worker = AnalyzerRunDao.ISSUE_WORKER_TYPE),
                npmIssue2.copy(identifier = npmIdentifier, worker = AnalyzerRunDao.ISSUE_WORKER_TYPE)
            )
            result shouldContainExactlyInAnyOrder expectedIssues
        }

        "truncate timestamp to database precision" {
            val timestamp = Instant.parse("2026-03-23T10:15:30.123456789Z")
            val identifier = Identifier("Maven", "com.example", "library", "1.0.0")

            val issue = Issue(
                timestamp = timestamp,
                source = "analyzer",
                message = "Test issue",
                severity = Severity.WARNING,
                identifier = null,
                worker = null
            )

            val graph = DependencyGraph(
                packages = listOf(identifier),
                nodes = listOf(
                    DependencyGraphNode(
                        pkg = 0,
                        fragment = 0,
                        linkage = "STATIC",
                        issues = listOf(issue)
                    )
                ),
                edges = emptySet(),
                scopes = emptyMap()
            )

            val result = AnalyzerRunDao.collectDependencyGraphIssues(mapOf("maven" to graph))

            result shouldHaveSize 1
            result.first().timestamp shouldBe timestamp.toDatabasePrecision()
        }

        "deduplicate identical issues" {
            val timestamp = Clock.System.now()
            val identifier = Identifier("Maven", "com.example", "library", "1.0.0")

            val issue = Issue(
                timestamp = timestamp,
                source = "analyzer",
                message = "Duplicate issue",
                severity = Severity.WARNING,
                identifier = null,
                worker = null
            )

            val graph = DependencyGraph(
                packages = listOf(identifier),
                nodes = listOf(
                    DependencyGraphNode(
                        pkg = 0,
                        fragment = 0,
                        linkage = "STATIC",
                        issues = listOf(issue, issue)
                    )
                ),
                edges = emptySet(),
                scopes = emptyMap()
            )

            val result = AnalyzerRunDao.collectDependencyGraphIssues(mapOf("maven" to graph))

            result shouldHaveSize 1
        }
    }
})
