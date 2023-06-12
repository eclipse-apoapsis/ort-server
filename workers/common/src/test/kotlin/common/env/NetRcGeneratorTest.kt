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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

class NetRcGeneratorTest : WordSpec({
    "targetFile" should {
        "return the correct path in the user's home directory" {
            val target = NetRcGenerator().targetFile()

            target.parent shouldBe System.getProperty("user.home")
            target.name shouldBe ".netrc"
        }
    }

    "generate" should {
        "generate the correct content of the .netrc file" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")
            val secretValues = mapOf(
                secUser1 to "scott",
                secPass1 to "tiger",
                secUser2 to "harry",
                secPass2 to "Hirsch"
            )

            val service1 = createInfrastructureService("https://repo1.example.org", secUser1, secPass1)
            val service2 = createInfrastructureService("https://repo2.example.org", secUser2, secPass2)
            val service3 = createInfrastructureService("https://repo3.example.org", secUser2, secPass2)

            val capturedSecrets = mutableListOf<Secret>()
            val context = mockk<WorkerContext>()
            coEvery { context.resolveSecrets(*varargAll { capturedSecrets.add(it) }) } returns secretValues

            val expectedLines = listOf(
                "machine repo1.example.org login ${secretValues[secUser1]} password ${secretValues[secPass1]}",
                "machine repo2.example.org login ${secretValues[secUser2]} password ${secretValues[secPass2]}",
                "machine repo3.example.org login ${secretValues[secUser2]} password ${secretValues[secPass2]}"
            )

            val generator = NetRcGenerator()
            val generatedLines = generator.generate(context, listOf(service1, service2, service3))

            generatedLines shouldContainExactly expectedLines

            capturedSecrets shouldContainExactlyInAnyOrder listOf(
                secUser1,
                secPass1,
                secUser2,
                secPass2,
                secUser2,
                secPass2
            )
        }

        "handle multiple services referencing the same host" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")
            val secUser3 = createSecret("user3Secret")
            val secPass3 = createSecret("pass3Secret")
            val secretValues = mapOf(
                secUser1 to "scott",
                secPass1 to "tiger"
            )

            val service1 = createInfrastructureService("https://repo.example.org/r1", secUser1, secPass1)
            val service2 = createInfrastructureService("https://repo.example.org/r2", secUser2, secPass2)
            val service3 = createInfrastructureService("https://repo.example.org:443/r3", secUser3, secPass3)

            val capturedSecrets = mutableListOf<Secret>()
            val context = mockk<WorkerContext>()
            coEvery { context.resolveSecrets(*varargAll { capturedSecrets.add(it) }) } returns secretValues

            val expectedLines = listOf(
                "machine repo.example.org login ${secretValues[secUser1]} password ${secretValues[secPass1]}"
            )

            val generator = NetRcGenerator()
            val generatedLines = generator.generate(context, listOf(service1, service2, service3))

            generatedLines shouldContainExactly expectedLines

            capturedSecrets shouldContainExactlyInAnyOrder listOf(secUser1, secPass1)
        }

        "ignore services with an invalid URL" {
            val secUser1 = createSecret("user1Secret")
            val secPass1 = createSecret("pass1Secret")
            val secUser2 = createSecret("user2Secret")
            val secPass2 = createSecret("pass2Secret")
            val secretValues = mapOf(
                secUser1 to "scott",
                secPass1 to "tiger"
            )

            val service1 = createInfrastructureService("https://repo.example.org", secUser1, secPass1)
            val service2 = createInfrastructureService("? invalid URL?!", secUser2, secPass2)

            val context = mockk<WorkerContext>()
            coEvery { context.resolveSecrets(*anyVararg()) } returns secretValues

            val expectedLines = listOf(
                "machine repo.example.org login ${secretValues[secUser1]} password ${secretValues[secPass1]}"
            )

            val generator = NetRcGenerator()
            val generatedLines = generator.generate(context, listOf(service1, service2))

            generatedLines shouldContainExactly expectedLines
        }
    }
})

/**
 * Return a test [InfrastructureService] based on the provided parameters.
 */
private fun createInfrastructureService(
    url: String,
    userSecret: Secret,
    passwordSecret: Secret
): InfrastructureService =
    InfrastructureService(
        name = url,
        url = url,
        usernameSecret = userSecret,
        passwordSecret = passwordSecret,
        organization = null,
        product = null
    )

/**
 * Return a test [Secret] based on the given [name].
 */
private fun createSecret(name: String): Secret =
    Secret(
        id = 0L,
        path = name,
        name = name,
        description = null,
        organization = null,
        product = null,
        repository = null
    )
