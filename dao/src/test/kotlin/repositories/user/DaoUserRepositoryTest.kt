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

package org.eclipse.apoapsis.ortserver.dao.repositories.user

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.User

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update

class DaoUserRepositoryTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var userRepository: DaoUserRepository

    beforeEach {
        userRepository = DaoUserRepository(dbExtension.db)
    }

    "upsert" should {
        "keep the creation and synchronization times, set the update time and clear the deletion time" {
            val lastSyncedAt = Clock.System.now().toDatabasePrecision()
            userRepository.upsert(User("user-id", "john.doe", "John Doe"))
            val createdAt = dbExtension.db.transaction { UserDao["user-id"].createdAt }
            val formerUpdatedAt = (Clock.System.now() - 1.hours).toDatabasePrecision()
            dbExtension.db.transaction {
                UsersTable.update({ UsersTable.id eq "user-id" }) {
                    it[updatedAt] = formerUpdatedAt
                    it[deletedAt] = Clock.System.now()
                    it[this.lastSyncedAt] = lastSyncedAt
                }
            }

            userRepository.upsert(User("user-id", "john.smith", "John Smith"))

            dbExtension.db.transaction {
                UserDao["user-id"].createdAt shouldBe createdAt
                UserDao["user-id"].updatedAt shouldBeGreaterThan formerUpdatedAt
            }

            userRepository.get("user-id") shouldBe User(
                userId = "user-id",
                username = "john.smith",
                fullName = "John Smith",
                deletedAt = null,
                lastSyncedAt = lastSyncedAt
            )
        }

        "keep the update time if nothing has changed" {
            val user = User("user-id", "john.doe", null)
            userRepository.upsert(user)
            val formerUpdatedAt = setUpdatedAtToOneHourAgo(dbExtension.db, "user-id")

            userRepository.upsert(user)

            dbExtension.db.transaction { UserDao["user-id"].updatedAt shouldBe formerUpdatedAt }
        }

        "set the update time if only the full name has changed" {
            userRepository.upsert(User("user-id", "john.doe", null))
            val formerUpdatedAt = setUpdatedAtToOneHourAgo(dbExtension.db, "user-id")

            userRepository.upsert(User("user-id", "john.doe", "John Doe"))

            dbExtension.db.transaction { UserDao["user-id"].updatedAt shouldBeGreaterThan formerUpdatedAt }
        }
    }

    "markDeleted" should {
        "keep the user with their names and set the deletion and update times" {
            userRepository.upsert(User("user-id", "john.doe", "John Doe"))
            val formerUpdatedAt = setUpdatedAtToOneHourAgo(dbExtension.db, "user-id")

            userRepository.markDeleted("user-id")

            dbExtension.db.transaction { UserDao["user-id"].updatedAt shouldBeGreaterThan formerUpdatedAt }

            userRepository.get("user-id").shouldNotBeNull {
                username shouldBe "john.doe"
                fullName shouldBe "John Doe"
                deletedAt.shouldNotBeNull()
                lastSyncedAt.shouldBeNull()
            }
        }
    }
})

/**
 * Set the update time of the user with the given [userId] to one hour ago and return this time, so that tests can
 * check whether a later write changes it.
 */
private suspend fun setUpdatedAtToOneHourAgo(db: Database, userId: String): Instant {
    val updatedAt = (Clock.System.now() - 1.hours).toDatabasePrecision()
    db.transaction {
        UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.updatedAt] = updatedAt }
    }

    return updatedAt
}
