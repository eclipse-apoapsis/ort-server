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

import com.github.ajalt.clikt.command.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.cli.OrtServerMain
import org.eclipse.apoapsis.ortserver.cli.json
import org.eclipse.apoapsis.ortserver.cli.utils.createOrtServerClient
import org.eclipse.apoapsis.ortserver.client.NotFoundException
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.api.RepositoriesApi

class StartCommandTest : StringSpec({
    afterEach { unmockkAll() }

    "start command should create an ORT run without waiting" {
        val repositoryId = 1L
        val revision = "main"

        val ortRun = OrtRun(
            id = 1,
            index = 1,
            organizationId = 1,
            productId = 1,
            repositoryId = 1,
            revision = "main",
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            jobConfigs = JobConfigurations(),
            status = OrtRunStatus.CREATED,
            jobs = Jobs(),
            issues = emptyList(),
            traceId = null,
            labels = emptyMap()
        )

        val repositoryMock = mockk<RepositoriesApi> {
            coEvery { createOrtRun(repositoryId, any()) } returns ortRun
        }
        val ortServerClientMock = mockk<OrtServerClient> {
            every { repositories } returns repositoryMock
        }

        mockkStatic(::createOrtServerClient)
        every { createOrtServerClient() } returns ortServerClientMock

        val command = OrtServerMain()
        val result = command.test(
            listOf(
                "runs",
                "start",
                "--repository-id",
                "$repositoryId",
                "--parameters",
                """{"revision": "$revision", "jobConfigs": {}}"""
            )
        )

        coVerify(exactly = 1) {
            repositoryMock.createOrtRun(repositoryId, any())
        }

        coVerify(exactly = 0) {
            repositoryMock.getOrtRun(any(), any())
        }

        result.statusCode shouldBe 0
        result.output shouldContain json.encodeToString(ortRun)
    }

    "start command should create an ORT run and wait" {
        val repositoryId = 1L
        val revision = "main"

        val createdOrtRun = OrtRun(
            id = 1,
            index = 1,
            organizationId = 1,
            productId = 1,
            repositoryId = 1,
            revision = "main",
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            jobConfigs = JobConfigurations(),
            status = OrtRunStatus.CREATED,
            jobs = Jobs(),
            issues = emptyList(),
            traceId = null,
            labels = emptyMap()
        )
        val activeOrtRun = createdOrtRun.copy(status = OrtRunStatus.ACTIVE)
        val finishedOrtRun = createdOrtRun.copy(status = OrtRunStatus.FINISHED)

        val repositoryMock = mockk<RepositoriesApi> {
            coEvery { createOrtRun(repositoryId, any()) } returns createdOrtRun
            coEvery { getOrtRun(repositoryId, createdOrtRun.index) } returns
                    createdOrtRun andThen
                    activeOrtRun andThen
                    finishedOrtRun
        }
        val ortServerClientMock = mockk<OrtServerClient> {
            every { repositories } returns repositoryMock
        }
        mockkStatic(::createOrtServerClient)
        every { createOrtServerClient() } returns ortServerClientMock

        System.setProperty("POLL_INTERVAL", "1")

        val command = OrtServerMain()
        val result = command.test(
            listOf(
                "runs",
                "start",
                "--repository-id",
                "$repositoryId",
                "--parameters",
                """{"revision": "$revision", "jobConfigs": {}}""",
                "--wait"
            )
        )

        result.statusCode shouldBe 0
        result.output shouldContain json.encodeToString(finishedOrtRun)

        coVerify(exactly = 1) {
            repositoryMock.createOrtRun(repositoryId, any())
        }

        coVerify(exactly = 3) {
            repositoryMock.getOrtRun(repositoryId, createdOrtRun.index)
        }
    }

    "start command should throw an exception if the ORT run cannot be created" {
        val repositoryId = 1L
        val revision = "main"

        val repositoryMock = mockk<RepositoriesApi> {
            coEvery { createOrtRun(repositoryId, any()) } throws NotFoundException("Invalid request")
        }
        val ortServerClientMock = mockk<OrtServerClient> {
            every { repositories } returns repositoryMock
        }
        mockkStatic(::createOrtServerClient)
        every { createOrtServerClient() } returns ortServerClientMock

        val command = OrtServerMain()
        shouldThrow<NotFoundException> {
            val result = command.test(
                listOf(
                    "runs",
                    "start",
                    "--repository-id",
                    "$repositoryId",
                    "--parameters",
                    """{"revision": "$revision", "jobConfigs": {}}"""
                )
            )

            result.statusCode shouldNotBe 0
        }

        coVerify(exactly = 1) {
            repositoryMock.createOrtRun(repositoryId, any())
        }
    }
})
