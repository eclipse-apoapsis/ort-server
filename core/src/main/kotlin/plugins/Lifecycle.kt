/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted

import kotlin.concurrent.thread

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.migration.RolesToDbMigration
import org.eclipse.apoapsis.ortserver.utils.logging.StandardMdcKeys
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.koin.ktor.ext.inject

import org.slf4j.MDC

/**
 * Configure actions that are triggered by
 * [lifecycle events][https://ktor.io/docs/events.html#handle-events-application].
 */
fun Application.configureLifecycle() {
    monitor.subscribe(ApplicationStarted) {
        val rolesMigration by inject<RolesToDbMigration>()

        val mdcContext = MDC.getCopyOfContextMap()

        thread {
            MDC.setContextMap(mdcContext)
            runBlocking(Dispatchers.IO) {
                migrateRoles(rolesMigration)
            }
        }
    }
}

/**
 * Perform a migration to new database-based structures for access rights if necessary. This makes sure that the
 * new structures are populated once when switching from access rights stored in Keycloak to the new storage in the
 * database. The migration then runs in the background.
 */
private suspend fun migrateRoles(migration: RolesToDbMigration) {
    withMdcContext(StandardMdcKeys.COMPONENT to "core") {
        launch {
            migration.migrateRolesToDb()
        }
    }
}
