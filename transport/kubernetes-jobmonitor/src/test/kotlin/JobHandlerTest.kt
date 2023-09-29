/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobCondition
import io.kubernetes.client.openapi.models.V1JobList
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.io.IOException
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

import org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor.JobHandler.Companion.isCompleted
import org.ossreviewtoolkit.server.transport.kubernetes.jobmonitor.JobHandler.Companion.isFailed

private const val NAMESPACE = "EventHandlerNamespace"

class JobHandlerTest : WordSpec({
    "deleteJob" should {
        "delete a job with all its pods" {
            val podNames = listOf("pod1", "pod2", "andAnotherPod")
            val jobName = "jobToDelete"
            val job = createJob(jobName)

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val podList = V1PodList().apply { items = podNames.map(::createPod) }
            every {
                coreApi.listNamespacedPod(
                    NAMESPACE,
                    null,
                    null,
                    null,
                    null,
                    "job-name=$jobName",
                    null,
                    null,
                    null,
                    null,
                    false
                )
            } returns podList
            every { coreApi.deleteNamespacedPod(any(), any(), any(), any(), any(), any(), any(), any()) } returns null
            every { jobApi.deleteNamespacedJob(any(), any(), any(), any(), any(), any(), any(), any()) } returns null

            val handler = JobHandler(jobApi, coreApi, NAMESPACE)

            handler.deleteJob(job)

            verify {
                podNames.forAll {
                    coreApi.deleteNamespacedPod(it, NAMESPACE, null, null, null, null, null, null)
                }

                jobApi.deleteNamespacedJob(jobName, NAMESPACE, null, null, null, null, null, null)
            }
        }

        "ignore a job without a name in metadata" {
            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val handler = JobHandler(jobApi, coreApi, NAMESPACE)

            handler.deleteJob(V1Job())

            verify(exactly = 0) {
                jobApi.deleteNamespacedJob(any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

        "handle exceptions when deleting elements" {
            val podNames = listOf("pod1", "pod2", "andAnotherPod")
            val jobName = "jobToDelete"

            val coreApi = mockk<CoreV1Api>()
            val jobApi = mockk<BatchV1Api>()

            val podList = V1PodList().apply { items = podNames.map(::createPod) }
            every {
                coreApi.listNamespacedPod(
                    NAMESPACE,
                    null,
                    null,
                    null,
                    null,
                    "job-name=$jobName",
                    null,
                    null,
                    null,
                    null,
                    false
                )
            } returns podList
            every {
                coreApi.deleteNamespacedPod(any(), any(), any(), any(), any(), any(), any(), any())
            } throws IOException("Test exception when deleting pod.")
            every {
                jobApi.deleteNamespacedJob(any(), any(), any(), any(), any(), any(), any(), any())
            } throws IOException("Test exception when deleting job.")

            val handler = JobHandler(jobApi, coreApi, NAMESPACE)

            handler.deleteJob(jobName)

            verify {
                podNames.forAll {
                    coreApi.deleteNamespacedPod(it, NAMESPACE, null, null, null, null, null, null)
                }

                jobApi.deleteNamespacedJob(jobName, NAMESPACE, null, null, null, null, null, null)
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

            val jobList = V1JobList().apply { items = listOf(matchJob1, runningJob, youngJob, matchJob2, matchJob3) }
            every {
                jobApi.listNamespacedJob(
                    NAMESPACE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
                )
            } returns jobList

            val handler = JobHandler(jobApi, coreApi, NAMESPACE)
            val jobs = handler.findJobsCompletedBefore(referenceTime)

            jobs shouldContainExactlyInAnyOrder listOf(matchJob1, matchJob2, matchJob3)
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
})

/**
 * Create a [V1Job] with the given [name] and [status].
 */
private fun createJob(name: String, status: V1JobStatus = V1JobStatus()): V1Job =
    V1Job().apply {
        metadata = createMetadata(name)
        status(status)
    }

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
