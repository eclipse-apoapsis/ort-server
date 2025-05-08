/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll

import java.net.URI

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.auth.AuthenticationEvent
import org.eclipse.apoapsis.ortserver.workers.common.auth.CredentialResolverFun
import org.eclipse.apoapsis.ortserver.workers.common.env.MockConfigFileBuilder.Companion.createInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

class NetRcManagerTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "createConfigFileBuilder()" should {
        "create a correct builder object" {
            val resolverFun = mockk<CredentialResolverFun>()
            val manager = NetRcManager.create(resolverFun, emptyList())

            val builder = manager.createConfigFileBuilder()

            builder.resolverFun shouldBe resolverFun
        }
    }

    "createNetRcGenerator()" should {
        "create a correct generator object" {
            val resolverFun = mockk<CredentialResolverFun>()
            val mockBuilder = MockConfigFileBuilder()
            val definition = EnvironmentServiceDefinition(
                createInfrastructureService(
                    "https://repo.example.org",
                    MockConfigFileBuilder.Companion.createSecret("s1"),
                    MockConfigFileBuilder.Companion.createSecret("s2")
                )
            )

            val manager = NetRcManager.create(resolverFun, emptyList())
            val generator = manager.createNetRcGenerator()

            generator.generate(mockBuilder.builder, listOf(definition))

            mockBuilder.homeFileName shouldBe ".netrc"
        }
    }

    "onAuthentication()" should {
        "write an entry to the .netrc file when a service is authenticated" {
            val service = createInfrastructureService()
            val services = listOf(service)

            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator> {
                coEvery { generate(mockBuilder, any()) } just runs
            }
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, services)

            manager.onAuthentication(AuthenticationEvent(service.name))

            val slotServices = slot<Collection<EnvironmentServiceDefinition>>()
            coVerify {
                mockGenerator.generate(mockBuilder, capture(slotServices))
            }

            slotServices.captured.map(EnvironmentServiceDefinition::service) shouldContainExactlyInAnyOrder services
        }

        "ignore services with unknown names" {
            val service1 = createInfrastructureService()
            val service2 = createInfrastructureService(url = "https://repo.example.org/other")

            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator>()
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, listOf(service1))

            manager.onAuthentication(AuthenticationEvent(service2.name))

            coVerify(exactly = 0) {
                mockGenerator.generate(any(), any())
            }
        }

        "ignore services with other credentials types" {
            val service = createInfrastructureService(credentialsTypes = setOf(CredentialsType.GIT_CREDENTIALS_FILE))
            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator>()
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, listOf(service))

            manager.onAuthentication(AuthenticationEvent(service.name))

            coVerify(exactly = 0) {
                mockGenerator.generate(any(), any())
            }
        }

        "add another service to the netrc file when it is authenticated" {
            val service1 = createInfrastructureService()
            val service2 = createInfrastructureService(url = "https://repo2.example.org/other")
            val services = listOf(service1, service2)

            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator> {
                coEvery { generate(mockBuilder, any()) } just runs
            }
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, services)

            manager.onAuthentication(AuthenticationEvent(service1.name))
            manager.onAuthentication(AuthenticationEvent(service2.name))

            val slotServices = mutableListOf<Collection<EnvironmentServiceDefinition>>()
            coVerify {
                mockGenerator.generate(mockBuilder, capture(slotServices))
            }

            slotServices shouldHaveSize 2
            slotServices[1].map(EnvironmentServiceDefinition::service) shouldContainExactlyInAnyOrder services
        }

        "replace a service for a specific host in the .netrc file" {
            val service1 = createInfrastructureService()
            val service2Url = URI.create(service1.url).resolve("other-repo.git").toString()
            val service2 = createInfrastructureService(url = service2Url)

            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator> {
                coEvery { generate(mockBuilder, any()) } just runs
            }
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, listOf(service1, service2))

            manager.onAuthentication(AuthenticationEvent(service1.name))
            manager.onAuthentication(AuthenticationEvent(service2.name))

            val slotServices = mutableListOf<Collection<EnvironmentServiceDefinition>>()
            coVerify {
                mockGenerator.generate(mockBuilder, capture(slotServices))
            }

            slotServices shouldHaveSize 2
            slotServices[1].map(EnvironmentServiceDefinition::service) shouldContainExactlyInAnyOrder listOf(service2)
        }

        "not write the .netrc file if there are no changes" {
            val service = createInfrastructureService()

            val mockBuilder = mockk<ConfigFileBuilder>()
            val mockGenerator = mockk<NetRcGenerator> {
                coEvery { generate(mockBuilder, any()) } just runs
            }
            val manager = createNetRcManagerSpy(mockBuilder, mockGenerator, listOf(service))

            manager.onAuthentication(AuthenticationEvent(service.name))
            manager.onAuthentication(AuthenticationEvent(service.name))

            coVerify(exactly = 1) {
                mockGenerator.generate(mockBuilder, any())
            }
        }
    }
})

/**
 * Create a spy for a [NetRcManager] instance that is prepared to use the given [mockBuilder] and [mockBuilder], and
 * which is initialized with the given [services].
 */
private fun createNetRcManagerSpy(
    mockBuilder: ConfigFileBuilder,
    mockGenerator: NetRcGenerator,
    services: Collection<InfrastructureService>
): NetRcManager =
    spyk(NetRcManager.create(mockk(), services)) {
        every { createConfigFileBuilder() } returns mockBuilder
        every { createNetRcGenerator() } returns mockGenerator
    }
