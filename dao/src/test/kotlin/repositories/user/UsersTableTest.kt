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
import io.kotest.matchers.comparables.beBetween
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.User

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

class UsersTableTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "insertOrUpdateAll" should {
        "store new and known users with the synchronization time and keep the creation time" {
            val formerTime = (Clock.System.now() - 1.hours).toDatabasePrecision()
            val syncedAt = Clock.System.now().toDatabasePrecision()
            dbExtension.db.transaction {
                UserDao.insertOrUpdate(User("known-id", "john.doe", "John Doe"))
                UsersTable.update({ UsersTable.id eq "known-id" }) {
                    it[createdAt] = formerTime
                    it[deletedAt] = formerTime
                }
            }

            val beforeStoring = Clock.System.now().toDatabasePrecision()
            dbExtension.db.transaction {
                UserDao.insertOrUpdateAll(
                    listOf(User("known-id", "john.smith", "John Smith"), User("new-id", "jane.doe", "Jane Doe")),
                    syncedAt
                )
            }
            val afterStoring = Clock.System.now()

            dbExtension.db.transaction {
                with(UserDao["known-id"]) {
                    username shouldBe "john.smith"
                    fullName shouldBe "John Smith"
                    createdAt shouldBe formerTime
                    deletedAt should beNull()
                    lastSyncedAt shouldBe syncedAt
                }

                with(UserDao["new-id"]) {
                    username shouldBe "jane.doe"
                    fullName shouldBe "Jane Doe"
                    createdAt should beBetween(beforeStoring, afterStoring)
                    deletedAt should beNull()
                    lastSyncedAt shouldBe syncedAt
                }
            }
        }

        "leave a user unchanged who was marked deleted after the synchronization time" {
            val syncedAt = (Clock.System.now() - 1.hours).toDatabasePrecision()
            val deletedAt = Clock.System.now().toDatabasePrecision()
            dbExtension.db.transaction {
                UserDao.insertOrUpdate(User("user-id", "john.doe"))
                UserDao.markDeleted(listOf("user-id"), deletedAt)
            }

            dbExtension.db.transaction { UserDao.insertOrUpdateAll(listOf(User("user-id", "john.doe")), syncedAt) }

            dbExtension.db.transaction {
                UserDao["user-id"].deletedAt shouldBe deletedAt
                UserDao["user-id"].lastSyncedAt should beNull()
            }
        }

        "keep the update time of a user that has not changed" {
            val user = User("user-id", "john.doe", "John Doe")
            val formerUpdatedAt = (Clock.System.now() - 1.hours).toDatabasePrecision()
            dbExtension.db.transaction {
                UserDao.insertOrUpdate(user)
                UsersTable.update({ UsersTable.id eq "user-id" }) { it[updatedAt] = formerUpdatedAt }
            }

            dbExtension.db.transaction { UserDao.insertOrUpdateAll(listOf(user), Clock.System.now()) }

            dbExtension.db.transaction { UserDao["user-id"].updatedAt shouldBe formerUpdatedAt }
        }
    }

    "markDeleted" should {
        "mark users deleted and keep the deletion time of users that are already marked deleted" {
            val formerDeletedAt = (Clock.System.now() - 1.hours).toDatabasePrecision()
            val deletedAt = Clock.System.now().toDatabasePrecision()
            dbExtension.db.transaction {
                UserDao.insertOrUpdate(User("deleted-id", "old.user"))
                UserDao.insertOrUpdate(User("active-id", "john.doe"))
                UsersTable.update({ UsersTable.id eq "deleted-id" }) {
                    it[this.deletedAt] = formerDeletedAt
                    it[updatedAt] = formerDeletedAt
                }
            }

            dbExtension.db.transaction { UserDao.markDeleted(listOf("deleted-id", "active-id"), deletedAt) }

            dbExtension.db.transaction {
                UserDao["deleted-id"].deletedAt shouldBe formerDeletedAt
                UserDao["deleted-id"].updatedAt shouldBe formerDeletedAt
                UserDao["active-id"].deletedAt shouldBe deletedAt
            }
        }
    }
})
