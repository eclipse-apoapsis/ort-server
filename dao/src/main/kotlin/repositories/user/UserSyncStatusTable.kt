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

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/** A table that holds the time when the users were last synchronized with Keycloak successfully, in at most one row. */
object UserSyncStatusTable : Table("user_sync_status") {
    /** The ID of the only row. */
    private const val ROW_ID = 1

    val id = integer("id")

    /** The time when the last successful synchronization finished. */
    val lastSucceededAt = timestamp("last_succeeded_at")

    override val primaryKey = PrimaryKey(id)

    /**
     * Record that a synchronization finished successfully at the given [time]. The time is stored with the precision of
     * the database, which [lastSuccess] returns.
     */
    fun recordSuccess(time: Instant) {
        upsert(id) {
            it[id] = ROW_ID
            it[lastSucceededAt] = time.toDatabasePrecision()
        }
    }

    /** Return the time when the last successful synchronization finished, or null if none has succeeded yet. */
    fun lastSuccess(): Instant? = selectAll().singleOrNull()?.get(lastSucceededAt)
}
