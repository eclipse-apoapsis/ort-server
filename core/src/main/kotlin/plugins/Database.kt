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

package org.ossreviewtoolkit.server.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.config.HoconApplicationConfig

import javax.sql.DataSource

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.dao.DatabaseConfig
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.createDataSource

fun Application.configureDatabase(dataSource: DataSource = createDataSource()) {
    dataSource.connect()
}

private fun Application.createDataSource(): DataSource {
    val config: HoconApplicationConfig by inject()

    val dataSourceConfig = DatabaseConfig(
        jdbcUrl = config.property("database.url").getString(),
        name = config.property("database.name").getString(),
        schema = config.property("database.schema").getString(),
        username = config.property("database.username").getString(),
        password = config.property("database.password").getString(),
        maximumPoolSize = config.property("database.poolsize").getString().toInt(),
        driverClassName = "org.postgresql.Driver",
        sslMode = config.property("database.sslmode").getString(),
        sslCert = config.propertyOrNull("database.sslcert")?.getString(),
        sslKey = config.propertyOrNull("database.sslkey")?.getString(),
        sslRootCert = config.propertyOrNull("database.sslrootcert")?.getString()
    )

    return createDataSource(dataSourceConfig)
}
