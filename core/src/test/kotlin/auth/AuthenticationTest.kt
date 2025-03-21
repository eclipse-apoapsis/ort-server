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

package org.eclipse.apoapsis.ortserver.core.auth

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

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.Role
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleId
import org.eclipse.apoapsis.ortserver.clients.keycloak.RoleName
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.core.plugins.getRoles

class AuthenticationTest : WordSpec({
    val subject = "subject"
    val keyCloakRoles = setOf(
        Role(RoleId("1"), RoleName("role1")),
        Role(RoleId("2"), RoleName("role2")),
        Role(RoleId("3"), RoleName("role3"))
    )
    val roles = keyCloakRoles.mapTo(mutableSetOf()) { it.name.value }

    "getRoles" should {
        "return the roles from Keycloak" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.minutes).asCache<String, Set<String>>()
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { getUserClientRoles(UserId(subject)) } returns keyCloakRoles
            }

            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)
        }

        "use the cache for subsequent requests" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.minutes).asCache<String, Set<String>>()
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { getUserClientRoles(UserId(subject)) } returns keyCloakRoles
            }

            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)
            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)
            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)

            coVerify(exactly = 1) {
                keycloakClient.getUserClientRoles(UserId(subject))
            }
        }

        "request the roles from Keycloak when the cache has expired" {
            val cache = Caffeine.newBuilder().expireAfterWrite(1.milliseconds).asCache<String, Set<String>>()
            val keycloakClient = mockk<KeycloakClient> {
                coEvery { getUserClientRoles(UserId(subject)) } returns keyCloakRoles
            }

            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)
            delay(10)
            getRoles(subject, keycloakClient, cache) should containExactlyInAnyOrder(roles)

            coVerify(exactly = 2) {
                keycloakClient.getUserClientRoles(UserId(subject))
            }
        }
    }
})
