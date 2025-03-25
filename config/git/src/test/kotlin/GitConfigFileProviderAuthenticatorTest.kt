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

package org.eclipse.apoapsis.ortserver.config.git

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.mockk

import java.net.Authenticator

class GitConfigFileProviderAuthenticatorTest : WordSpec({
    var originalAuthenticator: Authenticator? = null

    beforeEach {
        originalAuthenticator = Authenticator.getDefault()
    }

    afterEach {
        Authenticator.setDefault(originalAuthenticator)
    }

    "install()" should {
        "check whether the authenticator is already installed" {
            val authenticator = GitConfigFileProviderAuthenticator.install("username", "password")

            GitConfigFileProviderAuthenticator.install("username", "password") shouldBeSameInstanceAs authenticator
        }
    }

    "uninstall()" should {
        "restore the previous authenticator" {
            val orgAuthenticator = mockk<Authenticator>()
            Authenticator.setDefault(orgAuthenticator)

            GitConfigFileProviderAuthenticator.install("username", "password")
            GitConfigFileProviderAuthenticator.uninstall() shouldBe orgAuthenticator

            Authenticator.getDefault() shouldBe orgAuthenticator
        }

        "not change anything if the authenticator is not installed" {
            val orgAuthenticator = mockk<Authenticator>()
            Authenticator.setDefault(orgAuthenticator)

            GitConfigFileProviderAuthenticator.uninstall() shouldBe orgAuthenticator

            Authenticator.getDefault() shouldBe orgAuthenticator
        }
    }

    "getPasswordAuthentication()" should {
        "return the provided credentials" {
            GitConfigFileProviderAuthenticator.install("username", "password")

            val credentials = Authenticator.requestPasswordAuthentication("", null, 443, "", "", "")

            credentials.userName shouldBe "username"
            credentials.password shouldBe "password".toCharArray()
        }
    }
})
