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

import org.ossreviewtoolkit.server.dao.blockingQuery
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
        blockingQuery {
            val ortRun = createOrtRun.ortRun

            requireNotNull(repositoryRepository.get(ortRun.repositoryId)) {
                "Repository '${ortRun.repositoryId}' not found."
            }

            CreatedJobs(
                analyzerJob = createAnalyzerJob(ortRun)
            )
        }.onSuccess { createdJobs ->
            scheduleCreatedJobs(createdJobs, header)
        }.onFailure {
            log.warn("Failed to handle 'CreateOrtRun' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerResult].
     */
    fun handleAnalyzerWorkerResult(header: MessageHeader, analyzerWorkerResult: AnalyzerWorkerResult) {
        blockingQuery {
            val jobId = analyzerWorkerResult.jobId

            val analyzerJob = requireNotNull(analyzerJobRepository.get(jobId)) {
                "Analyzer job '$jobId' not found."
            }

            analyzerJobRepository.update(
                id = analyzerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(analyzerJob.ortRunId)) {
                "ORT run '${analyzerJob.ortRunId}' not found."
            }

            val createdJobs = CreatedJobs(
                advisorJob = createAdvisorJob(ortRun),
                scannerJob = createScannerJob(ortRun)
            )

            // Create an evaluator job only if the advisor and scanner jobs have finished successfully.
            if (createdJobs.advisorJob == null && createdJobs.scannerJob == null) {
                if (ortRun.jobs.evaluator != null) {
                    // Create an evaluator job if no advisor or scanner job is configured.
                    createdJobs.evaluatorJob = createEvaluatorJob(ortRun)
                } else {
                    // Create a reporter job if no advisor, scanner or reporter job is configured.
                    createdJobs.reporterJob = createReporterJob(ortRun)
                }
            }

            createdJobs
        }.onSuccess { createdJobs ->
            scheduleCreatedJobs(createdJobs, header)
        }.onFailure {
            log.warn("Failed to handle 'AnalyzerWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAnalyzerWorkerError(analyzerWorkerError: AnalyzerWorkerError) {
        blockingQuery {
            val jobId = analyzerWorkerError.jobId

            val analyzerJob = requireNotNull(analyzerJobRepository.get(jobId)) {
                "Analyzer job '$jobId' not found."
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
        }.onFailure {
            log.warn("Failed to handle 'AnalyzerWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [AdvisorWorkerResult].
     */
    fun handleAdvisorWorkerResult(header: MessageHeader, advisorWorkerResult: AdvisorWorkerResult) {
        blockingQuery {
            val jobId = advisorWorkerResult.jobId

            val advisorJob = requireNotNull(advisorJobRepository.get(jobId)) {
                "Advisor job '$jobId' not found."
            }

            advisorJobRepository.update(
                id = advisorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(advisorJob.ortRunId)) {
                "ORT run '${advisorJob.ortRunId}' not found."
            }

            val createdJobs = CreatedJobs()

            // Create an evaluator or reporter job only if the advisor and scanner jobs have finished successfully.
            if (scannerJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
                if (ortRun.jobs.evaluator != null) {
                    createdJobs.evaluatorJob = createEvaluatorJob(ortRun)
                } else {
                    createdJobs.reporterJob = createReporterJob(ortRun)
                }
            }

            createdJobs
        }.onSuccess { createdJobs ->
            scheduleCreatedJobs(createdJobs, header)
        }.onFailure {
            log.warn("Failed to handle 'AdvisorWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [AnalyzerWorkerError].
     */
    fun handleAdvisorWorkerError(advisorWorkerError: AdvisorWorkerError) {
        blockingQuery {
            val jobId = advisorWorkerError.jobId

            val advisorJob = requireNotNull(advisorJobRepository.get(jobId)) {
                "Advisor job '$jobId' not found."
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
        }.onFailure {
            log.warn("Failed to handle 'AdvisorWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerResult].
     */
    fun handleScannerWorkerResult(header: MessageHeader, scannerWorkerResult: ScannerWorkerResult) {
        blockingQuery {
            val jobId = scannerWorkerResult.jobId

            val scannerJob = requireNotNull(scannerJobRepository.get(jobId)) {
                "Scanner job '$jobId' not found."
            }

            scannerJobRepository.update(
                id = scannerJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(scannerJob.ortRunId)) {
                "ORT run '${scannerJob.ortRunId}' not found."
            }

            val createdJobs = CreatedJobs()

            // Create an evaluator or reporter job only if the advisor and scanner jobs have finished successfully.
            if (advisorJobRepository.getForOrtRun(ortRun.id)?.let { it.status == JobStatus.FINISHED } == true) {
                if (ortRun.jobs.evaluator != null) {
                    createdJobs.evaluatorJob = createEvaluatorJob(ortRun)
                } else {
                    createdJobs.reporterJob = createReporterJob(ortRun)
                }
            }

            createdJobs
        }.onSuccess { createdJobs ->
            scheduleCreatedJobs(createdJobs, header)
        }.onFailure {
            log.warn("Failed to handle 'ScannerWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [ScannerWorkerError].
     */
    fun handleScannerWorkerError(scannerWorkerError: ScannerWorkerError) {
        blockingQuery {
            val jobId = scannerWorkerError.jobId

            val scannerJob = requireNotNull(scannerJobRepository.get(jobId)) {
                "Scanner job '$jobId' not found."
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
        }.onFailure {
            log.warn("Failed to handle 'ScannerWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [EvaluatorWorkerResult].
     */
    fun handleEvaluatorWorkerResult(header: MessageHeader, evaluatorWorkerResult: EvaluatorWorkerResult) {
        blockingQuery {
            val jobId = evaluatorWorkerResult.jobId

            val evaluatorJob = requireNotNull(evaluatorJobRepository.get(jobId)) {
                "Evaluator job '$jobId' not found."
            }

            evaluatorJobRepository.update(
                id = evaluatorJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )

            val ortRun = requireNotNull(ortRunRepository.get(evaluatorJob.ortRunId)) {
                "ORT run '${evaluatorJob.ortRunId}' not found."
            }

            CreatedJobs(
                reporterJob = createReporterJob(ortRun)
            )
        }.onSuccess { createdJobs ->
            scheduleCreatedJobs(createdJobs, header)
        }.onFailure {
            log.warn("Failed to handle 'EvaluatorWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [EvaluatorWorkerError].
     */
    fun handleEvaluatorWorkerError(evaluatorWorkerError: EvaluatorWorkerError) {
        blockingQuery {
            val jobId = evaluatorWorkerError.jobId

            val evaluatorJob = requireNotNull(evaluatorJobRepository.get(jobId)) {
                "Evaluator job '$jobId' not found."
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
        }.onFailure {
            log.warn("Failed to handle 'EvaluatorWorkerError' message.", it)
        }
    }

    /**
     * Handle messages of the type [ReporterWorkerResult].
     */
    fun handleReporterWorkerResult(reporterWorkerResult: ReporterWorkerResult) {
        blockingQuery {
            val jobId = reporterWorkerResult.jobId

            val reporterJob = requireNotNull(reporterJobRepository.get(jobId)) {
                "Reporter job '$jobId' not found."
            }

            reporterJobRepository.update(
                id = reporterJob.id,
                finishedAt = Clock.System.now().asPresent(),
                status = JobStatus.FINISHED.asPresent()
            )
        }.onFailure {
            log.warn("Failed to handle 'ReporterWorkerResult' message.", it)
        }
    }

    /**
     * Handle messages of the type [ReporterWorkerError].
     */
    fun handleReporterWorkerError(reporterWorkerError: ReporterWorkerError) {
        blockingQuery {
            val jobId = reporterWorkerError.jobId

            val reporterJob = requireNotNull(reporterJobRepository.get(jobId)) {
                "Reporter job '$jobId' not found."
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
        }.onFailure {
            log.warn("Failed to handle 'ReporterWorkerError' message.", it)
        }
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
     * Create an [AdvisorJob] if it is enabled.
     */
    private fun createAdvisorJob(ortRun: OrtRun): AdvisorJob? =
        ortRun.jobs.advisor?.let { advisorJobConfiguration ->
            advisorJobRepository.create(ortRun.id, advisorJobConfiguration)
        }

    /**
     * Create a [ScannerJob] if it is enabled.
     */
    private fun createScannerJob(ortRun: OrtRun): ScannerJob? =
        ortRun.jobs.scanner?.let { scannerJobConfiguration ->
            scannerJobRepository.create(ortRun.id, scannerJobConfiguration)
        }

    /**
     * Create an [EvaluatorJob] if it is enabled.
     */
    private fun createEvaluatorJob(ortRun: OrtRun): EvaluatorJob? =
        ortRun.jobs.evaluator?.let { evaluatorJobConfiguration ->
            evaluatorJobRepository.create(ortRun.id, evaluatorJobConfiguration)
        }

    /**
     * Create a [ReporterJob] if it is enabled.
     */
    private fun createReporterJob(ortRun: OrtRun): ReporterJob? =
        ortRun.jobs.reporter?.let { reporterJobConfiguration ->
            reporterJobRepository.create(ortRun.id, reporterJobConfiguration)
        }

    private fun scheduleCreatedJobs(createdJobs: CreatedJobs, header: MessageHeader) {
        // TODO: Handle errors during job scheduling.

        createdJobs.analyzerJob?.let { scheduleAnalyzerJob(it, header) }
        createdJobs.advisorJob?.let { scheduleAdvisorJob(it, header) }
        createdJobs.scannerJob?.let { scheduleScannerJob(it, header) }
        createdJobs.evaluatorJob?.let { scheduleEvaluatorJob(it, header) }
        createdJobs.reporterJob?.let { scheduleReporterJob(it, header) }
    }

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
     * Publish a message to the [AdvisorEndpoint] and update the [advisorJob] status to [JobStatus.SCHEDULED].
     */
    private fun scheduleAdvisorJob(advisorJob: AdvisorJob, header: MessageHeader) {
        publisher.publish(
            to = AdvisorEndpoint,
            message = Message(header = header, payload = AdvisorRequest(advisorJob.id))
        )

        advisorJobRepository.update(
            advisorJob.id,
            startedAt = Clock.System.now().asPresent(),
            status = JobStatus.SCHEDULED.asPresent()
        )
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

/**
 * A class to collect all jobs created within an orchestrator callback.
 */
private class CreatedJobs(
    var analyzerJob: AnalyzerJob? = null,
    var advisorJob: AdvisorJob? = null,
    var scannerJob: ScannerJob? = null,
    var evaluatorJob: EvaluatorJob? = null,
    var reporterJob: ReporterJob? = null
)
