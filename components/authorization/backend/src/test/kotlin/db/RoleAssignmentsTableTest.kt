/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.authorization.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable.userId
import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class RoleAssignmentsTableTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    init {
        "Inserting an entity without a role reference should fail" {
            shouldThrow<ExposedSQLException> {
                dbExtension.db.blockingQuery {
                    RoleAssignmentsTable.insert {
                        it[organizationId] = dbExtension.fixtures.organization.id
                        it[userId] = "user-id"
                    }
                }
            }
        }

        "Inserting an entity with multiple role references should fail" {
            shouldThrow<ExposedSQLException> {
                dbExtension.db.blockingQuery {
                    RoleAssignmentsTable.insert {
                        it[organizationId] = dbExtension.fixtures.organization.id
                        it[userId] = "user-id"
                        it[organizationRole] = "READER"
                        it[productRole] = "WRITER"
                    }
                }
            }
        }

        "Inserting multiple entities for the same user and element should fail" {
            shouldThrow<ExposedSQLException> {
                dbExtension.db.blockingQuery {
                    RoleAssignmentsTable.insert {
                        it[organizationId] = dbExtension.fixtures.organization.id
                        it[productId] = dbExtension.fixtures.product.id
                        it[repositoryId] = dbExtension.fixtures.repository.id
                        it[userId] = "user-id"
                        it[organizationRole] = "READER"
                    }

                    RoleAssignmentsTable.insert {
                        it[organizationId] = dbExtension.fixtures.organization.id
                        it[productId] = dbExtension.fixtures.product.id
                        it[repositoryId] = dbExtension.fixtures.repository.id
                        it[userId] = "user-id"
                        it[organizationRole] = "WRITER"
                    }
                }
            }
        }

        "A valid entity can be inserted and retrieved" {
            val assignmentId = dbExtension.db.blockingQuery {
                RoleAssignmentsTable.insert {
                    it[organizationId] = dbExtension.fixtures.organization.id
                    it[userId] = "user-id"
                    it[organizationRole] = "WRITER"
                } get RoleAssignmentsTable.id
            }

            dbExtension.db.blockingQuery {
                val row = RoleAssignmentsTable.selectAll().single()

                row[RoleAssignmentsTable.id] shouldBe assignmentId
                row[RoleAssignmentsTable.organizationId]?.value shouldBe dbExtension.fixtures.organization.id
                row[RoleAssignmentsTable.userId] shouldBe "user-id"
                row[RoleAssignmentsTable.organizationRole] shouldBe "WRITER"
                row[RoleAssignmentsTable.productId] shouldBe null
                row[RoleAssignmentsTable.repositoryId] shouldBe null
                row[RoleAssignmentsTable.productRole] shouldBe null
                row[RoleAssignmentsTable.repositoryRole] shouldBe null
            }
        }
    }
}
