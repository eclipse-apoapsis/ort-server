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

package org.ossreviewtoolkit.server.workers.common.env.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.mockk

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.workers.common.common.env.REGISTRY_URI
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.MavenDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.NpmAuthMode
import org.ossreviewtoolkit.server.workers.common.env.definition.NpmDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.NuGetAuthMode
import org.ossreviewtoolkit.server.workers.common.env.definition.NuGetDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.YarnAuthMode
import org.ossreviewtoolkit.server.workers.common.env.definition.YarnDefinition

class EnvironmentDefinitionFactoryTest : WordSpec() {
    /** A mock for an infrastructure service to be used by tests. */
    private val service = mockk<InfrastructureService>()

    /** The factory to be tested. */
    private val factory = EnvironmentDefinitionFactory()

    init {
        "An unknown definition type" should {
            "yield a failure result" {
                val unsupportedType = "anUnknownDefinitionType"

                val exception = createFailed(unsupportedType, emptyMap())

                exception.message shouldContain unsupportedType
            }
        }

        "A MavenDefinition" should {
            "be created successfully" {
                val repositoryId = "my-repository"
                val properties = mapOf("id" to repositoryId)

                val definition = createSuccessful(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)

                definition.shouldBeInstanceOf<MavenDefinition>()
                definition.id shouldBe repositoryId
            }

            "fail if the ID is missing" {
                val exception = createFailed(EnvironmentDefinitionFactory.MAVEN_TYPE, emptyMap())

                exception.message shouldContain "'id'"
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "anotherProperty"
                val unsupportedProperty2 = "oneMoreUnsupportedProperty"
                val properties = mapOf("id" to "foo", unsupportedProperty1 to "bar", unsupportedProperty2 to "baz")

                val exception = createFailed(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
                exception.message shouldContain "'$unsupportedProperty2'"
            }

            "not fail for the standard service property" {
                val properties = mapOf("id" to "someId", EnvironmentDefinitionFactory.SERVICE_PROPERTY to "service")

                createSuccessful(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)
            }
        }

        "An NPM definition" should {
            "be created with the provided values" {
                val scope = "testScope"
                val email = "test@example.org"
                val authMode = NpmAuthMode.USERNAME_PASSWORD_AUTH

                val properties = mapOf(
                    "scope" to scope,
                    "email" to email,
                    "authMode" to authMode.name,
                    "alwaysAuth" to "false"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                definition.shouldBeInstanceOf<NpmDefinition>()
                definition.scope shouldBe scope
                definition.email shouldBe email
                definition.authMode shouldBe authMode
                definition.alwaysAuth shouldBe false
            }

            "be created with default values" {
                val properties = emptyMap<String, String>()

                val definition = createSuccessful(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                definition.shouldBeInstanceOf<NpmDefinition>()
                definition.scope should beNull()
                definition.email should beNull()
                definition.authMode shouldBe NpmAuthMode.PASSWORD
                definition.alwaysAuth shouldBe true
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "anotherProperty"
                val unsupportedProperty2 = "oneMoreUnsupportedProperty"
                val properties = mapOf("id" to "foo", unsupportedProperty1 to "bar", unsupportedProperty2 to "baz")

                val exception = createFailed(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
                exception.message shouldContain "'$unsupportedProperty2'"
            }

            "accept enum constants independent on case" {
                val properties = mapOf("authMode" to "password_Auth")

                val definition = createSuccessful(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                definition.shouldBeInstanceOf<NpmDefinition>()
                definition.authMode shouldBe NpmAuthMode.PASSWORD_AUTH
            }

            "fail for an unsupported enum constant" {
                val properties = mapOf("authMode" to "an unknown auth mode")

                val exception = createFailed(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                exception.message shouldContain properties.getValue("authMode")
                exception.message shouldContain NpmAuthMode.PASSWORD_AUTH.name
            }

            "fail for an invalid boolean value" {
                val properties = mapOf("alwaysAuth" to "maybe")

                val exception = createFailed(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                exception.message shouldContain properties.getValue("alwaysAuth")
                exception.message shouldContain "TRUE"
                exception.message shouldContain "FALSE"
            }
        }

        "A NuGet definition" should {
            "be created with the provided values" {
                val sourceName = "nuget.org"
                val sourcePath = "https://api.nuget.org/v3/index.json"
                val sourceProtocolVersion = "3"
                val authMode = NuGetAuthMode.PASSWORD

                val properties = mapOf(
                    "sourceName" to sourceName,
                    "sourcePath" to sourcePath,
                    "sourceProtocolVersion" to sourceProtocolVersion,
                    "authMode" to authMode.name,
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                definition.shouldBeInstanceOf<NuGetDefinition>()
                definition.sourceName shouldBe sourceName
                definition.sourcePath shouldBe sourcePath
                definition.sourceProtocolVersion shouldBe sourceProtocolVersion
                definition.authMode shouldBe authMode
            }

            "be created with default values" {
                val sourceName = "nuget.org"
                val sourcePath = "https://api.nuget.org/v3/index.json"

                val properties = mapOf(
                    "sourceName" to sourceName,
                    "sourcePath" to sourcePath
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                definition.shouldBeInstanceOf<NuGetDefinition>()
                definition.sourceName shouldBe sourceName
                definition.sourcePath shouldBe sourcePath
                definition.sourceProtocolVersion shouldBe null
                definition.authMode shouldBe NuGetAuthMode.API_KEY
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "oneMoreUnsupportedProperty"
                val properties = mapOf("sourceName" to "foo", "sourcePath" to "bar", unsupportedProperty1 to "baz")

                val exception = createFailed(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
            }

            "accept enum constants independent on case" {
                val sourceName = "nuget.org"
                val sourcePath = "https://api.nuget.org/v3/index.json"
                val properties = mapOf(
                    "sourceName" to sourceName,
                    "sourcePath" to sourcePath,
                    "authMode" to "apI_kEy"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                definition.shouldBeInstanceOf<NuGetDefinition>()
                definition.authMode shouldBe NuGetAuthMode.API_KEY
            }

            "fail for an unsupported enum constant" {
                val sourceName = "nuget.org"
                val sourcePath = "https://api.nuget.org/v3/index.json"
                val properties = mapOf(
                    "sourceName" to sourceName,
                    "sourcePath" to sourcePath,
                    "authMode" to "an unknown auth mode"
                )

                val exception = createFailed(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                exception.message shouldContain properties.getValue("authMode")
                exception.message shouldContain NuGetAuthMode.API_KEY.name
                exception.message shouldContain NuGetAuthMode.PASSWORD.name
            }
        }

        "A Yarn definition" should {
            "be created with the provided values" {
                val alwaysAuth = "false"
                val authMode = YarnAuthMode.AUTH_IDENT

                val properties = mapOf(
                    "registryUri" to REGISTRY_URI,
                    "alwaysAuth" to alwaysAuth,
                    "authMode" to authMode.name
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
                definition.registryUri shouldBe REGISTRY_URI
                definition.authMode shouldBe authMode
                definition.alwaysAuth shouldBe false
            }

            "be created with default values" {
                val properties = mapOf("registryUri" to REGISTRY_URI)

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
                definition.registryUri shouldBe REGISTRY_URI
                definition.authMode shouldBe YarnAuthMode.AUTH_TOKEN
                definition.alwaysAuth shouldBe true
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "anotherProperty"
                val unsupportedProperty2 = "oneMoreUnsupportedProperty"
                val properties =
                    mapOf("registryUri" to REGISTRY_URI, unsupportedProperty1 to "bar", unsupportedProperty2 to "baz")

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
                exception.message shouldContain "'$unsupportedProperty2'"
            }

            "fail if there are missing mandatory properties" {
                val properties = emptyMap<String, String>()

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain "Missing required properties: 'registryUri'"
            }

            "accept enum constants independent on case" {
                val properties = mapOf("registryUri" to REGISTRY_URI, "authMode" to "auth_token")

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
                definition.authMode shouldBe YarnAuthMode.AUTH_TOKEN
            }

            "fail for an unsupported enum constant" {
                val properties = mapOf("registryUri" to REGISTRY_URI, "authMode" to "unknown")

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain properties.getValue("authMode")
                exception.message shouldContain YarnAuthMode.AUTH_TOKEN.name
                exception.message shouldContain YarnAuthMode.AUTH_IDENT.name
            }

            "fail for an invalid boolean value" {
                val properties = mapOf("registryUri" to REGISTRY_URI, "alwaysAuth" to "maybe")

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain properties.getValue("alwaysAuth")
                exception.message shouldContain "TRUE"
                exception.message shouldContain "FALSE"
            }
        }
    }

    /**
     * Invoke the factory under test with the given [typeName] and [properties] and expect a success result. Return
     * the [EnvironmentServiceDefinition] that has been created.
     */
    private fun createSuccessful(typeName: String, properties: Map<String, String>): EnvironmentServiceDefinition {
        val result = factory.createDefinition(typeName, service, properties)

        return result shouldBeSuccess { definition ->
            definition.service shouldBe service
        }
    }

    /**
     * Invoke the factory under test with the given [typeName] and [properties] and expect a failure result. Return
     * the [EnvironmentConfigException] from the result.
     */
    private fun createFailed(typeName: String, properties: Map<String, String>): EnvironmentConfigException {
        val result = factory.createDefinition(typeName, service, properties)

        return result.shouldBeFailure<EnvironmentConfigException>()
    }
}
