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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1ContainerState
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1ContainerStatus
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobCondition
import io.kubernetes.client.openapi.models.V1JobList
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import java.io.IOException
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay

import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.isCompleted
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.isFailed
import org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes.JobHandler.Companion.isTimeout
import org.eclipse.apoapsis.ortserver.transport.AnalyzerEndpoint

private const val NAMESPACE = "EventHandlerNamespace"

class JobHandlerTest : WordSpec({
    "deleteAndNotifyIfFailed" should {
        "delete a job with all its pods" {
            val podNames = listOf("pod1", "pod2", "andAnotherPod")
            val jobName = "jobToDelete"
            val job = createJobData(jobName)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            coreApi.mockPodList(jobName, podNames.map(::createPod))

            every { coreApi.deleteNamespacedPod(any(), any()) } returns null
            every { jobApi.deleteNamespacedJob(any(), any()) } returns null

            val handler = createJobHandler(jobApi, coreApi)

            handler.deleteAndNotifyIfFailed(job)

            verify {
                podNames.forAll {
                    coreApi.deleteNamespacedPod(it, NAMESPACE)
                }

                jobApi.deleteNamespacedJob(jobName, NAMESPACE)
            }
        }

        "ignore a job without a name in metadata" {
            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val handler = createJobHandler(jobApi, coreApi)

            handler.deleteAndNotifyIfFailed(JobData(V1Job(), failed = false))

            verify(exactly = 0) {
                jobApi.deleteNamespacedJob(any(), any())
            }
        }

        "handle exceptions when deleting elements" {
            val podNames = listOf("pod1", "pod2", "andAnotherPod")
            val jobName = "jobToDelete"
            val job = createJobData(jobName)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            coreApi.mockPodList(jobName, podNames.map(::createPod))

            every { coreApi.deleteNamespacedPod(any(), any()) } throws IOException("Test exception when deleting pod.")
            every { jobApi.deleteNamespacedJob(any(), any()) } throws IOException("Test exception when deleting job.")

            val handler = createJobHandler(jobApi, coreApi)

            handler.deleteAndNotifyIfFailed(job)

            verify {
                podNames.forAll {
                    coreApi.deleteNamespacedPod(it, NAMESPACE)
                }

                jobApi.deleteNamespacedJob(jobName, NAMESPACE)
            }
        }

        "delete a failed job and trigger a notification" {
            val podNames = listOf("pod1", "pod2", "andAnotherPod")
            val jobName = "jobToDelete"
            val job = createJobData(jobName, failed = true)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            coreApi.mockPodList(jobName, podNames.map(::createPod))

            every { coreApi.deleteNamespacedPod(any(), any()) } returns null
            every { jobApi.deleteNamespacedJob(any(), any()) } returns null

            val notifier = mockk<FailedJobNotifier> {
                every { sendFailedJobNotification(job.job) } just runs
            }

            val handler = createJobHandler(jobApi, coreApi, notifier)

            handler.deleteAndNotifyIfFailed(job)

            verify {
                notifier.sendFailedJobNotification(job.job)

                podNames.forAll {
                    coreApi.deleteNamespacedPod(it, NAMESPACE)
                }

                jobApi.deleteNamespacedJob(jobName, NAMESPACE)
            }
        }

        "not delete a failed job if sending the notification fails" {
            val jobName = "failedJobWithNotificationProblem"
            val job = createJobData(jobName, failed = true)

            val notifier = mockk<FailedJobNotifier> {
                every { sendFailedJobNotification(any()) } throws IllegalStateException("Test exception")
            }

            val jobApi = mockk<BatchV1Api>()
            val handler = createJobHandler(jobApi = jobApi, notifier = notifier)

            handler.deleteAndNotifyIfFailed(job)

            verify(exactly = 0) {
                jobApi.deleteNamespacedJob(any(), any())
            }
        }

        "skip jobs that have already been processed recently" {
            val jobName = "jobToDeleteOnlyOnce"
            val job = createJobData(jobName, failed = true)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            coreApi.mockPodList(jobName, emptyList())

            every { coreApi.deleteNamespacedPod(any(), any()) } returns null

            val notifier = mockk<FailedJobNotifier> {
                every { sendFailedJobNotification(job.job) } just runs
            }

            val handler = createJobHandler(jobApi, coreApi, notifier)
            handler.deleteAndNotifyIfFailed(job)

            handler.deleteAndNotifyIfFailed(job)

            verify(exactly = 1) {
                notifier.sendFailedJobNotification(job.job)

                jobApi.deleteNamespacedJob(jobName, NAMESPACE)
            }
        }

        "clean up the recently processed jobs" {
            val jobName = "jobToDeleteMultipleTimes"
            val job = createJobData(jobName, failed = true)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val jobsToList = listOf<Pair<String, List<V1Pod>>>(
                jobName to emptyList(),
                "anotherJob" to emptyList(),
                "oneMoreJob" to emptyList(),
                jobName to emptyList()
            )
            coreApi.mockPodLists(jobsToList)

            every { coreApi.deleteNamespacedPod(any(), any()) } returns null

            val notifier = mockk<FailedJobNotifier> {
                every { sendFailedJobNotification(job.job) } just runs
            }

            val handler = createJobHandler(jobApi, coreApi, notifier, 1.milliseconds)
            handler.deleteAndNotifyIfFailed(job)
            handler.deleteAndNotifyIfFailed(createJobData("anotherJob"))
            handler.deleteAndNotifyIfFailed(createJobData("oneMoreJob"))

            delay(2.milliseconds)
            handler.deleteAndNotifyIfFailed(job)

            verify(exactly = 2) {
                notifier.sendFailedJobNotification(job.job)

                jobApi.deleteNamespacedJob(jobName, NAMESPACE)
            }
        }
    }

    "findJobsCompletedBefore" should {
        "return a list of jobs matching the condition" {
            val referenceTime = OffsetDateTime.of(2023, Month.MAY.value, 2, 8, 49, 7, 0, ZoneOffset.ofHours(2))
            val runningJob = createJob("runningJob")
            val youngJob = createJob(
                "youngster_job",
                status = V1JobStatus().apply { completionTime = referenceTime }
            )
            val matchJob1 = createJob(
                "a_job",
                status = V1JobStatus().apply { completionTime = referenceTime.minusSeconds(1) }
            )
            val matchJob2 = createJob(
                "old_job",
                status = V1JobStatus().apply { completionTime = referenceTime.minusDays(1) }
            )
            val matchJob3 = createJob(
                "failedJob",
                status = V1JobStatus().apply {
                    addConditionsItem(V1JobCondition().apply { type("Failed") })
                }
            )

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val jobList = listOf(matchJob1, runningJob, youngJob, matchJob2, matchJob3)
            val jobsWithPods = listOf(runningJob, youngJob, matchJob2, matchJob3)
            coreApi.mockPodLists(
                jobsWithPods.map { (it.metadata?.name.orEmpty()) to emptyList() }
            )

            jobApi.mockJobList(jobList)

            val handler = createJobHandler(jobApi, coreApi)
            val jobs = handler.findJobsCompletedBefore(referenceTime)

            val expectedData = listOf(
                JobData(matchJob1, failed = false),
                JobData(matchJob2, failed = false),
                JobData(matchJob3, failed = true)
            )
            jobs should containExactlyInAnyOrder(expectedData)
        }

        "query only jobs for ORT Server workers" {
            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val jobList = listOf(createJob("testJob"))
            val request = jobApi.mockJobList(jobList)

            coreApi.mockPodList("testJob", emptyList())

            val handler = createJobHandler(jobApi, coreApi)
            handler.findJobsCompletedBefore(OffsetDateTime.now())

            val slotLabel = slot<String>()
            verify {
                jobApi.listNamespacedJob(NAMESPACE)
                request.labelSelector(capture(slotLabel))
            }

            val labelSelectorRegex = Regex("""ort-worker in \((.+)\)""")
            labelSelectorRegex.matchEntire(slotLabel.captured) shouldNotBeNull {
                val workers = groupValues[1].split(',')
                workers should containExactlyInAnyOrder(
                    "advisor",
                    "analyzer",
                    "config",
                    "evaluator",
                    "notifier",
                    "orchestrator",
                    "reporter",
                    "scanner"
                )
            }
        }

        "include jobs with pods in failure state" {
            val referenceTime = OffsetDateTime.of(2026, Month.JULY.value, 7, 11, 41, 5, 0, ZoneOffset.ofHours(2))
            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val failedJob = createJob("failedJob")
            val failedPod = createPod("failedPod").apply {
                status = V1PodStatus().apply {
                    containerStatuses = listOf(
                        V1ContainerStatus().apply {
                            state = V1ContainerState().apply {
                                terminated = V1ContainerStateTerminated()
                                    .apply { exitCode = 1 }
                            }
                        }
                    )
                }
            }

            val otherJob = createJob("otherJob")
            val otherPod = createPod("otherPod").apply {
                status = V1PodStatus().apply {
                    containerStatuses = listOf(
                        V1ContainerStatus().apply {
                            state = V1ContainerState().apply {
                                terminated = V1ContainerStateTerminated()
                                    .apply { exitCode = 0 }
                            }
                        },
                        V1ContainerStatus()
                    )
                }
            }

            coreApi.mockPodLists(
                listOf("otherJob" to listOf(otherPod), "failedJob" to listOf(failedPod))
            )
            jobApi.mockJobList(listOf(otherJob, failedJob))

            val handler = createJobHandler(jobApi, coreApi)
            val jobs = handler.findJobsCompletedBefore(referenceTime)

            jobs should containExactlyInAnyOrder(JobData(failedJob, failed = true))
        }
    }

    "findJobsForWorker" should {
        "return a list of jobs for the given worker" {
            val job1 = createJob("job1")
            val job2 = createJob("job2")

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val jobList = V1JobList().apply { items = listOf(job1, job2) }

            val request = mockk<BatchV1Api.APIlistNamespacedJobRequest> {
                every { execute() } returns jobList
            }

            every { jobApi.listNamespacedJob(NAMESPACE) } returns request
            every { request.labelSelector("ort-worker=analyzer") } returns request
            every { request.watch(false) } returns request

            val handler = createJobHandler(jobApi, coreApi)
            val jobs = handler.findJobsForWorker(AnalyzerEndpoint)

            jobs should containExactlyInAnyOrder(job1, job2)
        }
    }

    "isFailed" should {
        "return true for a failed job" {
            val failedStatus = V1JobStatus().apply {
                addConditionsItem(
                    V1JobCondition().apply { type("someType") }
                )
                addConditionsItem(
                    V1JobCondition().apply { type("Failed") }
                )
            }
            val job = createJob("failedJob", failedStatus)

            job.isFailed() shouldBe true
        }

        "return false for a job that was completed normally" {
            val completedStatus = V1JobStatus().apply {
                addConditionsItem(
                    V1JobCondition().apply { type("Complete") }
                )
            }
            val job = createJob("completedJob", completedStatus)

            job.isFailed() shouldBe false
        }

        "return false for a job without any conditions" {
            val runningJob = createJob("ongoing")

            runningJob.isFailed() shouldBe false
        }
    }

    "isCompleted" should {
        "return true for a job with a completion date" {
            val status = V1JobStatus().apply { completionTime = OffsetDateTime.now() }
            val completedJob = createJob("iAmDone", status)

            completedJob.isCompleted() shouldBe true
        }

        "return false for a job without a matching condition" {
            val status = V1JobStatus().apply {
                addConditionsItem(
                    V1JobCondition().apply { type("someType") }
                )
            }
            val runningJob = createJob("runningJob", status)

            runningJob.isCompleted() shouldBe false
        }

        "return true for a job with a Complete condition" {
            val status = V1JobStatus().apply {
                addConditionsItem(
                    V1JobCondition().apply { type("Complete") }
                )
            }
            val completeJob = createJob("completeJob", status)

            completeJob.isCompleted() shouldBe true
        }

        "return true for a job with a Failed condition" {
            val status = V1JobStatus().apply {
                addConditionsItem(
                    V1JobCondition().apply { type("Failed") }
                )
            }
            val failedJob = createJob("failedJob", status)

            failedJob.isCompleted() shouldBe true
        }
    }

    "isTimeout" should {
        "return false for a completed job" {
            val startTime = OffsetDateTime.now().minusMinutes(10)
            val threshold = OffsetDateTime.now().minusMinutes(5)
            val completionTime = OffsetDateTime.now()

            val status = V1JobStatus().apply {
                this.startTime = startTime
                this.completionTime = completionTime
            }
            val job = createJob("completedJob", status)

            job.isTimeout(threshold) shouldBe false
        }

        "return true if the timeout was reached" {
            val startTime = OffsetDateTime.now().minusMinutes(10)
            val threshold = OffsetDateTime.now().minusMinutes(5)

            val status = V1JobStatus().apply { this.startTime = startTime }
            val job = createJob("timeoutJob", status)

            job.isTimeout(threshold) shouldBe true
        }

        "return false if the job was not created before the threshold" {
            val startTime = OffsetDateTime.now().minusMinutes(10)
            val threshold = OffsetDateTime.now().minusMinutes(11)

            val status = V1JobStatus().apply { this.startTime = startTime }
            val job = createJob("runningJob", status)

            job.isTimeout(threshold) shouldBe false
        }

        "return false for a job without a start time" {
            val job = createJob("strangeJob")

            job.isTimeout(OffsetDateTime.now()) shouldBe false
        }
    }
})

/**
 * Create a [JobHandler] instance with default dependencies that can be overridden if needed.
 */
private fun createJobHandler(
    jobApi: BatchV1Api = mockk(),
    api: CoreV1Api = mockk(),
    notifier: FailedJobNotifier = mockk(),
    recentlyProcessedInterval: Duration = 30.seconds
): JobHandler =
    JobHandler(jobApi, api, notifier, createMonitorConfig(recentlyProcessedInterval))

/**
 * Create a [MonitorConfig] with default values and the given [processedInterval].
 */
private fun createMonitorConfig(processedInterval: Duration): MonitorConfig =
    mockk {
        every { namespace } returns NAMESPACE
        every { recentlyProcessedInterval } returns processedInterval
    }

/**
 * Create a [V1Job] with the given [name] and [status].
 */
private fun createJob(name: String, status: V1JobStatus = V1JobStatus()): V1Job =
    V1Job().apply {
        metadata = createMetadata(name)
        status(status)
    }

/**
 * Create a [JobData] object containing a [V1Job] with the given [name], and the given [failed] flag.
 */
private fun createJobData(name: String, failed: Boolean = false): JobData =
    JobData(
        job = createJob(name),
        failed = failed
    )

/**
 * Create a [V1Job] object with the given [name].
 */
private fun createPod(name: String): V1Pod =
    V1Pod().apply {
        metadata = createMetadata(name)
    }

/**
 * Create a [V1ObjectMeta] object with the given [name].
 */
private fun createMetadata(name: String): V1ObjectMeta =
    V1ObjectMeta().apply {
        name(name)
    }

/**
 * Prepare this API mock to expect a list operation for the pods of the job with the given [jobName]. The operation
 * shall return a [V1PodList] containing the given [pods].
 */
private fun CoreV1Api.mockPodList(jobName: String, pods: Collection<V1Pod>) {
    mockPodLists(listOf(jobName to pods))
}

/**
 * Prepare this API mock to expect multiple list operations for the pods of jobs as defined by the given [jobs]
 * collection.
 */
private fun CoreV1Api.mockPodLists(jobs: Collection<Pair<String, Collection<V1Pod>>>) {
    val requests = jobs.map { (jobName, pods) ->
        val podList = V1PodList().apply { items = pods.toList() }

        val request = mockk<CoreV1Api.APIlistNamespacedPodRequest> {
            every { execute() } returns podList
        }

        every { request.labelSelector("job-name=$jobName") } returns request
        every { request.watch(false) } returns request
        request
    }

    every { listNamespacedPod(NAMESPACE) } returnsMany requests
}

/**
 * Prepare this [BatchV1Api] mock to expect a list operation for jobs and return a [V1JobList] containing the given
 * [jobs].
 */
private fun BatchV1Api.mockJobList(jobs: Collection<V1Job>): BatchV1Api.APIlistNamespacedJobRequest {
    val jobList = V1JobList().apply { items = jobs.toList() }

    val request = mockk<BatchV1Api.APIlistNamespacedJobRequest> {
        every { execute() } returns jobList
    }

    every { listNamespacedJob(NAMESPACE) } returns request
    every { request.labelSelector(any()) } returns request
    every { request.watch(false) } returns request

    return request
}
