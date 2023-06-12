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

package org.ossreviewtoolkit.server.workers.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

class EnvironmentServiceTest : WordSpec({
    "findInfrastructureServiceForRepository" should {
        "return null if no infrastructure services are defined" {
            val repository = mockk<InfrastructureServiceRepository> {
                every { listForRepositoryUrl(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID) } returns emptyList()
            }

            val environmentService = EnvironmentService(repository, mockk())
            val result =
                environmentService.findInfrastructureServiceForRepository(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID)

            result should beNull()
        }

        "return the infrastructure service with the longest matching URL" {
            val matchingService = createInfrastructureService()
            val service1 = createInfrastructureService("https://repo.example.org/test-orga/test-repo")
            val service2 = createInfrastructureService("https://repo.example.org/")

            val repository = mockk<InfrastructureServiceRepository> {
                every { listForRepositoryUrl(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID) } returns listOf(
                    service2,
                    matchingService,
                    service1
                )
            }

            val environmentService = EnvironmentService(repository, mockk())
            val result =
                environmentService.findInfrastructureServiceForRepository(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID)

            result shouldBe matchingService
        }

        "ignore infrastructure services for other repositories" {
            val matchingService = createInfrastructureService()
            val service1 = createInfrastructureService("https://repo.example.org/test-orga/other-repo.git")
            val service2 =
                createInfrastructureService("https://x.org?url=https://repo.example.org/test-orga/test-repo.git")
            val repository = mockk<InfrastructureServiceRepository> {
                every { listForRepositoryUrl(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID) } returns listOf(
                    service2,
                    matchingService,
                    service1
                )
            }

            val environmentService = EnvironmentService(repository, mockk())
            val result =
                environmentService.findInfrastructureServiceForRepository(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID)

            result shouldBe matchingService
        }
    }

    "generateNetRcFile" should {
        "produce the correct file using the NetRcGenerator" {
            val context = mockk<WorkerContext>()
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val targetFile = tempfile()
            val netRcLines = listOf("credentials1", "credentials2", "more credentials")
            val generator = mockk<NetRcGenerator> {
                every { targetFile() } returns targetFile
                coEvery { generate(context, services) } returns netRcLines
            }

            val environmentService = EnvironmentService(mockk(), generator)
            val resultFile = environmentService.generateNetRcFile(context, services)

            resultFile shouldBe targetFile
            resultFile.readLines() shouldBe netRcLines
        }
    }
})

private const val REPOSITORY_URL = "https://repo.example.org/test-orga/test-repo.git"
private const val ORGANIZATION_ID = 20230607115501L
private const val PRODUCT_ID = 20230607115528L

/** A counter for generating unique service names. */
private var counter = 0

/**
 * Create an [InfrastructureService] with the given [url] and other standard properties.
 */
private fun createInfrastructureService(url: String = REPOSITORY_URL): InfrastructureService =
    InfrastructureService(
        name = "service${counter++}",
        url = url,
        usernameSecret = mockk(),
        passwordSecret = mockk(),
        organization = null,
        product = null
    )
