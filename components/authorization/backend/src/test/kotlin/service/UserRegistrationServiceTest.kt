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

import com.github.benmanes.caffeine.cache.Ticker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.repositories.UserRepository

class UserRegistrationServiceTest : WordSpec({
    val user = User(userId = "user-id", username = "john.doe", fullName = "John Doe")

    "register" should {
        "store a user the first time" {
            val repository = createRepository()
            val service = UserRegistrationService(repository)

            service.register(user)

            coVerify(exactly = 1) { repository.upsert(user) }
        }

        "not store a user again with the same names within five minutes" {
            val repository = createRepository()
            val ticker = TestTicker()
            val service = UserRegistrationService(repository, ticker)

            service.register(user)
            ticker.advance(4.minutes + 59.seconds)
            service.register(user)

            coVerify(exactly = 1) { repository.upsert(user) }
        }

        "store a user again after five minutes" {
            val repository = createRepository()
            val ticker = TestTicker()
            val service = UserRegistrationService(repository, ticker)

            service.register(user)
            ticker.advance(5.minutes)
            service.register(user)

            coVerify(exactly = 2) { repository.upsert(user) }
        }

        "store a user again at once when their names have changed" {
            val repository = createRepository()
            val service = UserRegistrationService(repository)
            val renamedUser = user.copy(username = "john.smith", fullName = "John Smith")

            service.register(user)
            service.register(renamedUser)

            coVerify(exactly = 1) { repository.upsert(user) }
            coVerify(exactly = 1) { repository.upsert(renamedUser) }
        }

        "try again on the next call when storing the user has failed" {
            val repository = createRepository()
            coEvery { repository.upsert(user) } throws IllegalStateException("Database not available") andThen user
            val service = UserRegistrationService(repository)

            shouldThrow<IllegalStateException> { service.register(user) }
            service.register(user)

            coVerify(exactly = 2) { repository.upsert(user) }
        }
    }
})

/** Create a mock [UserRepository] that stores every user successfully. */
private fun createRepository() = mockk<UserRepository> {
    coEvery { upsert(any()) } answers { firstArg() }
}

/** A [Ticker] whose time only changes when the test [advances][advance] it. */
private class TestTicker : Ticker {
    private var nanos = 0L

    override fun read() = nanos

    fun advance(duration: Duration) {
        nanos += duration.inWholeNanoseconds
    }
}
