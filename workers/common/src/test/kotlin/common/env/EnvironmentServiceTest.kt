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

package org.ossreviewtoolkit.server.workers.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

import java.io.File

import org.ossreviewtoolkit.server.model.EnvironmentConfig
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.REPOSITORY_URL
import org.ossreviewtoolkit.server.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigLoader
import org.ossreviewtoolkit.server.workers.common.env.config.ResolvedEnvironmentConfig
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition

class EnvironmentServiceTest : WordSpec({
    "findInfrastructureServiceForRepository" should {
        "return null if no infrastructure services are defined" {
            val repository = mockk<InfrastructureServiceRepository> {
                every { listForRepositoryUrl(REPOSITORY_URL, ORGANIZATION_ID, PRODUCT_ID) } returns emptyList()
            }

            val environmentService = EnvironmentService(repository, mockk(), mockk())
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

            val environmentService = EnvironmentService(repository, mockk(), mockk())
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

            val environmentService = EnvironmentService(repository, mockk(), mockk())
            val result =
                environmentService.findInfrastructureServiceForRepository(mockContext())

            result shouldBe matchingService
        }
    }

    "setUpEnvironment from a file" should {
        "invoke all generators to produce the supported configuration files" {
            val definitions = listOf(
                EnvironmentServiceDefinition(mockk()),
                EnvironmentServiceDefinition(mockk()),
                EnvironmentServiceDefinition(mockk())
            )
            val context = mockContext()
            val generator1 = mockGenerator()
            val generator2 = mockGenerator()

            val config = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator1, generator2), configLoader)

            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null)

            configResult shouldBe config

            val args1 = generator1.verify(context, definitions)
            val args2 = generator2.verify(context, definitions)

            args1.first shouldNotBe args2.first
        }

        "associate all infrastructure services from the config file with the current ORT run" {
            val services = listOf<InfrastructureService>(mockk(), mockk())
            val context = mockContext()

            val config = ResolvedEnvironmentConfig(services, emptyList())
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null)

            configResult shouldBe config

            assignedServices shouldContainExactlyInAnyOrder services
        }

        "assign the infrastructure service for the repository to the current ORT run" {
            val repositoryService = mockk<InfrastructureService>()
            val otherService = mockk<InfrastructureService>()

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(otherService), emptyList())
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, repositoryService)

            assignedServices shouldContainExactlyInAnyOrder listOf(repositoryService, otherService)
        }

        "assign the infrastructure services referenced from environment definitions to the current ORT run" {
            val services = listOf<InfrastructureService>(mockk(), mockk(), mockk())
            val definitions = services.map(::EnvironmentServiceDefinition)

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, null)

            assignedServices shouldContainExactlyInAnyOrder services
        }

        "set an overridden excludeFromNetRc flag when assigning infrastructure services to the current ORT run" {
            val service = InfrastructureService(
                name = "aTestService",
                url = "https://test.example.org/test/service.git",
                usernameSecret = mockk(),
                passwordSecret = mockk(),
                organization = null,
                product = null
            )
            val definition = EnvironmentServiceDefinition(service, excludeServiceFromNetrc = true)

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(service), listOf(definition))
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, null)

            val expectedAssignedService = service.copy(excludeFromNetrc = true)
            assignedServices shouldContainExactlyInAnyOrder listOf(expectedAssignedService)
        }

        "remove duplicates before assigning services to the current ORT run" {
            val repositoryService = mockk<InfrastructureService>()
            val referencedService = mockk<InfrastructureService>()
            val services = listOf(repositoryService, mockk(), referencedService)

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(services, listOf(EnvironmentServiceDefinition(referencedService)))
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, repositoryService)

            assignedServices shouldContainExactlyInAnyOrder services
        }
    }

    "setUpEnvironment from a config" should {
        "invoke all generators to produce the supported configuration files" {
            val definitions = listOf(
                EnvironmentServiceDefinition(mockk()),
                EnvironmentServiceDefinition(mockk()),
                EnvironmentServiceDefinition(mockk())
            )
            val context = mockContext()
            val generator1 = mockGenerator()
            val generator2 = mockGenerator()

            val envConfig = mockk<EnvironmentConfig>()
            val resolvedConfig = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator1, generator2), configLoader)

            val configResult = environmentService.setUpEnvironment(context, envConfig)

            configResult shouldBe resolvedConfig

            val args1 = generator1.verify(context, definitions)
            val args2 = generator2.verify(context, definitions)

            args1.first shouldNotBe args2.first
        }

        "handle infrastructure services not referenced by environment definitions" {
            val service = mockk<InfrastructureService>()
            val context = mockContext()
            val generator = mockGenerator()

            val envConfig = mockk<EnvironmentConfig>()
            val resolvedConfig = ResolvedEnvironmentConfig(listOf(service), emptyList())
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator), configLoader)
            environmentService.setUpEnvironment(context, envConfig)

            val (_, definitions) = generator.verify(context, null)
            definitions shouldHaveSize 1
            definitions.first().service shouldBe service
        }
    }

    "generateNetRcFile" should {
        "produce the correct file using the NetRcGenerator" {
            val context = mockk<WorkerContext>()
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val generator = mockGenerator()

            val environmentService = EnvironmentService(mockk(), listOf(generator), mockk())
            environmentService.generateNetRcFile(context, services)

            val args = generator.verify(context)
            args.second.map { it.service } shouldContainExactlyInAnyOrder services
        }
    }

    "generateNetRcFileForCurrentRun" should {
        "produce the correct file with services stored in the database" {
            val context = mockContext()
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val serviceRepository = mockk<InfrastructureServiceRepository> {
                every { listForRun(RUN_ID) } returns services
            }

            val generator = mockGenerator()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator), mockk())
            environmentService.generateNetRcFileForCurrentRun(context)

            val args = generator.verify(context)
            args.second.map { it.service } shouldContainExactlyInAnyOrder services
        }
    }
})

private const val ORGANIZATION_ID = 20230607115501L
private const val PRODUCT_ID = 20230607115528L
private const val RUN_ID = 20230622095805L

/** A [Hierarchy] object for the test repository. */
private val repositoryHierarchy = Hierarchy(
    Repository(20230613071811L, ORGANIZATION_ID, PRODUCT_ID, RepositoryType.GIT, REPOSITORY_URL),
    Product(PRODUCT_ID, ORGANIZATION_ID, "testProduct"),
    Organization(ORGANIZATION_ID, "test organization")
)

/** A mock representing the current ORT run. */
private val currentOrtRun = mockk<OrtRun> {
    every { id } returns RUN_ID
}

/** A file representing the checkout folder of the current repository. */
private val repositoryFolder = File("repositoryCheckoutLocation")

/**
 * Create a mock [WorkerContext] object that is prepared to return the [Hierarchy] of the test repository.
 */
private fun mockContext(): WorkerContext =
    mockk {
        every { hierarchy } returns repositoryHierarchy
        every { ortRun } returns currentOrtRun
    }

/**
 * Create a mock [EnvironmentConfigGenerator] that is prepared for an invocation of its
 * [EnvironmentConfigGenerator.generateApplicable] method.
 */
private fun mockGenerator(): EnvironmentConfigGenerator<EnvironmentServiceDefinition> =
    mockk {
        coEvery { generateApplicable(any(), any()) } just runs
    }

/**
 * Create a mock [EnvironmentConfigLoader] that is prepared to return the given [config].
 */
private fun mockConfigLoader(config: ResolvedEnvironmentConfig): EnvironmentConfigLoader =
    mockk<EnvironmentConfigLoader> {
        every { parse(repositoryFolder, repositoryHierarchy) } returns config
    }

/**
 * Create a mock [EnvironmentConfigLoader] that is prepared to resolve the given [envConfig] and to return the
 * provided [resultConfig].
 */
private fun mockConfigLoader(
    envConfig: EnvironmentConfig,
    resultConfig: ResolvedEnvironmentConfig
): EnvironmentConfigLoader =
    mockk {
        every { resolve(envConfig, repositoryHierarchy) } returns resultConfig
    }

/**
 * Verify that this [EnvironmentConfigGenerator] has been invoked correctly with the given [context]. Optionally,
 * check the definitions passed to the generator. Return the arguments passed to the
 * [EnvironmentConfigGenerator.generateApplicable] function as a [Pair] for further checks.
 */
private fun <T : EnvironmentServiceDefinition> EnvironmentConfigGenerator<T>.verify(
    context: WorkerContext,
    expectedDefinitions: Collection<EnvironmentServiceDefinition>? = null
): Pair<ConfigFileBuilder, Collection<EnvironmentServiceDefinition>> {
    val slotBuilder = slot<ConfigFileBuilder>()
    val slotDefinitions = slot<Collection<EnvironmentServiceDefinition>>()

    coVerify {
        generateApplicable(capture(slotBuilder), capture(slotDefinitions))
    }

    slotBuilder.captured.context shouldBe context

    if (expectedDefinitions != null) {
        slotDefinitions.captured shouldBe expectedDefinitions
    }

    return slotBuilder.captured to slotDefinitions.captured
}

/**
 * Prepare this mock for an [InfrastructureServiceRepository] to expect calls that assign infrastructure services to
 * the current ORT run. Return a list that contains the assigned services after running the test.
 */
private fun InfrastructureServiceRepository.expectServiceAssignments(): List<InfrastructureService> {
    val assignedServices = mutableListOf<InfrastructureService>()

    val slotService = slot<InfrastructureService>()
    every { getOrCreateForRun(capture(slotService), RUN_ID) } answers {
        firstArg<InfrastructureService>().also {
            assignedServices += it
        }
    }

    return assignedServices
}
