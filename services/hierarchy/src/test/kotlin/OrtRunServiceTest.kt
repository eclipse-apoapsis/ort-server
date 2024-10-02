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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database

class OrtRunServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var db: Database
    private lateinit var fixtures: Fixtures

    init {
        beforeEach {
            db = dbExtension.db
            fixtures = dbExtension.fixtures
        }

        "listOrtRuns" should {
            "return all ort runs" {
                val service = OrtRunService(db, dbExtension.fixtures.ortRunRepository)
                createOrtRuns()

                val results = service.listOrtRuns().data

                results shouldHaveSize 4
            }

            "return ort runs filtered by status" {
                val service = OrtRunService(db, dbExtension.fixtures.ortRunRepository)
                createOrtRuns()

                val filters = OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.IN,
                        setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
                    )
                )

                val results = service.listOrtRuns(ListQueryParameters.DEFAULT, filters)

                results.data shouldHaveSize 2
                results.totalCount shouldBe 2

                results.data.first().status shouldBeIn setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
                results.data.last().status shouldBeIn setOf(OrtRunStatus.ACTIVE, OrtRunStatus.CREATED)
            }

            "return an empty list if no ORT runs with requested statuses are found" {
                val service = OrtRunService(db, dbExtension.fixtures.ortRunRepository)
                createOrtRuns()

                val filters = OrtRunFilters(
                    status = FilterOperatorAndValue(
                        ComparisonOperator.IN,
                        setOf(OrtRunStatus.FINISHED_WITH_ISSUES)
                    )
                )

                val results = service.listOrtRuns(ListQueryParameters.DEFAULT, filters)

                results.data shouldHaveSize 0
                results.totalCount shouldBe 0
            }
        }
    }

    private fun createOrtRuns() {
        val organization1Id = fixtures.createOrganization("org1").id
        val organization2Id = fixtures.createOrganization("org2").id
        val product1Id = fixtures.createProduct(organizationId = organization1Id).id
        val product2Id = fixtures.createProduct(organizationId = organization2Id).id
        val repository1Id = fixtures.createRepository(productId = product1Id).id
        val repository2Id = fixtures.createRepository(productId = product2Id).id

        val ortRunId1 = fixtures.createOrtRun(repository1Id).id
        val ortRunId2 = fixtures.createOrtRun(repository1Id).id
        val ortRunId3 = fixtures.createOrtRun(repository2Id).id
        fixtures.createOrtRun(repository2Id).id

        fixtures.ortRunRepository.update(ortRunId1, OrtRunStatus.FINISHED.asPresent())
        fixtures.ortRunRepository.update(ortRunId2, OrtRunStatus.FAILED.asPresent())
        fixtures.ortRunRepository.update(ortRunId3, OrtRunStatus.ACTIVE.asPresent())
    }
}
