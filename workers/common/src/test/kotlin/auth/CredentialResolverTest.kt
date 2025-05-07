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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude

import org.eclipse.apoapsis.ortserver.model.Secret

class CredentialResolverTest : WordSpec({
    "credentialResolver()" should {
        "return a resolver function based on authentication information" {
            val secretPath = "testSecret"
            val secretValue = "verySecretValue"
            val secret = Secret(0, secretPath, "someSecret", null, null, null, null)
            val unknownSecret = Secret(0, "unknownPath", "unknownSecret", null, null, null, null)

            val authInfo = AuthenticationInfo(
                secrets = mapOf(secretPath to secretValue),
                services = emptyList()
            )
            val resolver = credentialResolver(authInfo)

            resolver(secret) shouldBe secretValue

            shouldThrow<IllegalArgumentException> {
                resolver(unknownSecret)
            }
        }
    }

    "undefinedCredentialResolver" should {
        "throw an exception when called" {
            val secret = Secret(0, "somePath", "someSecret", null, null, null, null)

            val exception = shouldThrow<IllegalArgumentException> {
                undefinedCredentialResolver(secret)
            }

            exception.message shouldInclude secret.path
        }
    }

    "resolveCredentials()" should {
        "return a map with resolved secrets" {
            val secret1 = Secret(0, "secretPath1", "secret1", null, null, null, null)
            val secretValue1 = "very-secret"
            val secret2 = Secret(0, "secretPath2", "secret2", null, null, null, null)
            val secretValue2 = "even-more.secret"

            val secretsMap = mapOf(
                secret1 to secretValue1,
                secret2 to secretValue2
            )
            val resolverFun: CredentialResolverFun = secretsMap::getValue

            val resolvedSecrets = resolveCredentials(resolverFun, secret1, secret2)

            resolvedSecrets shouldBe secretsMap
        }
    }
})
