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

import io.ktor.events.EventDefinition
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted

import javax.sql.DataSource

import org.jetbrains.exposed.sql.Database

import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.KOIN_ATTRIBUTE_KEY

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.dao.DatabaseConfig
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.createDataSource
import org.ossreviewtoolkit.server.dao.migrate

val DatabaseReady: EventDefinition<Database> = EventDefinition()

/**
 * Connect and migrate the database. This is the only place where migrations for the production database are done. While
 * other services can connect to the database, they must not handle migrations.
 */
fun Application.configureDatabase() {
    val dataSource = createDataSource()
    val db = dataSource.connect()
    dataSource.migrate()

    environment.monitor.subscribe(ApplicationStarted) {
        attributes[KOIN_ATTRIBUTE_KEY].koin.declare(db)
        environment.monitor.raise(DatabaseReady, db)
    }
}

private fun Application.createDataSource(): DataSource {
    val configManager: ConfigManager by inject()

    val dataSourceConfig = DatabaseConfig.create(configManager)

    return createDataSource(dataSourceConfig)
}
