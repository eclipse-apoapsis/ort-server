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
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll

import java.io.File
import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceDeclarationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.workers.common.auth.CredentialResolverFun
import org.eclipse.apoapsis.ortserver.workers.common.auth.undefinedCredentialResolver
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.REPOSITORY_URL
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigLoader
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentDefinitionFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

import org.ossreviewtoolkit.utils.ort.createOrtTempDir

@Suppress("LargeClass")
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
                every { listForHierarchy(any<Hierarchy>()) } returns services
            }

            val workerConext = mockk<WorkerContext> {
                every { hierarchy } returns repositoryHierarchy
            }

            val environmentService = EnvironmentService(
                repository,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk()
            )
            val result = environmentService.findInfrastructureServicesForRepository(workerConext, null)

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
                every { listForHierarchy(repositoryHierarchy) } returns emptyList()
            }

            val environmentService = EnvironmentService(
                repository,
                mockk(),
                mockk(),
                mockk(),
                configLoader,
                mockk()
            )
            val result = environmentService.findInfrastructureServicesForRepository(mockContext(), config)

            result shouldContainExactlyInAnyOrder services
        }

        "return the merged infrastructure services from the hierarchy and the environment configuration" {
            val hierarchyService = createInfrastructureService("https://hierarchy.example.org/")
            val configService = createInfrastructureService("https://config.example.org/")
            val overriddenService = createInfrastructureService().copy(name = "overridden")
            val overrideService = createInfrastructureService()

            val repository = mockk<InfrastructureServiceRepository> {
                every { listForHierarchy(repositoryHierarchy) } returns listOf(hierarchyService, overriddenService)
            }

            val config = mockk<EnvironmentConfig>()
            val configLoader = mockk<EnvironmentConfigLoader> {
                every {
                    resolve(config, any())
                } returns ResolvedEnvironmentConfig(listOf(configService, overrideService))
            }

            val environmentService = EnvironmentService(
                repository,
                mockk(),
                mockk(),
                mockk(),
                configLoader,
                mockk()
            )
            val result = environmentService.findInfrastructureServicesForRepository(mockContext(), config)

            result shouldContainExactlyInAnyOrder listOf(hierarchyService, configService, overrideService)
        }
    }

    "setUpEnvironment from a file" should {
        "invoke all generators to produce the supported configuration files" {
            val definitions = listOf(
                EnvironmentServiceDefinition(createInfrastructureService()),
                EnvironmentServiceDefinition(createInfrastructureService("https://svc.example.com/service")),
                EnvironmentServiceDefinition(createInfrastructureService("https://svc.example.com/service2"))
            )
            val context = mockContext()
            val generator1 = mockGenerator()
            val generator2 = mockGenerator()

            val config = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                listOf(generator1, generator2),
                configLoader,
                createMockAdminConfigService()
            )

            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            configResult shouldBe config

            val args1 = generator1.verify(context, definitions)
            val args2 = generator2.verify(context, definitions)

            args1.first shouldNotBe args2.first
        }

        "associate all infrastructure services from the config file with the current ORT run" {
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://service.example.com/service"),
            )

            val expectedDynamicServices = services.map { it.toInfrastructureServiceDeclaration() }

            val context = mockContext()

            val config = ResolvedEnvironmentConfig(services, emptyList())
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            val assignedServices = dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            configResult shouldBe config

            assignedServices shouldContainExactlyInAnyOrder expectedDynamicServices
        }

        "setup the authenticator with the services from the config file" {
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://service.example.com/service"),
            )
            val context = mockContext()

            val config = ResolvedEnvironmentConfig(services, emptyList())
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            coVerify { context.setupAuthentication(services, any()) }
        }

        "load the environment configuration from the ORT run if any is provided" {
            val envConfigFileName = "alternative.ort.env.yml"

            val ortRunWithEnvironment = mockk<OrtRun> {
                every { id } returns RUN_ID
                every { organizationId } returns ORGANIZATION_ID
                every { environmentConfigPath } returns envConfigFileName
                every { resolvedJobConfigContext } returns RESOLVED_JOB_CONFIG_CONTEXT
            }
            val context = mockk<WorkerContext> {
                every { hierarchy } returns repositoryHierarchy
                every { ortRun } returns ortRunWithEnvironment
                every { credentialResolverFun } returns undefinedCredentialResolver
                coEvery { setupAuthentication(any(), any()) } just runs
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
            val environmentService = EnvironmentService(
                serviceRepository,
                mockk(),
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            val config = environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            config.environmentVariables shouldBe setOf(SimpleVariableDefinition("variable1", "testValue1"))
        }

        "assign the infrastructure services for the repository to the current ORT run" {
            val repositoryService = createInfrastructureService()
            val otherService = createInfrastructureService("https://service.example.com/service")

            val expectedDynamicServices = listOf(repositoryService, otherService).map(
                InfrastructureService::toInfrastructureServiceDeclaration
            )

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(otherService), emptyList())
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            val assignedServices = dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, null, listOf(repositoryService))

            assignedServices shouldContainExactlyInAnyOrder expectedDynamicServices
        }

        "assign the infrastructure services referenced from environment definitions to the current ORT run" {
            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://service.example.com/service"),
                createInfrastructureService("https://service2.example.com/service2")
            )
            val definitions = services.map(::EnvironmentServiceDefinition)

            val expectedDynamicServices = services.map(
                InfrastructureService::toInfrastructureServiceDeclaration
            )

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            val assignedServices = dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            assignedServices shouldContainExactlyInAnyOrder expectedDynamicServices
        }

        "set an overridden credentials type when assigning infrastructure services to the current ORT run" {
            val service = InfrastructureService(
                name = "aTestService",
                url = "https://test.example.org/test/service.git",
                usernameSecret = mockk {
                    every { name } returns "some-username-secret-name"
                },
                passwordSecret = mockk {
                    every { name } returns "some-password-secret-name"
                },
                organization = null,
                product = null,
                repository = null
            )
            val definition = EnvironmentServiceDefinition(
                service,
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(listOf(service), listOf(definition))
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            val assignedServices = dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, null, emptyList())

            val expectedAssignedService = service.copy(
                credentialsTypes = EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            ).toInfrastructureServiceDeclaration()

            assignedServices shouldContainExactlyInAnyOrder listOf(expectedAssignedService)
        }

        "remove duplicates before assigning services to the current ORT run" {
            val repositoryService = createInfrastructureService()
            val referencedService = createInfrastructureService("https://service.example.com/service")
            val services = listOf(
                repositoryService,
                createInfrastructureService("https://service2.example.com/service2"),
                referencedService
            )
            val expectedDynamicServices = services.map(
                InfrastructureService::toInfrastructureServiceDeclaration
            )

            val context = mockContext()
            val config = ResolvedEnvironmentConfig(services, listOf(EnvironmentServiceDefinition(referencedService)))
            val configLoader = mockConfigLoader(config)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            val assignedServices = dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, null, listOf(repositoryService))

            assignedServices shouldContainExactlyInAnyOrder expectedDynamicServices
        }
    }

    "setUpEnvironment from a config" should {
        "invoke all generators to produce the supported configuration files" {
            val definitions = listOf(
                EnvironmentServiceDefinition(createInfrastructureService()),
                EnvironmentServiceDefinition(createInfrastructureService("https://service.example.com/service")),
                EnvironmentServiceDefinition(createInfrastructureService("https://service2.example.com/service2"))
            )
            val context = mockContext()
            val generator1 = mockGenerator()
            val generator2 = mockGenerator()

            val envConfig = mockk<EnvironmentConfig>(relaxed = true)
            val resolvedConfig = ResolvedEnvironmentConfig(emptyList(), definitions)
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                listOf(generator1, generator2),
                configLoader,
                createMockAdminConfigService()
            )

            val configResult = environmentService.setUpEnvironment(context, repositoryFolder, envConfig, emptyList())

            configResult shouldBe resolvedConfig

            val args1 = generator1.verify(context, definitions)
            val args2 = generator2.verify(context, definitions)

            args1.first shouldNotBe args2.first
        }

        "handle infrastructure services not referenced by environment definitions" {
            val service = createInfrastructureService()
            val context = mockContext()
            val generator = mockGenerator()

            val envConfig = mockk<EnvironmentConfig>(relaxed = true)
            val resolvedConfig = ResolvedEnvironmentConfig(listOf(service), emptyList())
            val configLoader = mockConfigLoader(envConfig, resolvedConfig)

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            dynamicServiceRepository.expectServiceAssignments()

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                listOf(generator),
                configLoader,
                createMockAdminConfigService()
            )
            environmentService.setUpEnvironment(context, repositoryFolder, envConfig, emptyList())

            val (_, definitions) = generator.verify(context, null)
            definitions shouldHaveSize 1
            definitions.first().service shouldBe service
        }
    }

    "setupAuthentication" should {
        "setup the authenticator with the services" {
            val resolverFun = mockk<CredentialResolverFun>()
            val context = mockk<WorkerContext> {
                every { credentialResolverFun } returns resolverFun
                every { ortRun } returns currentOrtRun
                coEvery { setupAuthentication(any(), any()) } just runs
            }

            val services = listOf(
                createInfrastructureService(),
                createInfrastructureService("https://repo2.example.org/test-orga/test-repo2.git")
            )

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository>()
            dynamicServiceRepository.expectServiceAssignments()

            mockkObject(NetRcManager)
            val netRcManager = mockk<NetRcManager>()
            every { NetRcManager.create(resolverFun) } returns netRcManager

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                mockk(),
                emptyList(),
                mockk(),
                createMockAdminConfigService()
            )
            environmentService.setupAuthentication(context, services)

            coVerify { context.setupAuthentication(services, netRcManager) }
        }
    }

    "setupAuthenticationForCurrentRun" should {
        "setup the authenticator with services stored in the database" {
            val context = mockContext()
            val resolverFun = context.credentialResolverFun

            val usernameSecret = mockk<Secret> {
                every { name } returns "usernameSecretName"
            }

            val passwordSecret = mockk<Secret> {
                every { name } returns "passwordSecretName"
            }

            val services = listOf(
                createInfrastructureService(
                    usernameSecret = usernameSecret,
                    passwordSecret = passwordSecret
                ),
                createInfrastructureService(
                    "https://repo2.example.org/test-orga/test-repo2.git",
                    usernameSecret = usernameSecret,
                    passwordSecret = passwordSecret
                )
            )
            val dynamicServices = services.map { it.toInfrastructureServiceDeclaration() }

            val dynamicServiceRepository = mockk<InfrastructureServiceDeclarationRepository> {
                every { listForRun(RUN_ID) } returns dynamicServices
            }

            val secretRepository = mockk<SecretRepository> {
               every { getByIdAndName(any(), "usernameSecretName") } returns usernameSecret
               every { getByIdAndName(any(), "passwordSecretName") } returns passwordSecret
            }

            mockkObject(NetRcManager)
            val netRcManager = mockk<NetRcManager>()
            every { NetRcManager.create(resolverFun) } returns netRcManager

            val environmentService = EnvironmentService(
                mockk(),
                dynamicServiceRepository,
                secretRepository,
                emptyList(),
                mockk(),
                createMockAdminConfigService()
            )
            environmentService.setupAuthenticationForCurrentRun(context)

            coVerify { context.setupAuthentication(services, netRcManager) }
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

    "resolveServiceByName" should {
        "return null if a secret with the given name does not exist in the context of the ORT run" {
            val secretRepository = mockSecretRepository()

            val environmentService = EnvironmentService(
                mockk(),
                mockk(),
                secretRepository,
                emptyList(),
                mockk(),
                mockk()
            )

            val result = environmentService.resolveSecretByName("my-secret-name", currentOrtRun, "my-service-name")

            result shouldBe null
        }

        "return a matching secret on repository level in the context of the ORT run" {
            val mySecret = mockk<Secret> {
                every { repository } returns mockk<Repository> {
                    every { id } returns REPOSITORY_ID
                }
            }

            val secretRepository = mockSecretRepository {
                every {
                    getByIdAndName(RepositoryId(REPOSITORY_ID), "my-secret-name")
                } returns mySecret
            }

            val environmentService = EnvironmentService(
                mockk(),
                mockk(),
                secretRepository,
                emptyList(),
                mockk(),
                mockk()
            )

            val result = environmentService.resolveSecretByName("my-secret-name", currentOrtRun, "my-service-name")

            result shouldBe mySecret
            result?.repository?.id shouldBe REPOSITORY_ID
        }

        "return a matching secret on product level in the context of the ORT run" {
            val mySecret = mockk<Secret> {
                every { product } returns mockk<Product> {
                    every { id } returns PRODUCT_ID
                }
            }

            val secretRepository = mockSecretRepository {
                every {
                    getByIdAndName(ProductId(PRODUCT_ID), "my-secret-name")
                } returns mySecret
            }

            val environmentService = EnvironmentService(
                mockk(),
                mockk(),
                secretRepository,
                emptyList(),
                mockk(),
                mockk()
            )

            val result = environmentService.resolveSecretByName("my-secret-name", currentOrtRun, "my-service-name")

            result shouldBe mySecret
            result?.product?.id shouldBe PRODUCT_ID
        }

        "return a matching secret on organization level in the context of the ORT run" {
            val mySecret = mockk<Secret> {
                every { organization } returns mockk<Organization> {
                    every { id } returns ORGANIZATION_ID
                }
            }

            val secretRepository = mockSecretRepository {
                every {
                    getByIdAndName(OrganizationId(ORGANIZATION_ID), "my-secret-name")
                } returns mySecret
            }

            val environmentService = EnvironmentService(
                mockk(),
                mockk(),
                secretRepository,
                emptyList(),
                mockk(),
                mockk()
            )

            val result = environmentService.resolveSecretByName("my-secret-name", currentOrtRun, "my-service-name")

            result shouldBe mySecret
            result?.organization?.id shouldBe ORGANIZATION_ID
        }

        "return a matching secret on repository level in the context of the ORT run when multiple secrets exist" {
            val mySecretRepositoryLevel = mockk<Secret> {
                every { repository } returns mockk<Repository> {
                    every { id } returns REPOSITORY_ID
                }
            }

            val mySecretProductLevel = mockk<Secret> {
                every { product } returns mockk<Product> {
                    every { id } returns PRODUCT_ID
                }
            }

            val mySecretOrganizationLevel = mockk<Secret> {
                every { organization } returns mockk<Organization> {
                    every { id } returns ORGANIZATION_ID
                }
            }

            val secretRepository = mockSecretRepository {
                every {
                    getByIdAndName(RepositoryId(REPOSITORY_ID), "my-secret-name")
                } returns mySecretRepositoryLevel

                every {
                    getByIdAndName(ProductId(PRODUCT_ID), "my-secret-name")
                } returns mySecretProductLevel

                every {
                    getByIdAndName(OrganizationId(ORGANIZATION_ID), "my-secret-name")
                } returns mySecretOrganizationLevel
            }

            val environmentService = EnvironmentService(
                mockk(),
                mockk(),
                secretRepository,
                emptyList(),
                mockk(),
                mockk()
            )

            val result = environmentService.resolveSecretByName("my-secret-name", currentOrtRun, "my-service-name")

            result shouldBe mySecretRepositoryLevel
            result?.repository?.id shouldBe REPOSITORY_ID
        }
    }
})

private const val ORGANIZATION_ID = 20230607115501L
private const val PRODUCT_ID = 20230607115528L
private const val REPOSITORY_ID = 20230613071811L
private const val RUN_ID = 20230622095805L
private const val RESOLVED_JOB_CONFIG_CONTEXT = "12345678"

/** A [Hierarchy] object for the test repository. */
private val repositoryHierarchy = Hierarchy(
    Repository(REPOSITORY_ID, ORGANIZATION_ID, PRODUCT_ID, RepositoryType.GIT, REPOSITORY_URL),
    Product(PRODUCT_ID, ORGANIZATION_ID, "testProduct"),
    Organization(ORGANIZATION_ID, "test organization")
)

/** A mock representing the current ORT run. */
private val currentOrtRun = mockk<OrtRun> {
    every { id } returns RUN_ID
    every { environmentConfigPath } returns null
    every { organizationId } returns ORGANIZATION_ID
    every { productId } returns PRODUCT_ID
    every { repositoryId } returns REPOSITORY_ID
    every { resolvedJobConfigContext } returns RESOLVED_JOB_CONFIG_CONTEXT
}

/** A file representing the checkout folder of the current repository. */
private val repositoryFolder = File("repositoryCheckoutLocation")

/** A default [AdminConfig] object used in tests. */
val adminConfig = AdminConfig()

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
        every { credentialResolverFun } returns mockk()
        every { configManager } returns mockk()
        coEvery { setupAuthentication(any(), any()) } just runs
    }

/**
 * Create a mock [EnvironmentConfigGenerator] that is prepared for an invocation of its
 * [EnvironmentConfigGenerator.generateApplicable] method.
 */
private fun mockGenerator(): EnvironmentConfigGenerator<EnvironmentServiceDefinition> =
    mockk {
        coEvery { generateApplicable(any(), any()) } just runs
    }

private fun mockSecretRepository(
    extraSetup: SecretRepository.() -> Unit = {}
): SecretRepository =
    mockk {
        every { getByIdAndName(any(), any()) } returns null
        extraSetup()
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
 * Create a mock [AdminConfigService] that is prepared to return the [adminConfig] when loading the admin config.
 */
private fun createMockAdminConfigService(): AdminConfigService = mockk<AdminConfigService> {
    every { loadAdminConfig(Context(RESOLVED_JOB_CONFIG_CONTEXT), ORGANIZATION_ID) } returns adminConfig
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

    slotBuilder.captured.resolverFun shouldBe context.credentialResolverFun
    slotBuilder.captured.adminConfig shouldBe adminConfig

    if (expectedDefinitions != null) {
        slotDefinitions.captured shouldBe expectedDefinitions
    }

    return slotBuilder.captured to slotDefinitions.captured
}

/**
 * Prepare this mock for an [InfrastructureServiceDeclarationRepository] to expect calls that assign infrastructure
 * services to the current ORT run. Return a list that contains the assigned services after running the test.
 */
private fun InfrastructureServiceDeclarationRepository.expectServiceAssignments():
        List<InfrastructureServiceDeclaration> {
    val assignedServices = mutableListOf<InfrastructureServiceDeclaration>()

    val slotService = slot<InfrastructureServiceDeclaration>()
    every { getOrCreateForRun(capture(slotService), RUN_ID) } answers {
        firstArg<InfrastructureServiceDeclaration>().also { service ->
            assignedServices += service
        }
    }

    return assignedServices
}
