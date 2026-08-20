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
import io.kotest.core.spec.style.WordSpec

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.TEST_DB_SCHEMA
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DatabaseTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    afterEach {
        stopKoin()
    }

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

            val baseModule = module {
                single { config }
                single { ConfigManager.create(get()) }
            }

            val app = startKoin {
                modules(baseModule, databaseModule())
            }

            val db = app.koin.get<Database>()

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
})
