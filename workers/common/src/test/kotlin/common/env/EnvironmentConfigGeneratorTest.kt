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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

class EnvironmentConfigGeneratorTest : StringSpec({
    "Filtering for applicable definitions works" {
        val def1 = EnvironmentServiceDefinitionA(mockk())
        val def2 = EnvironmentServiceDefinitionB(mockk())
        val def3 = EnvironmentServiceDefinitionB(mockk())
        val def4 = EnvironmentServiceDefinitionA(mockk())
        val def5 = EnvironmentServiceDefinitionA(mockk())
        val def6 = EnvironmentServiceDefinitionB(mockk())
        val builder = mockk<ConfigFileBuilder>()

        val generator = GeneratorTestImpl()
        generator.generateApplicable(builder, listOf(def1, def2, def3, def4, def5, def6))

        generator.providedBuilder shouldBe builder
        generator.providedDefinitions shouldContainExactlyInAnyOrder listOf(def1, def4, def5)
    }
})

/**
 * A test environment service definition class.
 */
private class EnvironmentServiceDefinitionA(service: ResolvedInfrastructureService) : EnvironmentServiceDefinition(
    service
)

/**
 * Another test environment service definition class.
 */
private class EnvironmentServiceDefinitionB(service: ResolvedInfrastructureService) : EnvironmentServiceDefinition(
    service
)

/**
 * A test generator implementation that allows inspecting the data passed to its [generate] function.
 */
private class GeneratorTestImpl : EnvironmentConfigGenerator<EnvironmentServiceDefinitionA> {
    /** Stores the builder passed to the [generate] function. */
    var providedBuilder: ConfigFileBuilder? = null

    /** Stores the definitions passed to the [generate] function. */
    val providedDefinitions = mutableListOf<EnvironmentServiceDefinition>()

    override val environmentDefinitionType: Class<EnvironmentServiceDefinitionA> =
        EnvironmentServiceDefinitionA::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<EnvironmentServiceDefinitionA>) {
        providedBuilder = builder
        providedDefinitions += definitions
    }
}
