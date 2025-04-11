/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.scanner

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import java.time.Instant

import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant

import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.workers.common.OrtRunService
import org.eclipse.apoapsis.ortserver.workers.common.OrtTestData
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.EnvironmentService
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue as OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ProvenanceResolutionResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.Severity

private const val ORT_SERVER_MAPPINGS_FILE = "org.eclipse.apoapsis.ortserver.workers.common.OrtServerMappingsKt"

private const val SCANNER_JOB_ID = 1L
private const val REPOSITORY_ID = 1L
private const val ORT_RUN_ID = 12L
private const val TRACE_ID = "42"

private val scannerJob = ScannerJob(
    id = SCANNER_JOB_ID,
    ortRunId = ORT_RUN_ID,
    createdAt = Clock.System.now(),
    startedAt = Clock.System.now(),
    finishedAt = null,
    configuration = ScannerJobConfiguration(),
    status = JobStatus.CREATED
)

class ScannerWorkerTest : StringSpec({
    "A project should be scanned successfully" {
        val analyzerRun = mockk<AnalyzerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val ortRunService = mockk<OrtRunService> {
            every { createScannerRun(any()) } returns mockk {
                every { id } returns scannerJob.id
            }
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getOrtRun(any()) } returns ortRun
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { getScannerJob(any()) } returns scannerJob
            every { finalizeScannerRun(any(), any()) } returns mockk()
            every { startScannerJob(any()) } returns scannerJob
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockContextFactory(context)

        val ortIdentifier = Identifier("type", "namespace", "name", "version")
        val mappedIdentifier = org.eclipse.apoapsis.ortserver.model.runs.Identifier(
            ortIdentifier.type,
            ortIdentifier.namespace,
            ortIdentifier.name,
            ortIdentifier.version
        )

        val provenance = mockk<ArtifactProvenance>(relaxed = true)
        val ortScannerRun = ScannerRun.EMPTY.copy(
            scanners = mapOf(ortIdentifier to setOf("scanner1", "scanner2")),
            scanResults = setOf(
                ScanResult(
                    provenance = provenance,
                    scanner = mockk(relaxed = true),
                    summary = ScanSummary(
                        startTime = Instant.now(),
                        endTime = Instant.now(),
                        issues = emptyList()
                    )
                )
            ),
            provenances = setOf(
                ProvenanceResolutionResult(packageProvenance = provenance, id = ortIdentifier)
            )
        )
        val issue = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner",
            message = "Test hint message",
            severity = Severity.HINT,
            affectedPath = "test/path"
        )
        val issuesMap = mapOf<Provenance, Set<OrtIssue>>(provenance to setOf(issue))

        val runner = mockk<ScannerRunner> {
            coEvery { run(context, any(), any(), any()) } returns OrtScannerResult(ortScannerRun, issuesMap)
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { setupAuthenticationForCurrentRun(context) } just runs
        }

        val worker = ScannerWorker(mockk(), runner, ortRunService, contextFactory, environmentService)

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Success

            val slotScannerRun = slot<org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun>()
            verify(exactly = 1) { ortRunService.finalizeScannerRun(capture(slotScannerRun), any()) }
            slotScannerRun.captured.scanners shouldBe mapOf(mappedIdentifier to setOf("scanner1", "scanner2"))

            coVerify { environmentService.setupAuthenticationForCurrentRun(context) }
        }
    }

    "Issues should be stored correctly" {
        val analyzerRun = mockk<AnalyzerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val ortRunService = mockk<OrtRunService> {
            every { createScannerRun(any()) } returns mockk {
                every { id } returns scannerJob.id
            }
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getOrtRun(any()) } returns ortRun
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { getScannerJob(any()) } returns scannerJob
            every { finalizeScannerRun(any(), any()) } returns mockk()
            every { startScannerJob(any()) } returns scannerJob
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockContextFactory(context)

        val provenance1 = mockk<ArtifactProvenance>(relaxed = true)
        val provenance2 = mockk<RepositoryProvenance>(relaxed = true)
        val provenance3 = mockk<RepositoryProvenance>(relaxed = true)
        val ortIdentifier1 = Identifier("type", "namespace", "name", "version")
        val ortIdentifier2 = Identifier("type2", "namespace2", "name2", "version2")
        val ortIssue1 = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner",
            message = "Test message",
            severity = Severity.WARNING,
            affectedPath = "test/path"
        )
        val ortIssue2 = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner2",
            message = "Test message 2",
            severity = Severity.ERROR,
            affectedPath = "test/path"
        )
        val ortIssue3 = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner3",
            message = "Test message 3",
            severity = Severity.HINT,
            affectedPath = "test/path"
        )
        val ortIssue4 = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner4",
            message = "Test message 4",
            severity = Severity.HINT,
            affectedPath = "test/path2"
        )
        val scanResult1 = ScanResult(
            provenance = provenance1,
            scanner = mockk(relaxed = true),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                issues = listOf(ortIssue1)
            )
        )
        val scanResult2 = ScanResult(
            provenance = provenance2,
            scanner = mockk(relaxed = true),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                issues = listOf(ortIssue2, ortIssue3)
            )
        )
        val scanResult3 = ScanResult(
            provenance = provenance3,
            scanner = mockk(relaxed = true),
            summary = ScanSummary(
                startTime = Instant.now(),
                endTime = Instant.now(),
                issues = listOf(ortIssue4)
            )
        )
        val ortScannerRun = mockk<ScannerRun>(relaxed = true) {
            every { scanners } returns mapOf(
                ortIdentifier1 to setOf("scanner1", "scanner2"),
                ortIdentifier2 to setOf("scanner3")
            )
            every { scanResults } returns setOf(scanResult1, scanResult2)
            every { getAllScanResults() } returns mapOf(
                ortIdentifier1 to listOf(scanResult1),
                ortIdentifier2 to listOf(scanResult2, scanResult3),
            )
        }
        val issuesMap = mapOf<Provenance, Set<OrtIssue>>(
            provenance1 to setOf(ortIssue1),
            provenance2 to setOf(ortIssue2, ortIssue3),
            provenance3 to setOf(ortIssue4)
        )

        val runner = mockk<ScannerRunner> {
            coEvery { run(context, any(), any(), any()) } returns OrtScannerResult(ortScannerRun, issuesMap)
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { setupAuthenticationForCurrentRun(context) } just runs
        }

        val worker = ScannerWorker(mockk(), runner, ortRunService, contextFactory, environmentService)

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues

            val slotScannerRun = slot<org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun>()
            val slotIssues = slot<Collection<Issue>>()
            verify(exactly = 1) {
                ortRunService.finalizeScannerRun(capture(slotScannerRun), capture(slotIssues))
            }
            slotScannerRun.captured.scanners shouldBe mapOf(
                ortIdentifier1.mapToModel() to setOf("scanner1", "scanner2"),
                ortIdentifier2.mapToModel() to setOf("scanner3")
            )

            val identifier2 = ortIdentifier2.mapToModel()
            val expectedIssues = listOf(ortIssue1, ortIssue2, ortIssue3, ortIssue4)
                .zip(listOf(ortIdentifier1.mapToModel(), identifier2, identifier2, identifier2))
                .map { (issue, identifier) ->
                    Issue(
                        timestamp = issue.timestamp.toKotlinInstant(),
                        source = issue.source,
                        message = issue.message,
                        severity = issue.severity.mapToModel(),
                        affectedPath = issue.affectedPath,
                        identifier = identifier,
                        worker = "scanner"
                    )
                }
            slotIssues.captured shouldContainExactlyInAnyOrder expectedIssues

            coVerify { environmentService.setupAuthenticationForCurrentRun(context) }
        }
    }

    "A failure result should be returned in case of an error" {
        val textException = IllegalStateException("Test exception")
        val ortRunService = mockk<OrtRunService> {
            every { getScannerJob(any()) } throws textException
        }

        val worker = ScannerWorker(mockk(), mockk(), ortRunService, mockk(), mockk())

        mockkTransaction {
            when (val result = worker.run(SCANNER_JOB_ID, TRACE_ID)) {
                is RunResult.Failed -> result.error shouldBe textException
                else -> fail("Unexpected result: $result")
            }
        }
    }

    "A 'finished with issues' result should be returned if the scanner run finished with issues" {
        val analyzerRun = mockk<AnalyzerRun>()
        val hierarchy = mockk<Hierarchy>()
        val ortRun = mockk<OrtRun> {
            every { id } returns ORT_RUN_ID
            every { repositoryId } returns REPOSITORY_ID
            every { revision } returns "main"
        }

        mockkStatic(ORT_SERVER_MAPPINGS_FILE)
        every { analyzerRun.mapToOrt() } returns mockk()
        every { ortRun.mapToOrt(any(), any(), any(), any(), any(), any()) } returns OrtResult.EMPTY

        val ortRunService = mockk<OrtRunService> {
            every { createScannerRun(any()) } returns mockk {
                every { id } returns scannerJob.id
            }
            every { getAnalyzerRunForOrtRun(any()) } returns analyzerRun
            every { getHierarchyForOrtRun(any()) } returns hierarchy
            every { getOrtRepositoryInformation(any()) } returns mockk()
            every { getOrtRun(any()) } returns ortRun
            every { getResolvedConfiguration(any()) } returns ResolvedConfiguration()
            every { getScannerJob(any()) } returns scannerJob
            every { finalizeScannerRun(any(), any()) } returns mockk()
            every { startScannerJob(any()) } returns scannerJob
        }

        val context = mockk<WorkerContext>()
        val contextFactory = mockContextFactory(context)

        val provenance: Provenance =
            OrtTestData.scannerRun.provenances.firstNotNullOf(ProvenanceResolutionResult::packageProvenance)
        val issue = OrtIssue(
            timestamp = Instant.now(),
            source = "TestScanner",
            message = "Test message",
            severity = Severity.WARNING,
            affectedPath = "test/path"
        )
        val issuesMap = mapOf(provenance to setOf(issue))
        val runner = mockk<ScannerRunner> {
            coEvery { run(context, any(), any(), any()) } returns OrtScannerResult(OrtTestData.scannerRun, issuesMap)
        }

        val environmentService = mockk<EnvironmentService> {
            coEvery { setupAuthenticationForCurrentRun(context) } just runs
        }

        val worker = ScannerWorker(mockk(), runner, ortRunService, contextFactory, environmentService)

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.FinishedWithIssues
        }
    }

    "An ignore result should be returned for an invalid job" {
        val invalidJob = scannerJob.copy(status = JobStatus.FINISHED)
        val ortRunService = mockk<OrtRunService> {
            every { getScannerJob(any()) } returns invalidJob
        }

        val worker = ScannerWorker(mockk(), mockk(), ortRunService, mockk(), mockk())

        mockkTransaction {
            val result = worker.run(SCANNER_JOB_ID, TRACE_ID)

            result shouldBe RunResult.Ignored
        }
    }
})

/**
 * Create a mock [WorkerContextFactory] and prepare it to return the given [context].
 */
private fun mockContextFactory(context: WorkerContext = mockk()): WorkerContextFactory {
    val slot = slot<suspend (WorkerContext) -> RunResult>()
    return mockk {
        coEvery { withContext(ORT_RUN_ID, capture(slot)) } coAnswers {
            slot.captured(context)
        }
    }
}
