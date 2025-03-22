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

package org.eclipse.apoapsis.ortserver.workers.common.env

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll

import java.io.File
import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.REPOSITORY_URL
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigLoader
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentDefinitionFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

import org.ossreviewtoolkit.utils.ort.createOrtTempDir

class EnvironmentServiceTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "findInfrastructureServicesForRepository" should {
        "return the infrastructure services defined for the hierarchy" {
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo.example.org/test-orga/test-repo"),
                createInfrastructureService("https://repo.example.org/")
            )

            val repository = mockk<InfrastructureServiceRepository> {
                every { listForHierarchy(ORGANIZATION_ID, PRODUCT_ID) } returns services
            }

            val environmentService = EnvironmentService(repository, mockk(), mockk())
            val result = environmentService.findInfrastructureServicesForRepository(mockContext(), null)

            result shouldContainExactlyInAnyOrder services
        }

        "return the infrastructure services from the environment configuration" {
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo.example.org/test-orga/test-repo"),
                createInfrastructureService("https://repo.example.org/")
            )

            val config = mockk<EnvironmentConfig>()
            val configLoader = mockk<EnvironmentConfigLoader> {
                every { resolve(config, any()) } returns ResolvedEnvironmentConfig(services)
            }

            val repository = mockk<InfrastructureServiceRepository> {
                every { listForHierarchy(ORGANIZATION_ID, PRODUCT_ID) } returns emptyList()
            }

            val environmentService = EnvironmentService(repository, mockk(), configLoader)
            val result = environmentService.findInfrastructureServicesForRepository(mockContext(), config)

            result shouldContainExactlyInAnyOrder services
        }

        "return the merged infrastructure services from the hierarchy and the environment configuration" {
            val hierarchyService = createInfrastructureService("https://hierarchy.example.org/")
            val configService = createInfrastructureService("https://config.example.org/")
            val overriddenService = createInfrastructureService().copy(name = "overridden")
            val overrideService = createInfrastructureService()

            val repository = mockk<InfrastructureServiceRepository> {
                every {
                    listForHierarchy(ORGANIZATION_ID, PRODUCT_ID)
                } returns listOf(hierarchyService, overriddenService)
            }

            val config = mockk<EnvironmentConfig>()
            val configLoader = mockk<EnvironmentConfigLoader> {
                every {
                    resolve(config, any())
                } returns ResolvedEnvironmentConfig(listOf(configService, overrideService))
            }

            val environmentService = EnvironmentService(repository, mockk(), configLoader)
            val result = environmentService.findInfrastructureServicesForRepository(mockContext(), config)

            result shouldContainExactlyInAnyOrder listOf(hierarchyService, configService, overrideService)
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

            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

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
            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            configResult shouldBe config

            assignedServices shouldContainExactlyInAnyOrder services
        }

        "setup the authenticator with the services from the config file" {
            val services = listOf<InfrastructureService>(mockk(), mockk())
            val context = mockContext()

            val config = ResolvedEnvironmentConfig(services, emptyList())
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            coVerify { context.setupAuthentication(services) }
        }

        "load the environment configuration from the ORT run if any is provided" {
            val envConfigFileName = "alternative.ort.env.yml"

            val ortRunWithEnvironment = mockk<OrtRun> {
                every { id } returns RUN_ID
                every { environmentConfigPath } returns envConfigFileName
            }
            val context = mockk<WorkerContext> {
                every { hierarchy } returns repositoryHierarchy
                every { ortRun } returns ortRunWithEnvironment
                coEvery { setupAuthentication(any()) } just runs
            }

            val repositoryFolder = createOrtTempDir("EnvironmentServiceTest")
            File(repositoryFolder, envConfigFileName).writeText(
                """
                environmentVariables:
                - name: "variable1"
                  value: "testValue1"
            """.trimIndent()
            )

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val configLoader = EnvironmentConfigLoader(mockk(), mockk(), EnvironmentDefinitionFactory())
            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            val config = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            config.environmentVariables shouldBe setOf(SimpleVariableDefinition("variable1", "testValue1"))
        }

        "assign the infrastructure services for the repository to the current ORT run" {
            val repositoryService = mockk<InfrastructureService>()
            val otherService = mockk<InfrastructureService>()

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(otherService), emptyList())
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, null, listOf(repositoryService))

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
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            assignedServices shouldContainExactlyInAnyOrder services
        }

        "set an overridden credentials type when assigning infrastructure services to the current ORT run" {
            val service = InfrastructureService(
                name = "aTestService",
                url = "https://test.example.org/test/service.git",
                usernameSecret = mockk(),
                passwordSecret = mockk(),
                organization = null,
                product = null
            )
            val definition = EnvironmentServiceDefinition(
                service,
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(service), listOf(definition))
            val configLoader = mockConfigLoader(config)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            val assignedServices = serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            val expectedAssignedService = service.copy(
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )
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
            environmentService.setUpEnvironment(context, repositoryFolder, null, listOf(repositoryService))

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

            val envConfig = mockk<EnvironmentConfig>(relaxed = true)
            val resolvedConfig = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator1, generator2), configLoader)

            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, envConfig, emptyList())

            configResult shouldBe resolvedConfig

            val args1 = generator1.verify(context, definitions)
            val args2 = generator2.verify(context, definitions)

            args1.first shouldNotBe args2.first
        }

        "handle infrastructure services not referenced by environment definitions" {
            val service = mockk<InfrastructureService>()
            val context = mockContext()
            val generator = mockGenerator()

            val envConfig = mockk<EnvironmentConfig>(relaxed = true)
            val resolvedConfig = ResolvedEnvironmentConfig(listOf(service), emptyList())
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, listOf(generator), configLoader)
            environmentService.setUpEnvironment(context, repositoryFolder, envConfig, emptyList())

            val (_, definitions) = generator.verify(context, null)
            definitions shouldHaveSize 1
            definitions.first().service shouldBe service
        }
    }

    "generateNetRcFile" should {
        "produce the correct file using the NetRcGenerator" {
            val context = mockk<WorkerContext>(relaxed = true)
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

        "setup the authenticator with the services" {
            val context = mockk<WorkerContext> {
                coEvery { setupAuthentication(any()) } just runs
            }

            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val serviceRepository = mockk<InfrastructureServiceRepository>()
            serviceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(serviceRepository, emptyList(), mockk())
            environmentService.generateNetRcFile(context, services)

            coVerify { context.setupAuthentication(services) }
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

        "setup the authenticator with services stored in the database" {
            val context = mockContext()
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val serviceRepository = mockk<InfrastructureServiceRepository> {
                every { listForRun(RUN_ID) } returns services
            }

            val environmentService = EnvironmentService(serviceRepository, emptyList(), mockk())
            environmentService.generateNetRcFileForCurrentRun(context)

            coVerify { context.setupAuthentication(services) }
        }
    }

    "merge" should {
        "return the first configuration if the second one is null" {
            val config = EnvironmentConfig(
                infrastructureServices = listOf(createInfrastructureServiceDeclaration("service")),
                environmentDefinitions = mapOf("key" to listOf(mapOf("key" to "value"))),
                environmentVariables = listOf(EnvironmentVariableDeclaration("env"))
            )

            val result = config.merge(null)

            result shouldBe config
        }

        "combine non-overlapping services" {
            val service1 = createInfrastructureServiceDeclaration("service1")
            val service2 = createInfrastructureServiceDeclaration("service2")

            val config1 = EnvironmentConfig(infrastructureServices = listOf(service1))
            val config2 = EnvironmentConfig(infrastructureServices = listOf(service2))

            val result = config1.merge(config2)

            result.infrastructureServices shouldContainExactlyInAnyOrder listOf(service1, service2)
        }

        "override overlapping services" {
            val service1 = createInfrastructureServiceDeclaration("service")
            val service2 = createInfrastructureServiceDeclaration("service")

            val config1 = EnvironmentConfig(infrastructureServices = listOf(service1))
            val config2 = EnvironmentConfig(infrastructureServices = listOf(service2))

            val result = config1.merge(config2)

            result.infrastructureServices shouldContainExactlyInAnyOrder listOf(service2)
        }

        "combine non-overlapping environment definitions" {
            val definitions1 = mapOf("key1" to listOf(mapOf("key1" to "value1")))
            val definitions2 = mapOf("key2" to listOf(mapOf("key2" to "value2")))

            val config1 = EnvironmentConfig(environmentDefinitions = definitions1)
            val config2 = EnvironmentConfig(environmentDefinitions = definitions2)

            val result = config1.merge(config2)

            result.environmentDefinitions shouldBe definitions1 + definitions2
        }

        "override overlapping environment definitions" {
            val definitions1 = listOf(mapOf("key" to "value1"))
            val definitions2 = listOf(mapOf("key" to "value2"))

            val config1 = EnvironmentConfig(environmentDefinitions = mapOf("key" to definitions1))
            val config2 = EnvironmentConfig(environmentDefinitions = mapOf("key" to definitions2))

            val result = config1.merge(config2)

            result.environmentDefinitions shouldBe mapOf("key" to definitions1 + definitions2)
        }

        "combine non-overlapping environment variables" {
            val variable1 = EnvironmentVariableDeclaration("env1")
            val variable2 = EnvironmentVariableDeclaration("env2")

            val config1 = EnvironmentConfig(environmentVariables = listOf(variable1))
            val config2 = EnvironmentConfig(environmentVariables = listOf(variable2))

            val result = config1.merge(config2)

            result.environmentVariables shouldContainExactlyInAnyOrder listOf(variable1, variable2)
        }

        "override overlapping environment variables" {
            val variable1 = EnvironmentVariableDeclaration("env")
            val variable2 = EnvironmentVariableDeclaration("env")

            val config1 = EnvironmentConfig(environmentVariables = listOf(variable1))
            val config2 = EnvironmentConfig(environmentVariables = listOf(variable2))

            val result = config1.merge(config2)

            result.environmentVariables shouldContainExactlyInAnyOrder listOf(variable2)
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
    every { environmentConfigPath } returns null
}

/** A file representing the checkout folder of the current repository. */
private val repositoryFolder = File("repositoryCheckoutLocation")

/**
 * Create an InfrastructureServiceDeclaration with the given [name] and [credentialsTypes].
 */
fun createInfrastructureServiceDeclaration(
    name: String,
    credentialsTypes: Set<CredentialsType> = EnumSet.of(CredentialsType.NETRC_FILE)
): InfrastructureServiceDeclaration =
    InfrastructureServiceDeclaration(
        name = name,
        url = "https://example.org/$name",
        usernameSecret = "usernameSecret",
        passwordSecret = "passwordSecret",
        credentialsTypes = credentialsTypes
    )

/**
 * Create a mock [WorkerContext] object that is prepared to return the [Hierarchy] of the test repository and
 * expects some default interactions.
 */
private fun mockContext(): WorkerContext =
    mockk {
        every { hierarchy } returns repositoryHierarchy
        every { ortRun } returns currentOrtRun
        coEvery { setupAuthentication(any()) } just runs
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
private fun mockConfigLoader(config: ResolvedEnvironmentConfig): EnvironmentConfigLoader {
    val envConfig = mockk<EnvironmentConfig>(relaxed = true)

    mockkStatic(EnvironmentConfig::merge)
    every { envConfig.merge(any()) } returns envConfig

    return mockk<EnvironmentConfigLoader> {
        every { resolveAndParse(repositoryFolder) } returns envConfig
        every { resolve(any(), repositoryHierarchy) } returns config
    }
}

/**
 * Create a mock [EnvironmentConfigLoader] that is prepared to resolve the given [envConfig] and to return the
 * provided [resultConfig].
 */
private fun mockConfigLoader(
    envConfig: EnvironmentConfig,
    resultConfig: ResolvedEnvironmentConfig
): EnvironmentConfigLoader {
    val mockConfig = mockk<EnvironmentConfig>(relaxed = true)

    mockkStatic(EnvironmentConfig::merge)
    every { mockConfig.merge(envConfig) } returns envConfig

    return mockk {
        every { resolveAndParse(repositoryFolder) } returns mockConfig
        every { resolve(envConfig, repositoryHierarchy) } returns resultConfig
    }
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
        firstArg<InfrastructureService>().also { service ->
            assignedServices += service
        }
    }

    return assignedServices
}
