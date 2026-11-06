/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.authorization.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker

import com.sksamuel.aedile.core.asCache

import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.repositories.UserRepository

/**
 * A service to store the current names of authenticated users in the users table. To avoid a database write on every
 * request, it remembers for a while which names it has stored for which user, and writes again only when the names
 * have changed or this time has passed.
 */
class UserRegistrationService(
    /** The repository to store the users. */
    private val userRepository: UserRepository,

    /** The source of time for the cache, which tests can replace. */
    ticker: Ticker = Ticker.systemTicker()
) {
    companion object {
        /** The maximum number of users to remember. */
        private const val MAX_CACHED_USERS = 10_000L

        /** How long to remember that a user has been stored. */
        private val CACHE_DURATION = 5.minutes
    }

    /** The users stored last, by their user ID. */
    private val storedUsers = Caffeine.newBuilder()
        .maximumSize(MAX_CACHED_USERS)
        .expireAfterWrite(CACHE_DURATION.toJavaDuration())
        .ticker(ticker)
        .asCache<String, User>()

    /**
     * Store the [user] with their current username and full name, unless the same names have been stored for them
     * recently. Throw an exception if the user cannot be stored; the next call then tries again.
     */
    suspend fun register(user: User) {
        if (storedUsers.getIfPresent(user.userId) == user) return

        userRepository.upsert(user)
        storedUsers.put(user.userId, user)
    }
}
