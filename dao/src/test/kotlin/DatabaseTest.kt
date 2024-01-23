/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify

import javax.sql.DataSource

import org.jetbrains.exposed.sql.Database

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactoryForTesting

class DatabaseTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "databaseModule" should {
        "return a module that connects to the database" {
            val dbConfig = DatabaseConfig(
                jdbcUrl = "jdbc:test:db",
                name = "myTestDataSource",
                schema = "myTestSchema",
                username = "scott",
                password = "tiger",
                maximumPoolSize = 12,
                sslMode = "myTestSSLMode",
                sslCert = "myTestSSLCert",
                sslKey = "myTestSSLKey",
                sslRootCert = "myTestSSLRootCert"
            )

            val secretsMap = mapOf(
                "database.username" to dbConfig.username,
                "database.password" to dbConfig.password
            )
            val secretConfigMap = mapOf(
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
                ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
            )
            val config = ConfigFactory.parseMap(
                mapOf(
                    "database.url" to dbConfig.jdbcUrl,
                    "database.name" to dbConfig.name,
                    "database.schema" to dbConfig.schema,
                    "database.poolsize" to dbConfig.maximumPoolSize,
                    "database.sslmode" to dbConfig.sslMode,
                    "database.sslcert" to dbConfig.sslCert,
                    "database.sslkey" to dbConfig.sslKey,
                    "database.sslrootcert" to dbConfig.sslRootCert,
                    ConfigManager.CONFIG_MANAGER_SECTION to secretConfigMap
                )
            )

            val dataSource = mockk<DataSource>()
            val database = mockk<Database>()
            mockkStatic(::createDataSource)
            mockkStatic(DataSource::connect)
            every { createDataSource(dbConfig) } returns dataSource
            every { dataSource.connect() } returns database

            val baseModule = module {
                single { config }
                single { ConfigManager.create(get()) }
            }

            startKoin {
                modules(baseModule, databaseModule())
            }

            try {
                verify {
                    createDataSource(dbConfig)
                }
            } finally {
                stopKoin()
            }
        }
    }
})
