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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import kotlin.io.path.createTempDirectory

import org.eclipse.apoapsis.ortserver.cli.model.AuthenticationStorage
import org.eclipse.apoapsis.ortserver.cli.model.HostAuthenticationDetails
import org.eclipse.apoapsis.ortserver.cli.model.Tokens
import org.eclipse.apoapsis.ortserver.cli.utils.configDir

class AuthenticationStorageTest : StringSpec({
    val tmpConfigDir = createTempDirectory("osc-auth-test").toFile()
    val tmpAuthFile = tmpConfigDir.resolve("auth.yml")

    beforeSpec {
        mockkStatic("org.eclipse.apoapsis.ortserver.cli.utils.ConfigDirHelperKt")
        every { configDir } returns tmpConfigDir
    }

    afterEach {
        unmockkAll()

        tmpAuthFile.delete()
    }

    afterSpec { tmpConfigDir.deleteRecursively() }

    "Authentication Storage" should {
        "be able to store and retrieve authentication information" {
            val auth = exampleAuthentication()

            AuthenticationStorage.store(auth)

            AuthenticationStorage.get() shouldBe auth
        }

        "be able to clear stored authentication information" {
            val auth = exampleAuthentication()

            AuthenticationStorage.store(auth)
            AuthenticationStorage.clear()

            AuthenticationStorage.get() shouldBe null
        }
    }
})

private fun exampleAuthentication() = HostAuthenticationDetails(
    baseUrl = "https://example.com/",
    tokenUrl = "https://example.com/token",
    clientId = "client-id",
    username = "user",
    tokens = Tokens(
        access = "access-token",
        refresh = "refresh-token"
    )
)
