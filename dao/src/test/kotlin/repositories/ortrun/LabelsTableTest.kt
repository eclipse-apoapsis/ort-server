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

package org.eclipse.apoapsis.ortserver.dao.repositories.ortrun

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll

class LabelsTableTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    "getOrPut" should {
        "return the same label if it is created by concurrent transactions" {
            val firstInserted = CompletableDeferred<Unit>()
            val secondWaiting = CompletableDeferred<Unit>()

            coroutineScope {
                val first = async {
                    dbExtension.db.transaction {
                        maxAttempts = 1
                        LabelDao.getOrPut("key", "value").id.value.also {
                            firstInserted.complete(Unit)

                            // Keep the transaction open until the second transaction waits for it.
                            secondWaiting.await()
                        }
                    }
                }

                firstInserted.await()

                val second = async {
                    dbExtension.db.transaction {
                        // Do not retry, as a retry would hide a unique constraint violation.
                        maxAttempts = 1
                        LabelDao.getOrPut("key", "value").id.value
                    }
                }

                try {
                    // Wait until the insert of the second transaction is blocked by the uncommitted label of the
                    // first transaction. At this point, the second transaction has already checked that the label
                    // does not exist.
                    eventually(10.seconds) {
                        dbExtension.db.transaction { countTransactionsWaitingToInsertLabels() } shouldBe 1
                    }
                } finally {
                    secondWaiting.complete(Unit)
                }

                second.await() shouldBe first.await()
            }

            dbExtension.db.transaction { LabelsTable.selectAll().count() } shouldBe 1
        }
    }
})

/** Return the number of database sessions that wait for a lock while inserting into the labels table. */
private fun JdbcTransaction.countTransactionsWaitingToInsertLabels(): Int? =
    exec(
        "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE 'INSERT INTO labels%'"
    ) { resultSet ->
        resultSet.takeIf { it.next() }?.getInt(1)
    }
