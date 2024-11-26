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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.runs.notifier.NotifierRun

class DaoNotifierRunRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var notifierRunRepository: DaoNotifierRunRepository
    lateinit var notifierJob: NotifierJob

    val time = Clock.System.now().toDatabasePrecision()

    beforeEach {
        notifierRunRepository = dbExtension.fixtures.notifierRunRepository
        notifierJob = dbExtension.fixtures.notifierJob
    }

    "create should create an entry in the database" {
        val notifierRun = notifierRunRepository.create(notifierJob.id, time, time)

        val dbEntry = notifierRunRepository.get(notifierRun.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe NotifierRun(
            id = notifierRun.id,
            notifierJobId = notifierJob.id,
            startTime = time,
            endTime = time
        )
    }

    "getByJobId should return the notifier run for a job" {
        val notifierRun = notifierRunRepository.create(notifierJob.id, time, time)

        notifierRunRepository.getByJobId(notifierJob.id) shouldBe notifierRun
    }

    "get should the notifier run" {
        val notifierRun = notifierRunRepository.create(notifierJob.id, time, time)

        notifierRunRepository.get(notifierRun.id) shouldBe notifierRun
    }
})
