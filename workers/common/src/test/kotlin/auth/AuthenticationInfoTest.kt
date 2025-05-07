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

class AuthenticationInfoTest : WordSpec({
    "resolveSecret()" should {
        "return the value of a known secret" {
            val secretPath = "my/secret/path"
            val secretValue = "mySecretValue"
            val secret = Secret(0, secretPath, "someSecret", null, null, null, null)

            val info = AuthenticationInfo(
                secrets = mapOf(secretPath to secretValue),
                services = emptyList()
            )

            info.resolveSecret(secret) shouldBe secretValue
        }

        "throw an exception for an unknown secret" {
            val secret = Secret(0, "unknownPath", "unknownSecret", null, null, null, null)

            val info = AuthenticationInfo(emptyMap(), emptyList())

            val exception = shouldThrow<IllegalArgumentException> {
                info.resolveSecret(secret)
            }

            exception.message shouldInclude secret.path
        }
    }
})
