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

package org.eclipse.apoapsis.ortserver.workers.common.env.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.mockk

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.common.env.REGISTRY_URI
import org.eclipse.apoapsis.ortserver.workers.common.common.env.REMOTE_NAME
import org.eclipse.apoapsis.ortserver.workers.common.common.env.REMOTE_URL
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.ConanDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.GradleDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.MavenDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NpmAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NpmDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.NuGetDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnAuthMode
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.YarnDefinition

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
                val mirrorOf = "central"
                val properties = mapOf("id" to repositoryId, "mirrorOf" to mirrorOf)

                val definition = createSuccessful(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)

                definition.shouldBeInstanceOf<MavenDefinition>()
                definition.id shouldBe repositoryId
                definition.mirrorOf shouldBe mirrorOf
            }

            "be created with default values" {
                val repositoryId = "my-repository"
                val properties = mapOf("id" to repositoryId)

                val definition = createSuccessful(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)

                definition.shouldBeInstanceOf<MavenDefinition>()
                definition.mirrorOf should beNull()
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

            "allow overriding the credentials types" {
                val properties = mapOf(
                    "id" to "someId",
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to "GIT_CREDENTIALS_FILE, NETRC_FILE"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.MAVEN_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.of(
                    CredentialsType.GIT_CREDENTIALS_FILE,
                    CredentialsType.NETRC_FILE
                )
            }
        }

        "A ConanDefinition" should {
            "be created successfully" {
                val properties = mapOf(
                    "name" to REMOTE_NAME,
                    "url" to REMOTE_URL,
                    "verifySsl" to "true"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.CONAN_TYPE, properties)

                definition.shouldBeInstanceOf<ConanDefinition>()
                definition.name shouldBe REMOTE_NAME
                definition.url shouldBe REMOTE_URL
                definition.verifySsl shouldBe true
            }

            "fail if mandatory properties is missing" {
                val exception = createFailed(EnvironmentDefinitionFactory.CONAN_TYPE, emptyMap())

                exception.message shouldContain "'name'"
                exception.message shouldContain "'url'"
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "anotherProperty"
                val unsupportedProperty2 = "oneMoreUnsupportedProperty"

                val properties = mapOf(
                    "name" to REMOTE_NAME,
                    "url" to REMOTE_URL,
                    "verifySsl" to "true",
                    unsupportedProperty1 to "bar",
                    unsupportedProperty2 to "baz"
                )

                val exception = createFailed(EnvironmentDefinitionFactory.CONAN_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
                exception.message shouldContain "'$unsupportedProperty2'"
            }

            "allow overriding the credentials types" {
                val properties = mapOf(
                    "name" to REMOTE_NAME,
                    "url" to REMOTE_URL,
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to "NETRC_FILE,GIT_CREDENTIALS_FILE"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.CONAN_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.of(
                    CredentialsType.GIT_CREDENTIALS_FILE,
                    CredentialsType.NETRC_FILE
                )
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

            "allow overriding the credentials types" {
                val properties = mapOf(
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to " NETRC_FILE  , GIT_CREDENTIALS_FILE  "
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.of(
                    CredentialsType.GIT_CREDENTIALS_FILE,
                    CredentialsType.NETRC_FILE
                )
            }

            "fail for an unsupported value of the credentials types property" {
                val properties = mapOf(EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to "maybe")

                val exception = createFailed(EnvironmentDefinitionFactory.NPM_TYPE, properties)

                exception.message shouldContain
                        properties.getValue(EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY)
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

            "allow overriding the credentials types" {
                val properties = mapOf(
                    "sourceName" to "someSource",
                    "sourcePath" to "somePath",
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to ""
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.NUGET_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.noneOf(CredentialsType::class.java)
            }
        }

        "A Yarn definition" should {
            "be created with the provided values" {
                val alwaysAuth = "false"
                val authMode = YarnAuthMode.AUTH_IDENT

                val properties = mapOf(
                    "alwaysAuth" to alwaysAuth,
                    "authMode" to authMode.name
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
                definition.authMode shouldBe authMode
                definition.alwaysAuth shouldBe false
            }

            "be created with default values" {
                val properties = emptyMap<String, String>()

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
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

            "accept enum constants independent on case" {
                val properties = mapOf("authMode" to "auth_token")

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.shouldBeInstanceOf<YarnDefinition>()
                definition.authMode shouldBe YarnAuthMode.AUTH_TOKEN
            }

            "fail for an unsupported enum constant" {
                val properties = mapOf("authMode" to "unknown")

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain properties.getValue("authMode")
                exception.message shouldContain YarnAuthMode.AUTH_TOKEN.name
                exception.message shouldContain YarnAuthMode.AUTH_IDENT.name
            }

            "fail for an invalid boolean value" {
                val properties = mapOf("alwaysAuth" to "maybe")

                val exception = createFailed(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                exception.message shouldContain properties.getValue("alwaysAuth")
                exception.message shouldContain "TRUE"
                exception.message shouldContain "FALSE"
            }

            "allow overriding the credentials types" {
                val properties = mapOf(
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to "GIT_CREDENTIALS_FILE"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.YARN_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            }
        }

        "A GradleDefinition" should {
            "be created successfully" {
                val definition = createSuccessful(EnvironmentDefinitionFactory.GRADLE_TYPE, emptyMap())

                definition.shouldBeInstanceOf<GradleDefinition>()
            }

            "fail if there are unsupported properties" {
                val unsupportedProperty1 = "anotherProperty"
                val unsupportedProperty2 = "oneMoreUnsupportedProperty"
                val properties =
                    mapOf(unsupportedProperty1 to "bar", unsupportedProperty2 to "baz")

                val exception = createFailed(EnvironmentDefinitionFactory.GRADLE_TYPE, properties)

                exception.message shouldContain "'$unsupportedProperty1'"
                exception.message shouldContain "'$unsupportedProperty2'"
            }

            "allow overriding the credentials types" {
                val properties = mapOf(
                    EnvironmentDefinitionFactory.CREDENTIALS_TYPE_PROPERTY to "GIT_CREDENTIALS_FILE"
                )

                val definition = createSuccessful(EnvironmentDefinitionFactory.GRADLE_TYPE, properties)

                definition.credentialsTypes() shouldBe EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
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
