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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobList
import io.kubernetes.client.openapi.models.V1ListMeta
import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.util.Watch

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import java.io.IOException

import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions

import okhttp3.Call

private const val NAMESPACE = "someTestNamespace"

class JobWatchHelperTest : StringSpec({
    beforeSpec {
        mockkStatic(Watch::class)
    }

    afterSpec {
        unmockkAll()
    }

    "An event can be obtained" {
        val resourceVersion = "rv1"
        val jobApi = createApiMock()
        val event = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.1"))
        val watch = createWatchWithEvents(event)
        val request = mockWatchCreation(jobApi, resourceVersion, watch)
        every { jobApi.listNamespacedJob(NAMESPACE) } returns request

        val helper = JobWatchHelper.create(jobApi, createConfig(), resourceVersion)

        helper.nextEvent() shouldBe event
    }

    "Only MODIFIED events are returned" {
        val resourceVersion = "rv1"
        val jobApi = createApiMock()
        val eventIgnored = Watch.Response("ADDED", createJobWithResourceVersion("rv1.1"))
        val event = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.2"))
        val watch = createWatchWithEvents(eventIgnored, event)
        val request = mockWatchCreation(jobApi, resourceVersion, watch)
        every { jobApi.listNamespacedJob(NAMESPACE) } returns request

        val helper = JobWatchHelper.create(jobApi, createConfig(), resourceVersion)

        helper.nextEvent() shouldBe event
    }

    "A new watch is created at the correct bookmark when the previous one terminates" {
        val resourceVersion1 = "rv1"
        val resourceVersion2 = "rv2"
        val jobApi = createApiMock()

        val event1 = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.1"))
        val event2 = Watch.Response("MODIFIED", createJobWithResourceVersion("rv2.1"))
        val bookmarkEvent = Watch.Response("BOOKMARK", createJobWithResourceVersion(resourceVersion2))

        val watch1 = createWatchWithEvents(event1, bookmarkEvent)
        val watch2 = createWatchWithEvents(event2)

        mockMultipleWatchCreation(
            jobApi,
            mockWatchCreation(jobApi, resourceVersion1, watch1),
            mockWatchCreation(jobApi, resourceVersion2, watch2)
        )

        val helper = JobWatchHelper.create(jobApi, createConfig(), resourceVersion1)

        helper.nextEvent() shouldBe event1
        helper.nextEvent() shouldBe event2
    }

    "Exceptions are handled" {
        val resourceVersion1 = "rv1"
        val resourceVersion2 = "rv2"
        val jobApi = createApiMock()

        val bookmarkEvent = Watch.Response("BOOKMARK", createJobWithResourceVersion(resourceVersion2))
        val errorIterator = mockk<MutableIterator<Watch.Response<V1Job>>>()
        every { errorIterator.hasNext() } returns true
        var success = true
        every { errorIterator.next() } answers {
            if (success) {
                success = false
                bookmarkEvent
            } else {
                throw IOException("Test exception")
            }
        }

        val errorWatch = mockk<Watch<V1Job>>()
        every { errorWatch.iterator() } returns errorIterator

        val event = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.1"))
        val watch = createWatchWithEvents(event)

        mockMultipleWatchCreation(
            jobApi,
            mockWatchCreation(jobApi, resourceVersion1, errorWatch),
            mockWatchCreation(jobApi, resourceVersion2, watch)
        )

        val helper = JobWatchHelper.create(jobApi, createConfig(), resourceVersion1)

        helper.nextEvent() shouldBe event
    }

    "The initial resource version is obtained if not specified" {
        val initialResourceVersion = "rvInit"

        val jobApi = createApiMock()
        jobApi.expectJobListRequests(initialResourceVersion)

        val event = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.1"))
        val watch = createWatchWithEvents(event)
        val request = mockWatchCreation(jobApi, initialResourceVersion, watch)
        every { jobApi.listNamespacedJob(NAMESPACE) } returns request

        val helper = JobWatchHelper.create(jobApi, createConfig())

        helper.nextEvent() shouldBe event
    }

    "A stalled watch iterator is detected" {
        val initialResourceVersion = "rvInitial"
        val updatedResourceVersion = "rvNext"

        val jobApi = createApiMock()
        jobApi.expectJobListRequests(initialResourceVersion, updatedResourceVersion)

        val eventIgnored = Watch.Response("ignored", createJobWithResourceVersion("x1"))
        val watch1 = createWatchWithEvents(eventIgnored)

        val event = Watch.Response("MODIFIED", createJobWithResourceVersion("rv1.1"))
        val watch2 = createWatchWithEvents(event)

        mockMultipleWatchCreation(
            jobApi,
            mockWatchCreation(jobApi, initialResourceVersion, watch1),
            mockWatchCreation(jobApi, updatedResourceVersion, watch2)
        )

        val helper = JobWatchHelper.create(jobApi, createConfig())

        helper.nextEvent() shouldBe event
    }
})

/**
 * Create mock for the [MonitorConfig] that returns the test namespace.
 */
private fun createConfig(): MonitorConfig =
    mockk {
        every { namespace } returns NAMESPACE
    }

/**
 * Create a mock for the jobs API.
 */
private fun createApiMock(): BatchV1Api {
    val client = mockk<ApiClient>()
    val api = mockk<BatchV1Api>()
    every { api.apiClient } returns client

    return api
}

/**
 * Create a mock for a [Watch] that returns the given events.
 */
private fun createWatchWithEvents(vararg events: Watch.Response<V1Job>): Watch<V1Job> =
    mockk<Watch<V1Job>>().apply {
        every { iterator() } returns events.toMutableList().iterator()
    }

/**
 * Mock the creation of a [Watch] for jobs using the given [jobApi] and [resourceVersion]. Return the request that
 * should be returned (mocked) by [jobApi].listNamespacedJob API call.
 */
private fun mockWatchCreation(
    jobApi: BatchV1Api,
    resourceVersion: String,
    watch: Watch<V1Job>
): BatchV1Api.APIlistNamespacedJobRequest {
    val call = mockk<Call>()

    // Mocks the two kinds of listNamespacedJob calls made by the JobWatchHelper, either with execute() or with call().
    val request = mockkBuilder<BatchV1Api.APIlistNamespacedJobRequest> {
        // Only stay inside this mock object if the expected "builder path" is called, otherwise "lock" builder calls
        // inside the "dummyRequest".
        every { allowWatchBookmarks(any()) } returns this
        every { resourceVersion(resourceVersion) } returns this
        every { watch(any()) } returns this
        every { limit(1) } returns this

        every { buildCall(null) } returns call
        every { execute() } returns V1JobList()
    }

    every { Watch.createWatch<V1Job>(jobApi.apiClient, call, JobWatchHelper.JOB_TYPE.type) } returns watch

    return request
}

/**
 * Use several [requests] to mock the return values of the [jobApi].listNamespacedJob API call. The requests are
 * returned in the order of the given parameter.
 */
fun mockMultipleWatchCreation(
    jobApi: BatchV1Api,
    vararg requests: BatchV1Api.APIlistNamespacedJobRequest
) {
    every { jobApi.listNamespacedJob(NAMESPACE) } returnsMany requests.toList()
}

/**
 * Create a [V1Job] object with metadata referencing the given [resourceVersion].
 */
private fun createJobWithResourceVersion(resourceVersion: String): V1Job = V1Job().apply {
    metadata(
        V1ObjectMeta().apply { resourceVersion(resourceVersion) }
    )
}

/**
 * Prepare this mock for the Job API to expect requests for the list of jobs. Answer these requests with mock job
 * lists that report the specified [jobListResourceVersions].
 */
private fun BatchV1Api.expectJobListRequests(vararg jobListResourceVersions: String) {
    val jobLists = jobListResourceVersions.map { version ->
        val meta = V1ListMeta().apply {
            resourceVersion = version
        }
        V1JobList().apply {
            metadata = meta
        }
    }

    val dummyRequest = mockkBuilder<BatchV1Api.APIlistNamespacedJobRequest> {
        every { buildCall(null) } returns mockk()
        every { execute() } returns mockk()
    }

    val requestForExecute = mockkBuilder<BatchV1Api.APIlistNamespacedJobRequest>(dummyRequest) {
        every { allowWatchBookmarks(false) } returns this
        every { limit(1) } returns this
        every { watch(false) } returns this

        every { execute() } returnsMany jobLists
    }

    every { listNamespacedJob(NAMESPACE) } returns requestForExecute
}

// See https://mockk.io/#reflection-matchers.
private inline fun <reified T : Any> mockkBuilder(defaultAnswer: T? = null, block: T.() -> Unit = {}): T {
    // Get all member functions that return the same type as the class itself.
    val builderFunctions = T::class.memberFunctions.filter { it.returnType.classifier == T::class }

    return mockk<T> {
        builderFunctions.forEach { func ->
            every {
                // Replace the "this" parameter with the mock object for any other parameters.
                val params = listOf<Any>(this@mockk) + func.parameters.drop(1).map {
                    @Suppress("UNCHECKED_CAST")
                    any(it.type.classifier as KClass<Any>)
                }

                func.call(*params.toTypedArray())
            } answers {
                defaultAnswer ?: this@mockk
            }
        }

        block()
    }
}
