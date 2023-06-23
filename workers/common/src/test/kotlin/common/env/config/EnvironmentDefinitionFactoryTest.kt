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
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.mockk

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.MavenDefinition

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
