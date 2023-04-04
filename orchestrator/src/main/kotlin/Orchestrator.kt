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

import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ScannerJob
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

        if (repository == null) {
            log.warn("Failed to schedule Analyzer job. Repository '${ortRun.repositoryId}' not found.")
            return
        }

        scheduleAnalyzerJob(createAnalyzerJob(ortRun), header)
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        val jobId = analyzerWorkerResult.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob == null) {
            log.warn("Failed to handle 'AnalyzerWorkerResult' message. No analyzer job '$jobId' found.")
            return
        }

        analyzerJobRepository.update(
            id = analyzerJob.id,
            finishedAt = Clock.System.now().asPresent(),
            status = JobStatus.FINISHED.asPresent()
        )

        val ortRun = ortRunRepository.get(analyzerJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'AnalyzerWorkerResult' message. No ORT run '${analyzerJob.ortRunId}' found.")
            return
        }

        val advisorJob = createAdvisorJob(ortRun)
        if (advisorJob != null) scheduleAdvisorJob(advisorJob, analyzerJob, header)

        val scannerJob = createScannerJob(ortRun)
        if (scannerJob != null) scheduleScannerJob(scannerJob, header)

        /**
         * Create an evaluator job if both advisor and scanner jobs are disabled
         */
        if (advisorJob == null && scannerJob == null) {
            if (ortRun.jobs.evaluator != null) {
                createEvaluatorJob(ortRun)?.let {
                    scheduleEvaluatorJob(it, header)
                }
            } else {
                createReporterJob(ortRun)?.let {
                    scheduleReporterJob(it, header)
                }
            }
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        val jobId = analyzerWorkerError.jobId

        val analyzerJob = analyzerJobRepository.get(jobId)

        if (analyzerJob == null) {
            log.warn("Failed to handle 'AnalyzerWorkerError' message. No analyzer job ORT run '$jobId' found.")
            return
        }

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
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        val jobId = advisorWorkerResult.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob == null) {
            log.warn("Failed to handle 'AdvisorWorkerResult' message. No advisor job '$jobId' found.")
            return
        }

        advisorJobRepository.update(
            id = advisorJob.id,
            finishedAt = Clock.System.now().asPresent(),
            status = JobStatus.FINISHED.asPresent()
        )

        val ortRun = ortRunRepository.get(advisorJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'AdvisorWorkerResult' message. No ORT run '${advisorJob.ortRunId}' found.")
            return
        }

        // Create an evaluator job only if the advisor and scanner jobs have finished successfully.
        if (scannerJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
            if (ortRun.jobs.evaluator != null) {
                createEvaluatorJob(ortRun)?.let {
                    scheduleEvaluatorJob(it, header)
                }
            } else {
                createReporterJob(ortRun)?.let {
                    scheduleReporterJob(it, header)
                }
            }
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(advisorWorkerError: AdvisorWorkerError) {
        val jobId = advisorWorkerError.jobId

        val advisorJob = advisorJobRepository.get(jobId)

        if (advisorJob == null) {
            log.warn("Failed to handle 'AdvisorWorkerError' message. No advisor job ORT run '$jobId' found.")
            return
        }

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
    }

    /**
     * Handle messages of the type [ScannerWorkerResult].
     */
    fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        val jobId = scannerWorkerResult.jobId

        val scannerJob = scannerJobRepository.get(jobId)

        if (scannerJob == null) {
            log.warn("Failed to handle 'ScannerWorkerResult' message. No scanner job '$jobId' found.")
            return
        }

        scannerJobRepository.update(
            id = scannerJob.id,
            finishedAt = Clock.System.now().asPresent(),
            status = JobStatus.FINISHED.asPresent()
        )

        val ortRun = ortRunRepository.get(scannerJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'ScannerWorkerResult' message. No ORT run '${scannerJob.ortRunId}' found.")
            return
        }

        // Create an evaluator job only if the advisor and scanner jobs have finished successfully.
        if (advisorJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
            if (ortRun.jobs.evaluator != null) {
                createEvaluatorJob(ortRun)?.let {
                    scheduleEvaluatorJob(it, header)
                }
            } else {
                createReporterJob(ortRun)?.let {
                    scheduleReporterJob(it, header)
                }
            }
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerError].
     */
    fun handleScannerWorkerError(scannerWorkerError: ScannerWorkerError) {
        val jobId = scannerWorkerError.jobId

        val scannerJob = scannerJobRepository.get(jobId)

        if (scannerJob == null) {
            log.warn("Failed to handle 'ScannerWorkerError' message. No advisor job ORT run '$jobId' found.")
            return
        }

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
    }

    /**
     * Handle messages of the type [EvaluatorWorkerResult].
     */
    fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        val jobId = evaluatorWorkerResult.jobId

        val evaluatorJob = evaluatorJobRepository.get(jobId)

        if (evaluatorJob == null) {
            log.warn("Failed to handle 'EvaluatorWorkerResult' message. No evaluator job '$jobId' found.")
            return
        }

        evaluatorJobRepository.update(
            id = evaluatorJob.id,
            finishedAt = Clock.System.now().asPresent(),
            status = JobStatus.FINISHED.asPresent()
        )

        val ortRun = ortRunRepository.get(evaluatorJob.ortRunId)

        if (ortRun == null) {
            log.warn("Failed to handle 'EvaluatorWorkerResult' message. No ORT run '${evaluatorJob.ortRunId}' found.")
            return
        }

        createReporterJob(ortRun)?.let {
            scheduleReporterJob(it, header)
        }
    }

    /**
     * Handle messages of the type [EvaluatorWorkerError].
     */
    fun handleEvaluatorWorkerError(evaluatorWorkerError: EvaluatorWorkerError) {
        val jobId = evaluatorWorkerError.jobId

        val evaluatorJob = evaluatorJobRepository.get(jobId)

        if (evaluatorJob == null) {
            log.warn("Failed to handle 'EvaluatorWorkerError' message. No evaluator job ORT run '$jobId' found.")
            return
        }

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
    }

    /**
     * Handle messages of the type [ReporterWorkerResult].
     */
    fun handleReporterWorkerResult(reporterWorkerResult: ReporterWorkerResult) {
        val jobId = reporterWorkerResult.jobId

        val reporterJob = reporterJobRepository.get(jobId)

        if (reporterJob == null) {
            log.warn("Failed to handle 'ReporterWorkerResult' message. No reporter job '$jobId' found.")
            return
        }

        reporterJobRepository.update(
            id = reporterJob.id,
            finishedAt = Clock.System.now().asPresent(),
            status = JobStatus.FINISHED.asPresent()
        )
    }

    /**
     * Handle messages of the type [ReporterWorkerError].
     */
    fun handleReporterWorkerError(reporterWorkerError: ReporterWorkerError) {
        val jobId = reporterWorkerError.jobId

        val reporterJob = reporterJobRepository.get(jobId)

        if (reporterJob == null) {
            log.warn("Failed to handle 'ReporterWorkerError' message. No reporter job ORT run '$jobId' found.")
            return
        }

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
    }

    /**
     * Create an [AnalyzerJob].
     */
    private fun createAnalyzerJob(ortRun: OrtRun): AnalyzerJob =
        analyzerJobRepository.create(
            ortRun.id,
            ortRun.jobs.analyzer
        )

    /**
     * Publish a message to the [AnalyzerEndpoint] and update the [analyzerJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleAnalyzerJob(analyzerJob: AnalyzerJob, header: MessageHeader) {
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
    }

    /**
     * Create an [AdvisorJob] if it is enabled.
     */
    private fun createAdvisorJob(ortRun: OrtRun): AdvisorJob? =
        ortRun.jobs.advisor?.let { advisorJobConfiguration ->
            advisorJobRepository.create(ortRun.id, advisorJobConfiguration)
        }

    /**
     * Publish a message to the [AdvisorEndpoint] and update the [advisorJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleAdvisorJob(advisorJob: AdvisorJob, analyzerJob: AnalyzerJob, header: MessageHeader) {
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

    /**
     * Create a [ScannerJob] if it is enabled.
     */
    private fun createScannerJob(ortRun: OrtRun): ScannerJob? =
        ortRun.jobs.scanner?.let { scannerJobConfiguration ->
            scannerJobRepository.create(ortRun.id, scannerJobConfiguration)
        }

    /**
     * Publish a message to the [ScannerEndpoint] and update the [scannerJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleScannerJob(scannerJob: ScannerJob, header: MessageHeader) {
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
     * Create an [EvaluatorJob] if it is enabled.
     */
    private fun createEvaluatorJob(ortRun: OrtRun): EvaluatorJob? =
        ortRun.jobs.evaluator?.let { evaluatorJobConfiguration ->
            evaluatorJobRepository.create(ortRun.id, evaluatorJobConfiguration)
        }

    /**
     * Publish a message to the [EvaluatorEndpoint] and update the [evaluatorJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleEvaluatorJob(evaluatorJob: EvaluatorJob, header: MessageHeader) {
        publisher.publish(
            to = EvaluatorEndpoint,
            message = Message(header = header, payload = EvaluatorRequest(evaluatorJob.id))
        )

        evaluatorJobRepository.update(
            id = evaluatorJob.id,
            startedAt = Clock.System.now().asPresent(),
            status = JobStatus.SCHEDULED.asPresent()
        )
    }

    /**
     * Create a [ReporterJob] if it is enabled.
     */
    private fun createReporterJob(ortRun: OrtRun): ReporterJob? =
        ortRun.jobs.reporter?.let { reporterJobConfiguration ->
            reporterJobRepository.create(ortRun.id, reporterJobConfiguration)
        }

    /**
     * Publish a message to the [ReporterEndpoint] and update the [reporterJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleReporterJob(reporterJob: ReporterJob, header: MessageHeader) {
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
