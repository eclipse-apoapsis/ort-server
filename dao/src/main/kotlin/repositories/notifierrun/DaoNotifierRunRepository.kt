/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.notifierrun

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.entityQuery
import org.eclipse.apoapsis.ortserver.dao.tables.NotifierJobDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.notifier.NotifierRunDao
import org.eclipse.apoapsis.ortserver.dao.tables.runs.notifier.NotifierRunsTable
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun

import org.jetbrains.exposed.sql.Database

/**
 * An implementation of [NotifierRunRepository] that stores notifier runs in [NotifierRunsTable].
 */
class DaoNotifierRunRepository(private val db: Database) : NotifierRunRepository {
    override fun create(notifierJobId: Long, startTime: Instant, endTime: Instant): NotifierRun = db.blockingQuery {
        NotifierRunDao.new {
            this.notifierJob = NotifierJobDao[notifierJobId]
            this.startTime = startTime
            this.endTime = endTime
        }.mapToModel()
    }

    override fun get(id: Long): NotifierRun? = db.entityQuery { NotifierRunDao[id].mapToModel() }

    override fun getByJobId(notifierJobId: Long): NotifierRun? = db.entityQuery {
        NotifierRunDao.find { NotifierRunsTable.notifierJobId eq notifierJobId }.firstOrNull()?.mapToModel()
    }
}
