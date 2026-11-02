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

package org.eclipse.apoapsis.ortserver.orchestrator

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.WorkerJobRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.asPresent

/**
 * A class that encapsulates all database access of the Orchestrator. It reads and updates ORT runs and the jobs of
 * their workers.
 */
internal class RunStore(
    /** The repositories for the worker jobs. */
    private val workerJobRepositories: WorkerJobRepositories,

    /** The repository for repositories, used to validate new runs. */
    private val repositoryRepository: RepositoryRepository,

    /** The repository for ORT runs. */
    private val ortRunRepository: OrtRunRepository
) {
    /** Make sure that the repository with the given [repositoryId] exists or fail with an exception. */
    suspend fun requireRepository(repositoryId: Long) {
        requireNotNull(repositoryRepository.get(repositoryId)) {
            "Repository '$repositoryId' not found."
        }
    }

    /** Return the [OrtRun] with the given [ortRunId] or fail with an exception if it does not exist. */
    suspend fun getOrtRun(ortRunId: Long): OrtRun =
        requireNotNull(ortRunRepository.get(ortRunId)) {
            "ORT run '$ortRunId' not found."
        }

    /**
     * Load the [RunState] of the given [run] from the status of its jobs. The [configWorkerFailed] flag must be set if
     * the Config worker failed, as this cannot be derived from the jobs.
     */
    suspend fun loadRunState(run: OrtRun, configWorkerFailed: Boolean = false): RunState {
        val jobStatuses = WorkerScheduleInfo.entries.mapNotNull { worker ->
            repository(worker).getForOrtRun(run.id)?.let { worker to it.status }
        }.toMap()

        return RunState(run.effectiveJobConfigs(), jobStatuses, configWorkerFailed)
    }

    /**
     * Create a job for the given [worker] for the given [run]. Return *null* if the worker is not configured for the
     * run.
     */
    suspend fun createJob(run: OrtRun, worker: WorkerScheduleInfo): WorkerJob? {
        val configs = run.effectiveJobConfigs()

        return when (worker) {
            WorkerScheduleInfo.ANALYZER -> workerJobRepositories.analyzerJobRepository.create(run.id, configs.analyzer)

            WorkerScheduleInfo.ADVISOR ->
                configs.advisor?.let { workerJobRepositories.advisorJobRepository.create(run.id, it) }

            WorkerScheduleInfo.SCANNER ->
                configs.scanner?.let { workerJobRepositories.scannerJobRepository.create(run.id, it) }

            WorkerScheduleInfo.EVALUATOR ->
                configs.evaluator?.let { workerJobRepositories.evaluatorJobRepository.create(run.id, it) }

            WorkerScheduleInfo.REPORTER ->
                configs.reporter?.let { workerJobRepositories.reporterJobRepository.create(run.id, it) }

            WorkerScheduleInfo.NOTIFIER ->
                configs.notifier?.let { workerJobRepositories.notifierJobRepository.create(run.id, it) }
        }
    }

    /** Set the status of the job with the given [jobId] for the given [worker] to [JobStatus.SCHEDULED]. */
    suspend fun markJobScheduled(worker: WorkerScheduleInfo, jobId: Long) {
        repository(worker).update(id = jobId, status = JobStatus.SCHEDULED.asPresent())
    }

    /**
     * Complete the job with the given [jobId] for the given [worker] with the given [status] and optional
     * [errorMessage]. Return the job as it was before the update or fail with an exception if it does not exist.
     */
    suspend fun completeJob(
        worker: WorkerScheduleInfo,
        jobId: Long,
        status: JobStatus,
        errorMessage: String? = null
    ): WorkerJob {
        val repository = repository(worker)

        return requireNotNull(repository.get(jobId)) {
            "Job for endpoint '${worker.endpoint.configPrefix}' with ID '$jobId' not found."
        }.also {
            repository.complete(jobId, Clock.System.now(), status, errorMessage)
        }
    }

    /**
     * Mark the job for the given [worker] of the run with the given [ortRunId] as failed. Return *false* if the job
     * had already been completed before, so that its status was not changed.
     */
    suspend fun tryFailJob(worker: WorkerScheduleInfo, ortRunId: Long): Boolean {
        val repository = repository(worker)
        val job = requireNotNull(repository.getForOrtRun(ortRunId)) {
            "ORT run '$ortRunId' not found."
        }

        return repository.tryComplete(job.id, Clock.System.now(), JobStatus.FAILED) != null
    }

    /** Set the status of the run with the given [ortRunId] to the given [status]. */
    suspend fun setRunStatus(ortRunId: Long, status: OrtRunStatus) {
        ortRunRepository.update(ortRunId, status.asPresent())
    }

    /** Add the given [issues] to the run with the given [ortRunId]. */
    suspend fun addIssues(ortRunId: Long, issues: List<Issue>) {
        ortRunRepository.update(ortRunId, issues = issues.asPresent())
    }

    /** Mark the run with the given [ortRunId] as failed because of the given [issue]. */
    suspend fun failRun(ortRunId: Long, issue: Issue) {
        ortRunRepository.update(
            id = ortRunId,
            status = OrtRunStatus.FAILED.asPresent(),
            issues = listOf(issue).asPresent()
        )
    }

    /**
     * Set the final [status] of the run with the given [ortRunId] and clean up data of its jobs that is no longer
     * needed, i.e., the mail recipients of the notifier job.
     */
    suspend fun finishRun(ortRunId: Long, status: OrtRunStatus) {
        workerJobRepositories.notifierJobRepository.getForOrtRun(ortRunId)?.let { notifierJob ->
            workerJobRepositories.notifierJobRepository.deleteMailRecipients(notifierJob.id)
        }

        setRunStatus(ortRunId, status)
    }

    /** Return the [WorkerJobRepository] for the given [worker]. */
    private fun repository(worker: WorkerScheduleInfo): WorkerJobRepository<*> = workerJobRepositories[worker]
}

/** Return the resolved job configurations of this run if available; otherwise, return the original ones. */
internal fun OrtRun.effectiveJobConfigs(): JobConfigurations = resolvedJobConfigs ?: jobConfigs
