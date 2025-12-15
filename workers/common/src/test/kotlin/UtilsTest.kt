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

package org.eclipse.apoapsis.ortserver.workers.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.string.shouldContainIgnoringCase

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.JobStatus

import org.jetbrains.exposed.sql.Database

class UtilsTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
    }

    "validateForProcessing" should {
        "throw an exception for a job that could not be resolved" {
            val jobId = 42L
            val job: AnalyzerJob? = null

            val exception = shouldThrow<IllegalArgumentException> {
                job.validateForProcessing(jobId)
            }

            exception.message shouldContainIgnoringCase jobId.toString()
        }

        "succeed for jobs with valid states" {
            val validStates = JobStatus.entries.filterNot { it.final }

            validStates.forAll { status ->
                val job = fixtures.analyzerJob.copy(status = status)

                job.validateForProcessing(job.id)
            }
        }

        "throw a JobIgnoredException for jobs with invalid states" {
            val invalidStates = JobStatus.entries.filter { it.final }

            invalidStates.forAll { status ->
                val job = fixtures.analyzerJob.copy(status = status)

                val exception = shouldThrow<JobIgnoredException> {
                    job.validateForProcessing(job.id)
                }

                exception.message shouldContainIgnoringCase job.id.toString()
            }
        }
    }
})
