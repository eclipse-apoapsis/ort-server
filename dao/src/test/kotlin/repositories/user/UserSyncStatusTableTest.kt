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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision

import org.jetbrains.exposed.v1.jdbc.selectAll

class UserSyncStatusTableTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "lastSuccess" should {
        "return null if no synchronization has succeeded yet" {
            dbExtension.db.transaction { UserSyncStatusTable.lastSuccess() } should beNull()
        }
    }

    "recordSuccess" should {
        "keep a single row with the time of the latest success" {
            val firstSuccess = Clock.System.now() - 1.hours
            val secondSuccess = Instant.parse("2026-11-10T08:00:00.123456789Z")

            dbExtension.db.transaction { UserSyncStatusTable.recordSuccess(firstSuccess) }
            dbExtension.db.transaction { UserSyncStatusTable.recordSuccess(secondSuccess) }

            dbExtension.db.transaction {
                UserSyncStatusTable.selectAll().count() shouldBe 1
                UserSyncStatusTable.lastSuccess() shouldBe secondSuccess.toDatabasePrecision()
            }
        }
    }
})
