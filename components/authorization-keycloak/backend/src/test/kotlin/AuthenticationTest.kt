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

package org.eclipse.apoapsis.ortserver.components.authorization.keycloak

import com.github.benmanes.caffeine.cache.Caffeine

import com.sksamuel.aedile.core.asCache
import com.sksamuel.aedile.core.expireAfterWrite

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.delay

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.service.AuthorizationService

class AuthenticationTest : WordSpec({
    val subject = "subject"
    val userRoles = setOf("role1", "role2", "role3")

    "getRoles" should {
        "return the roles from Keycloak" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.minutes).asCache<String, Set<String>>()
            val authorizationService = mockk<AuthorizationService> {
                coEvery { getUserRoleNames(subject) } returns userRoles
            }

            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)
        }

        "use the cache for subsequent requests" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.minutes).asCache<String, Set<String>>()
            val authorizationService = mockk<AuthorizationService> {
                coEvery { getUserRoleNames(subject) } returns userRoles
            }

            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)
            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)
            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)

            coVerify(exactly = 1) {
                authorizationService.getUserRoleNames(subject)
            }
        }

        "request the roles from Keycloak when the cache has expired" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.milliseconds).asCache<String, Set<String>>()
            val authorizationService = mockk<AuthorizationService> {
                coEvery { getUserRoleNames(subject) } returns userRoles
            }

            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)
            delay(10)
            getRoles(subject, authorizationService, cache) should containExactlyInAnyOrder(userRoles)

            coVerify(exactly = 2) {
                authorizationService.getUserRoleNames(subject)
            }
        }
    }
})
