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

package org.eclipse.apoapsis.ortserver.dao

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting

class DataSourceConfigTest : WordSpec({
    "create" should {
        "create a DataSourceConfig from the ConfigManager" {
            val dataSourceConfig = DataSourceConfig(
                host = "host",
                port = 5432,
                name = "myTestDataSource",
                schema = "myTestSchema",
                username = "scott",
                password = "tiger",
                connectionTimeout = 30000L,
                idleTimeout = 600000L,
                keepaliveTime = 0L,
                maxLifetime = 1800000L,
                maximumPoolSize = 12,
                minimumIdle = 5,
                sslMode = "myTestSSLMode",
                sslCert = "myTestSSLCert",
                sslKey = "myTestSSLKey",
                sslRootCert = "myTestSSLRootCert",
                initSqlStatement = "SET search_path TO test,public;"
            )

            val secretsMap = mapOf(
                "database.username" to dataSourceConfig.username,
                "database.password" to dataSourceConfig.password
            )

            val secretProviderConfig = mapOf(
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
                ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
            )

            val config = ConfigFactory.parseMap(
                mapOf(
                    "database.host" to dataSourceConfig.host,
                    "database.port" to dataSourceConfig.port,
                    "database.name" to dataSourceConfig.name,
                    "database.schema" to dataSourceConfig.schema,
                    "database.connectionTimeout" to dataSourceConfig.connectionTimeout,
                    "database.idleTimeout" to dataSourceConfig.idleTimeout,
                    "database.keepaliveTime" to dataSourceConfig.keepaliveTime,
                    "database.maxLifetime" to dataSourceConfig.maxLifetime,
                    "database.maximumPoolSize" to dataSourceConfig.maximumPoolSize,
                    "database.minimumIdle" to dataSourceConfig.minimumIdle,
                    "database.sslMode" to dataSourceConfig.sslMode,
                    "database.sslCert" to dataSourceConfig.sslCert,
                    "database.sslKey" to dataSourceConfig.sslKey,
                    "database.sslRootCert" to dataSourceConfig.sslRootCert,
                    "database.initSqlStatement" to dataSourceConfig.initSqlStatement,
                    ConfigManager.CONFIG_MANAGER_SECTION to secretProviderConfig
                )
            )

            val configManager = ConfigManager.create(config)

            DataSourceConfig.create(configManager) shouldBe dataSourceConfig
        }
    }
})
