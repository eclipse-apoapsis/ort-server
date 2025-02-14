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
import org.eclipse.apoapsis.ortserver.client.OrtServerClient
import org.eclipse.apoapsis.ortserver.client.OrtServerClientException
import org.eclipse.apoapsis.ortserver.client.api.RepositoriesApi
import org.eclipse.apoapsis.ortserver.client.api.RunsApi

class InfoCommandTest : StringSpec({
    afterEach { unmockkAll() }

    "info command should get ORT run by ID" {
        val ortRunId = 1L

        val ortRun = OrtRun(
            id = ortRunId,
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

        val runsMock = mockk<RunsApi> {
            coEvery { getOrtRun(ortRunId) } returns ortRun
        }
        val ortServerClientMock = mockk<OrtServerClient> {
            every { runs } returns runsMock
        }
        mockkStatic(::createOrtServerClient)
        every { createOrtServerClient() } returns ortServerClientMock

        val command = OrtServerMain()
        val result = command.test(
            listOf(
                "runs",
                "info",
                "--run-id",
                "$ortRunId"
            )
        )

        coVerify(exactly = 1) {
            runsMock.getOrtRun(ortRunId)
        }

        result.statusCode shouldBe 0
        result.output shouldContain json.encodeToString(ortRun)
    }

    "info command should get ORT run by index" {
        val repositoryId = 1L
        val ortRunIndex = 1L

        val ortRun = OrtRun(
            id = 1,
            index = ortRunIndex,
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
            coEvery { getOrtRun(repositoryId, ortRunIndex) } returns ortRun
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
                "info",
                "--repository-id",
                "$repositoryId",
                "--index",
                "$ortRunIndex"
            )
        )

        coVerify(exactly = 1) {
            repositoryMock.getOrtRun(repositoryId, ortRunIndex)
        }

        result.statusCode shouldBe 0
        result.output shouldContain json.encodeToString(ortRun)
    }

    "info command should throw an exception if the ORT run does not exist" {
        val ortRunId = 1L

        val runsMock = mockk<RunsApi> {
            coEvery { getOrtRun(ortRunId) } throws OrtServerClientException("OrtRun not found")
        }
        val ortServerClientMock = mockk<OrtServerClient> {
            every { runs } returns runsMock
        }
        mockkStatic(::createOrtServerClient)
        every { createOrtServerClient() } returns ortServerClientMock

        val command = OrtServerMain()
        shouldThrow<OrtServerClientException> {
            val result = command.test(
                listOf(
                    "runs",
                    "info",
                    "--run-id",
                    "$ortRunId"
                )
            )

            result.statusCode shouldNotBe 0
        }

        coVerify(exactly = 1) {
            runsMock.getOrtRun(ortRunId)
        }
    }

    "info command should fail if no options are provided" {
        val command = OrtServerMain()
        val result = command.test(
            listOf(
                "runs",
                "info"
            )
        )

        result.statusCode shouldNotBe 0
    }

    "info command should fail if both options are provided" {
        val command = OrtServerMain()
        val result = command.test(
            listOf(
                "runs",
                "info",
                "--run-id",
                "1",
                "--repository-id",
                "1",
                "--index",
                "1"
            )
        )

        result.statusCode shouldNotBe 0
    }
})
