/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.notifier

import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.WorkerJob
import org.eclipse.apoapsis.ortserver.services.ortrun.OrtRunService
import org.eclipse.apoapsis.ortserver.transport.AdvisorEndpoint
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EvaluatorEndpoint
import org.eclipse.apoapsis.ortserver.transport.ReporterEndpoint
import org.eclipse.apoapsis.ortserver.transport.ScannerEndpoint

import org.ossreviewtoolkit.model.OrtResult

/**
 * An internally used helper class to generate an [OrtResult] the Notifier can operate on from the current [OrtRun].
 *
 * This class loads the results of the previous worker steps and generates an [OrtResult] from them. In addition, it
 * adds a number of labels that can be evaluated by the notifier script to obtain further information about the run.
 * The following labels are added:
 * - emailRecipients: A list of email addresses separated by ';' that should receive a notification mail.
 * - report_&lt;name&gt;: For each report generated for this run, a label with the name of the report is added.
 *   The value is the link under which the report can be downloaded. So, these links could be included in a
 *   notification mail.
 * - &lt;worker&gt;JobStatus: Labels of this category allow finding out which worker jobs have been executed and their
 *   status. The values correspond to the names of the constants from the [JobStatus] enum. For instance, a label
 *   _analyzerJobStatus_ with the value _FINISHED_ means that the Analyzer job has been executed successfully.
 * - runId: The id of the current [OrtRun].
 */
internal class NotifierOrtResultGenerator(
    /** Reference to the service to access the current ORT run. */
    private val ortRunService: OrtRunService
) {
    companion object {
        /** The prefix for labels that represent the download links for reports. */
        private const val REPORT_LABEL_PREFIX = "report_"

        /** The suffix for labels that represent the status of a worker job. */
        private const val JOB_STATUS_LABEL_SUFFIX = "JobStatus"

        /** The label for the email recipients. */
        private const val EMAIL_RECIPIENTS_LABEL = "emailRecipients"

        /** The separator for multiple email recipients. */
        private const val RECIPIENTS_SEPARATOR = ";"
    }

    /** A map with functions to retrieve the job status for each worker endpoint. */
    private val jobStatusFunctions: Map<Endpoint<*>, (Long) -> WorkerJob?> = mapOf(
        AnalyzerEndpoint to ortRunService::getAnalyzerJobForOrtRun,
        AdvisorEndpoint to ortRunService::getAdvisorJobForOrtRun,
        ScannerEndpoint to ortRunService::getScannerJobForOrtRun,
        EvaluatorEndpoint to ortRunService::getEvaluatorJobForOrtRun,
        ReporterEndpoint to ortRunService::getReporterJobForOrtRun
    )

    /**
     * Generate an [OrtResult] from the given [ortRun] object based on the given [notifierJob].
     */
    fun generateOrtResult(ortRun: OrtRun, notifierJob: NotifierJob): OrtResult {
        val baseResult = ortRunService.generateOrtResult(ortRun, failIfRepoInfoMissing = false)

        // Add other labels that are specific to the Notifier.
        val labelsToAdd = getLabelsForReportDownloadLinks(ortRun) +
                getMailRecipientsLabels(notifierJob) +
                getJobStatusLabels(ortRun)

        return baseResult.takeIf { labelsToAdd.isEmpty() }
            ?: baseResult.copy(labels = baseResult.labels + labelsToAdd)
    }

    /**
     * Return a map with labels that represent the direct download links for the reports generated for the given
     * [ortRun].
     */
    private fun getLabelsForReportDownloadLinks(ortRun: OrtRun): Map<String, String> =
        ortRunService.getDownloadLinksForOrtRun(ortRun.id).associate {
            "$REPORT_LABEL_PREFIX${it.filename}" to it.downloadLink
        }

    /**
     * Return a map with labels that can be queried from notifier scripts in order to obtain the recipients of
     * notification mails to be sent.
     * TODO: ORT's Notifier API should be changed to support the email addresses, and not require to handle this
     *       information as a label in the ORT result.
     */
    private fun getMailRecipientsLabels(notifierJob: NotifierJob): Map<String, String> {
        return notifierJob.configuration.mail?.recipientAddresses?.let { recipients ->
            mapOf(EMAIL_RECIPIENTS_LABEL to recipients.joinToString(RECIPIENTS_SEPARATOR))
        }.orEmpty()
    }

    /**
     * Return a map with labels that contain information about the worker jobs executed for the given [ortRun].
     */
    private fun getJobStatusLabels(ortRun: OrtRun): Map<String, String> {
        val jobStatusLabels = mutableMapOf<String, String>()

        jobStatusFunctions.forEach { (endpoint, getJobFunction) ->
            val job = getJobFunction(ortRun.id)
            if (job != null) {
                jobStatusLabels["${endpoint.configPrefix}$JOB_STATUS_LABEL_SUFFIX"] = job.status.name
            }
        }

        return jobStatusLabels
    }
}
