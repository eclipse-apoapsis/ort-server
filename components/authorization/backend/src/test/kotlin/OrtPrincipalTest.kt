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

package org.eclipse.apoapsis.ortserver.core.authorization

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.authorization.OrtPrincipal
import org.eclipse.apoapsis.ortserver.components.authorization.hasRole
import org.eclipse.apoapsis.ortserver.components.authorization.isSuperuser
import org.eclipse.apoapsis.ortserver.components.authorization.roles.Superuser

class OrtPrincipalTest : WordSpec({
    val role1 = "role1"
    val role2 = "role2"
    val role3 = "role3"

    "hasRole" should {
        "return true if the principal has the requested role" {
            val principal = OrtPrincipal(mockk(), setOf(role1, role2, role3))

            principal.hasRole(role1) shouldBe true
            principal.hasRole(role2) shouldBe true
            principal.hasRole(role3) shouldBe true
        }

        "return false if the principal does not have the requested role" {
            val principal = OrtPrincipal(mockk(), setOf(role1, role2))

            principal.hasRole(role3) shouldBe false
        }

        "return false if the principal is null" {
            null.hasRole(role1) shouldBe false
        }
    }

    "isSuperuser" should {
        "return true if the principal has the superuser role" {
            val principal = OrtPrincipal(mockk(), setOf(role1, role2, role3, Superuser.ROLE_NAME))

            principal.isSuperuser() shouldBe true
        }

        "return false if the principal does not have the superuser role" {
            val principal = OrtPrincipal(mockk(), setOf(role1, role2, role3))

            principal.isSuperuser() shouldBe false
        }

        "return false if the principal is null" {
            null.isSuperuser() shouldBe false
        }
    }
})
