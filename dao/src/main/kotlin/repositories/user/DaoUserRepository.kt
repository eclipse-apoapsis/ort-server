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

import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.repositories.UserRepository

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update

/** An implementation of [UserRepository] that stores users in [UsersTable]. */
class DaoUserRepository(private val db: Database) : UserRepository {
    override suspend fun get(userId: String): User? = db.transaction { UserDao.findById(userId)?.mapToModel() }

    override suspend fun upsert(user: User): User = db.transaction {
        checkNotNull(UserDao.insertOrUpdate(user)).mapToModel()
    }

    override suspend fun markDeleted(userId: String) {
        db.transaction {
            val now = Clock.System.now()

            UsersTable.update({ UsersTable.id eq userId }) {
                it[deletedAt] = now
                it[updatedAt] = now
            }
        }
    }
}
