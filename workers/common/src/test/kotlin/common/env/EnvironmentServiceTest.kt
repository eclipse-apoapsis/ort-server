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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.REPOSITORY_URL
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService

class EnvironmentServiceTest : WordSpec({
    "findInfrastructureServiceForRepository" should {
        "return null if no infrastructure services are defined" {
            val repository = mockk<InfrastructureServiceRepository> {
                every { listForRepositoryUrl(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID) } returns emptyList()
            }

            val environmentService = EnvironmentService(repository, mockk())
            val result =
                environmentService.findInfrastructureServiceForRepository(mockContext())

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
                environmentService.findInfrastructureServiceForRepository(mockContext())

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
                environmentService.findInfrastructureServiceForRepository(mockContext())

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

            val generator = mockk<NetRcGenerator> {
                coEvery { generate(any(), any()) } just runs
            }

            val environmentService = EnvironmentService(mockk(), generator)
            environmentService.generateNetRcFile(context, services)

            val slotBuilder = slot<ConfigFileBuilder>()
            val definitions = mutableListOf<Collection<EnvironmentServiceDefinition>>()
            coVerify {
                generator.generate(capture(slotBuilder), capture(definitions))
            }

            slotBuilder.captured.context shouldBe context
            definitions.flatten().map { it.service } shouldContainExactlyInAnyOrder services
        }
    }
})

private const val ORGANIZATION_ID = 20230607115501L
private const val PRODUCT_ID = 20230607115528L

/** A [Hierarchy] object for the test repository. */
private val repositoryHierarchy = Hierarchy(
    Repository(20230613071811L, ORGANIZATION_ID, PRODUCT_ID, RepositoryType.GIT, REPOSITORY_URL),
    Product(PRODUCT_ID, ORGANIZATION_ID, "testProduct"),
    Organization(ORGANIZATION_ID, "test organization")
)

/**
 * Create a mock [WorkerContext] object that is prepared to return the [Hierarchy] of the test repository.
 */
private fun mockContext(): WorkerContext =
    mockk {
        every { hierarchy } returns repositoryHierarchy
    }
