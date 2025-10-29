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

package org.eclipse.apoapsis.ortserver.components.authorization.routes

import com.auth0.jwt.interfaces.Payload

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole

class OrtServerPrincipalTest : WordSpec({
    "create()" should {
        "create an instance correctly from a JWT payload" {
            val userId = "0x93847-973498-734987"
            val username = "jdoe"
            val fullName = "John Doe"
            val payload = mockk<Payload> {
                every { subject } returns userId
                every { getClaim("preferred_username").asString() } returns username
                every { getClaim("name").asString() } returns fullName
            }
            val effectiveRole = mockk<EffectiveRole>()

            val principal = OrtServerPrincipal.create(payload, effectiveRole)

            principal.userId shouldBe userId
            principal.username shouldBe username
            principal.fullName shouldBe fullName
            principal.effectiveRole shouldBe effectiveRole
        }
    }

    "isAuthorized" should {
        "return true if an effective role is present" {
            val principal = OrtServerPrincipal(
                userId = "user-id",
                username = "username",
                fullName = "Full Name",
                role = mockk()
            )

            principal.isAuthorized shouldBe true
        }

        "return false if no effective role is present" {
            val principal = OrtServerPrincipal(
                userId = "user-id",
                username = "username",
                fullName = "Full Name",
                role = null
            )

            principal.isAuthorized shouldBe false
        }
    }

    "effectiveRole" should {
        "return the effective role if present" {
            val effectiveRole = mockk<EffectiveRole>()
            val principal = OrtServerPrincipal(
                userId = "user-id",
                username = "username",
                fullName = "Full Name",
                role = effectiveRole
            )

            principal.effectiveRole shouldBe effectiveRole
        }

        "throw an exception if no effective role is present" {
            val principal = OrtServerPrincipal(
                userId = "user-id",
                username = "username",
                fullName = "Full Name",
                role = null
            )

            shouldThrow<AuthorizationException> {
                principal.effectiveRole
            }
        }
    }
})
