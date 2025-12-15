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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.every

import org.eclipse.apoapsis.ortserver.services.config.MavenCentralMirror
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.GradleDefinition

class GradleInitGeneratorTest : WordSpec({
    "environmentDefinitionType" should {
        "return the correct definition class" {
            val definitionType = GradleInitGenerator().environmentDefinitionType

            definitionType shouldBe GradleDefinition::class.java
        }
    }

    "generate" should {
        "generate the file at the correct location" {
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central"
            )

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            mockBuilder.homeFileName shouldBe ".gradle/init.gradle.kts"
        }

        "generate an empty file if MavenCentralMirror is null" {
            val mockBuilder = MockConfigFileBuilder()

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())

            mockBuilder.targetFiles should beEmpty()
        }

        "correctly replace placeholders in the template" {
            val mavenCentralMirror = MavenCentralMirror(
                id = "central",
                name = "Maven Central",
                url = "https://repo.maven.apache.org/maven2",
                mirrorOf = "central"
            )
            val regPlaceholder = Regex("#\\{\\w+}#")

            val mockBuilder = MockConfigFileBuilder()
            every { mockBuilder.adminConfig.mavenCentralMirror } returns mavenCentralMirror

            GradleInitGenerator().generate(mockBuilder.builder, emptyList())
            val lines = mockBuilder.generatedLines()

            lines.forAll {
                regPlaceholder.find(it) should beNull()
            }

            lines.find { mavenCentralMirror.url in it } shouldNot beNull()
        }
    }
})
