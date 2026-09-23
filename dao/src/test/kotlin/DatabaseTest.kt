/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.koin.KoinExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.Dispatchers

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.TEST_DB_SCHEMA
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

val testModule = module {
    includes(databaseModule(startEager = false))

    single { ConfigManager.create(get()) }
}

@Suppress("TooGenericExceptionThrown")
class DatabaseTest : KoinTest, WordSpec() {
    init {
        tags(Integration)

        val dbExtension = extension(DatabaseTestExtension())
        extension(KoinExtension(testModule))

        "databaseModule" should {
            "add a working database to the Koin context" {
                val postgres = dbExtension.postgres

                val secretsMap = mapOf(
                    "database.username" to postgres.username,
                    "database.password" to postgres.password
                )

                val secretConfigMap = mapOf(
                    ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
                    ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
                )

                val config = ConfigFactory.parseMap(
                    mapOf(
                        "database.host" to postgres.host,
                        "database.port" to postgres.firstMappedPort,
                        "database.name" to postgres.databaseName,
                        "database.schema" to TEST_DB_SCHEMA,
                        "database.connectionTimeout" to 30000,
                        "database.idleTimeout" to 600000,
                        "database.keepaliveTime" to 0,
                        "database.maxLifetime" to 1800000,
                        "database.maximumPoolSize" to 5,
                        "database.minimumIdle" to 1,
                        "database.sslMode" to "disable",
                        ConfigManager.CONFIG_MANAGER_SECTION to secretConfigMap
                    )
                )

                getKoin().declare(config)

                val db by inject<Database>()

                shouldNotThrowAny {
                    transaction(db) {
                        OrganizationsTable.insert {
                            it[name] = "name"
                            it[description] = "description"
                        }
                    }
                }
            }
        }

        "blockingQuery" should {
            "execute a block of code in a transaction" {
                shouldNotThrowAny {
                    dbExtension.db.blockingQuery {
                        OrganizationsTable.insert {
                            it[name] = "name"
                            it[description] = "description"
                        }
                    }
                }
            }

            "run a nested blockingQuery in the same transaction" {
                dbExtension.db.blockingQuery {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    dbExtension.db.blockingQuery {
                        this shouldBeSameInstanceAs outerTransaction
                        Thread.currentThread() shouldBeSameInstanceAs outerThread
                    }
                }
            }

            "not run a nested dbQuery in the same transaction" {
                dbExtension.db.blockingQuery {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    runBlocking(Dispatchers.IO) {
                        dbExtension.db.dbQuery {
                            this shouldNotBeSameInstanceAs outerTransaction
                            Thread.currentThread() shouldNotBeSameInstanceAs outerThread
                        }
                    }
                }
            }

            "not run a nested suspending transaction in the same transaction" {
                dbExtension.db.blockingQuery {
                    val outerTransaction = this

                    runBlocking(Dispatchers.IO) {
                        dbExtension.db.transaction {
                            this shouldNotBeSameInstanceAs outerTransaction
                        }
                    }
                }
            }
        }

        "dbQuery" should {
            "execute a block of code in a transaction" {
                shouldNotThrowAny {
                    dbExtension.db.dbQuery {
                        OrganizationsTable.insert {
                            it[name] = "name"
                            it[description] = "description"
                        }
                    }
                }
            }

            "not run a nested dbQuery in the same transaction" {
                dbExtension.db.dbQuery {
                    val outerTransaction = this

                    runBlocking {
                        dbExtension.db.dbQuery {
                            this shouldNotBeSameInstanceAs outerTransaction
                        }
                    }
                }
            }

            "run a nested blockingQuery in the same transaction" {
                dbExtension.db.dbQuery {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    dbExtension.db.blockingQuery {
                        this shouldBeSameInstanceAs outerTransaction
                        Thread.currentThread() shouldBeSameInstanceAs outerThread
                    }
                }
            }

            "not run a nested blockingQuery in the same transaction when switching to a different thread" {
                dbExtension.db.dbQuery {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    runBlocking(Dispatchers.Default) {
                        dbExtension.db.blockingQuery {
                            this shouldNotBeSameInstanceAs outerTransaction
                            Thread.currentThread() shouldNotBeSameInstanceAs outerThread
                        }
                    }
                }
            }

            "not run a nested suspending transaction in the same transaction" {
                dbExtension.db.dbQuery {
                    val outerTransaction = this

                    runBlocking {
                        dbExtension.db.transaction {
                            this shouldNotBeSameInstanceAs outerTransaction
                        }
                    }
                }
            }
        }

        "transaction" should {
            "execute a block of code in a transaction" {
                shouldNotThrowAny {
                    dbExtension.db.transaction {
                        OrganizationsTable.insert {
                            it[name] = "name"
                            it[description] = "description"
                        }
                    }
                }
            }

            "run a nested transaction in the same transaction" {
                dbExtension.db.transaction {
                    val outerTransaction = this

                    dbExtension.db.transaction {
                        this shouldBeSameInstanceAs outerTransaction
                    }
                }
            }

            "run a nested dbQuery in the same transaction" {
                dbExtension.db.transaction {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    dbExtension.db.dbQuery {
                        this shouldBeSameInstanceAs outerTransaction
                        Thread.currentThread() shouldNotBeSameInstanceAs outerThread
                    }
                }
            }

            "run a nested blockingQuery in the same transaction" {
                dbExtension.db.transaction {
                    val outerTransaction = this
                    val outerThread = Thread.currentThread()

                    dbExtension.db.blockingQuery {
                        this shouldBeSameInstanceAs outerTransaction
                        Thread.currentThread() shouldBeSameInstanceAs outerThread
                    }
                }
            }
        }

        "transactionCatching" should {
            "execute a block of code in a transaction and return a Result" {
                val result = dbExtension.db.transactionCatching {
                    OrganizationsTable.insert {
                        it[name] = "name"
                        it[description] = "description"
                    }
                }

                result.isSuccess shouldBe true
            }

            "return a failure Result if an exception is thrown" {
                val result = dbExtension.db.transactionCatching {
                    throw RuntimeException("Test exception")
                }

                result.isFailure shouldBe true
            }

            "propagate CancellationException" {
                shouldThrow<CancellationException> {
                    dbExtension.db.transactionCatching {
                        throw CancellationException("Test cancellation")
                    }
                }
            }

            "propagate Error" {
                shouldThrow<Error> {
                    dbExtension.db.transactionCatching {
                        throw Error("Test error")
                    }
                }
            }
        }
    }
}
