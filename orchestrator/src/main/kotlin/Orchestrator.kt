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

package org.ossreviewtoolkit.server.orchestrator

import kotlinx.datetime.Clock

import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorRequest
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AdvisorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerRequest
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzerWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorRequest
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.EvaluatorWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.ReporterRequest
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ReporterWorkerResult
import org.ossreviewtoolkit.server.model.orchestrator.ScannerRequest
import org.ossreviewtoolkit.server.model.orchestrator.ScannerWorkerError
import org.ossreviewtoolkit.server.model.orchestrator.ScannerWorkerResult
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.util.asPresent
import org.ossreviewtoolkit.server.transport.AdvisorEndpoint
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.EvaluatorEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessagePublisher
import org.ossreviewtoolkit.server.transport.ReporterEndpoint
import org.ossreviewtoolkit.server.transport.ScannerEndpoint

import org.slf4j.LoggerFactory

/**
 * The Orchestrator is the central component that breaks an ORT run into single steps and coordinates their execution.
 * It creates jobs for the single processing steps and passes them to the corresponding workers. It collects the results
 * produced by the workers until the complete ORT result is available or the run has failed.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class Orchestrator(
    private val analyzerJobRepository: AnalyzerJobRepository,
    private val advisorJobRepository: AdvisorJobRepository,
    private val scannerJobRepository: ScannerJobRepository,
    private val evaluatorJobRepository: EvaluatorJobRepository,
    private val reporterJobRepository: ReporterJobRepository,
    private val repositoryRepository: RepositoryRepository,
    private val ortRunRepository: OrtRunRepository,
    private val publisher: MessagePublisher
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Handle messages of the type [CreateOrtRun].
     */
    fun handleCreateOrtRun(header: MessageHeader, createOrtRun: CreateOrtRun) {
        val ortRun = createOrtRun.ortRun

        val repository = repositoryRepository.get(ortRun.repositoryId)
        if (repository != null) {
            val analyzerJob = analyzerJobRepository.create(
                ortRun.id,
                ortRun.jobs.analyzer
            )

            publisher.publish(
                AnalyzerEndpoint,
                Message(
                    header = header,
                    payload = AnalyzerRequest(analyzerJob.id)
                )
            )

            analyzerJobRepository.update(
                analyzerJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        } else {
            log.warn("Failed to schedule Analyzer job. Repository '${ortRun.repositoryId}' not found.")
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        val jobId = analyzerWorkerResult.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AnalyzeResult' message. No analyzer job '$jobId' found.")
            return
        }

        val ortRun = ortRunRepository.get(analyzerJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'AnalyzeResult' message. No ORT run '${analyzerJob.ortRunId}' found.")
            return
        }

        ortRun.jobs.advisor?.let { advisorJobConfiguration ->
            val advisorJob = advisorJobRepository.create(analyzerJob.ortRunId, advisorJobConfiguration)

            publisher.publish(
                to = AdvisorEndpoint,
                message = Message(header = header, payload = AdvisorRequest(advisorJob.id, analyzerJob.id))
            )

            advisorJobRepository.update(
                advisorJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        }

        ortRun.jobs.scanner?.let { scannerJobConfiguration ->
            val scannerJob = scannerJobRepository.create(analyzerJob.ortRunId, scannerJobConfiguration)

            publisher.publish(
                to = ScannerEndpoint,
                message = Message(header = header, payload = ScannerRequest(scannerJob.id))
            )

            scannerJobRepository.update(
                id = scannerJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        }

        /**
         * Create an evaluator job if both advisor and scanner jobs are disabled
         */
        if (ortRun.jobs.advisor == null && ortRun.jobs.scanner == null) {
            createEvaluatorJob(ortRun, header)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        val jobId = analyzerWorkerError.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob != null) {
            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the analyzerJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = analyzerJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AnalyzeError' message. No analyzer job ORT run '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        val jobId = advisorWorkerResult.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob != null) {
            advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AdviseResult' message. No advisor job '$jobId' found.")
            return
        }

        val ortRun = ortRunRepository.get(advisorJob.ortRunId)
        if (ortRun == null) {
            log.warn("Failed to handle 'AdviseResult' message. No ORT run '${advisorJob.ortRunId}' found.")
            return
        }

        /**
         * Create an Evaluator job only if Advisor and Scanner jobs have finished successfully
         */
        if (scannerJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
            createEvaluatorJob(ortRun, header)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(advisorWorkerError: AdvisorWorkerError) {
        val jobId = advisorWorkerError.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob != null) {
            advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the advisorJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = advisorJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'AdviseError' message. No advisor job ORT run '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerResult].
     */
    fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        val jobId = scannerWorkerResult.jobId

        val scannerJob = scannerJobRepository.get(jobId)

        if (scannerJob != null) {
            scannerJobRepository.update(
                id = scannerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'ScannerResult' message. No scanner job '$jobId' found.")
            return
        }

        val ortRun = ortRunRepository.get(scannerJob.ortRunId)
        if (ortRun == null) {
            log.warn("Failed to handle 'ScannerResult' message. No ORT run '${scannerJob.ortRunId}' found.")
            return
        }

        /**
         * Create an Evaluator job only if Advisor and Scanner jobs have finished successfully
         */
        if (advisorJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
            createEvaluatorJob(ortRun, header)
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerError].
     */
    fun handleScannerWorkerError(scannerWorkerError: ScannerWorkerError) {
        val jobId = scannerWorkerError.jobId

        val scannerJob = scannerJobRepository.get(jobId)

        if (scannerJob != null) {
            scannerJobRepository.update(
                id = scannerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the scannerJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = scannerJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'ScannerError' message. No advisor job ORT run '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [EvaluatorWorkerResult].
     */
    fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        val jobId = evaluatorWorkerResult.jobId

        val evaluatorJob = evaluatorJobRepository.get(jobId)

        if (evaluatorJob != null) {
            evaluatorJobRepository.update(
                id = evaluatorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'EvaluatorResult' message. No evaluator job '$jobId' found.")
            return
        }

        val ortRun = ortRunRepository.get(evaluatorJob.ortRunId)
        if (ortRun == null) {
            log.warn("Failed to handle 'EvaluatorResult' message. No ORT run '${evaluatorJob.ortRunId}' found.")
            return
        }

        createReporterJob(ortRun, header)
    }

    /**
     * Handle messages of the type [EvaluatorWorkerError].
     */
    fun handleEvaluatorWorkerError(evaluatorWorkerError: EvaluatorWorkerError) {
        val jobId = evaluatorWorkerError.jobId

        val evaluatorJob = evaluatorJobRepository.get(jobId)

        if (evaluatorJob != null) {
            evaluatorJobRepository.update(
                id = evaluatorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the evaluatorJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = evaluatorJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'EvaluatorError' message. No evaluator job ORT run '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [ReporterWorkerResult].
     */
    fun handleReporterWorkerResult(reporterWorkerResult: ReporterWorkerResult) {
        val jobId = reporterWorkerResult.jobId

        val reporterJob = reporterJobRepository.get(jobId)

        if (reporterJob != null) {
            reporterJobRepository.update(
                id = reporterJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'ReporterResult' message. No reporter job '$jobId' found.")
        }
    }

    /**
     * Handle messages of the type [ReporterWorkerError].
     */
    fun handleReporterWorkerError(reporterWorkerError: ReporterWorkerError) {
        val jobId = reporterWorkerError.jobId

        val reporterJob = reporterJobRepository.get(jobId)

        if (reporterJob != null) {
            reporterJobRepository.update(
                id = reporterJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FAILED.asPresent()
            )

            // If the reporterJob failed, the whole OrtRun will be treated as failed.
            ortRunRepository.update(
                id = reporterJob.ortRunId,
                status = OrtRunStatus.FAILED.asPresent()
            )
        } else {
            log.warn("Failed to handle 'ReporterError' message. No reporter job ORT run '$jobId' found.")
        }
    }

    /**
     * Create an Evaluator job if it is enabled; otherwise, delegate to [createReporterJob]
     */
    private fun createEvaluatorJob(ortRun: OrtRun, header: MessageHeader) {
        ortRun.jobs.evaluator?.let { evaluatorJobConfiguration ->
            val evaluatorJob = evaluatorJobRepository.create(ortRun.id, evaluatorJobConfiguration)

            publisher.publish(
                to = EvaluatorEndpoint,
                message = Message(header = header, payload = EvaluatorRequest(evaluatorJob.id))
            )

            evaluatorJobRepository.update(
                id = evaluatorJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        } ?: createReporterJob(ortRun, header)
    }

    private fun createReporterJob(ortRun: OrtRun, header: MessageHeader) {
        ortRun.jobs.reporter?.let { reporterJobConfiguration ->
            val reporterJob = reporterJobRepository.create(ortRun.id, reporterJobConfiguration)

            publisher.publish(
                to = ReporterEndpoint,
                message = Message(header = header, payload = ReporterRequest(reporterJob.id))
            )

            reporterJobRepository.update(
                id = reporterJob.id,
                startedAt = Clock.System.now().asPresent(),
                status = JobStatus.SCHEDULED.asPresent()
            )
        }
    }
}
