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

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

import org.jetbrains.exposed.sql.Database

private const val DEFAULT_SCHEMA = "ort_server"

fun DataSource.connect() {
    Database.connect(this)
    migrate(this)
}

fun migrate(dataSource: DataSource) {
    Flyway(getFlywayConfig(dataSource, DEFAULT_SCHEMA)).migrate()
}

fun clean(dataSource: DataSource) {
    Flyway(getFlywayConfig(dataSource, DEFAULT_SCHEMA)).clean()
}

private fun getFlywayConfig(dataSource: DataSource, schema: String) = FluentConfiguration()
    .dataSource(dataSource)
    .schemas(schema)
    .cleanDisabled(false)
    .createSchemas(true)
    .baselineOnMigrate(true)

fun createDataSource(config: DatabaseConfig): DataSource {
    val dataSourceConfig = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        schema = config.schema
        username = config.username
        password = config.password
        maximumPoolSize = config.maximumPoolSize
        driverClassName = "org.postgresql.Driver"

        addDataSourceProperty("ApplicationName", "ort_server")
        addDataSourceProperty("sslmode", config.sslMode)
        config.sslCert?.let { addDataSourceProperty("sslcert", it) }
        config.sslKey?.let { addDataSourceProperty("sslkey", it) }
        config.sslRootCert?.let { addDataSourceProperty("sslrootcert", it) }
    }

    dataSourceConfig.validate()

    return HikariDataSource(dataSourceConfig)
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val schema: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
    val driverClassName: String,
    val sslMode: String,
    val sslCert: String?,
    val sslKey: String?,
    val sslRootCert: String?,
)
