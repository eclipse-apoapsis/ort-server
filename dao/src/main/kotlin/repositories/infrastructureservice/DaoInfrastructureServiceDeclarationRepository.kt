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

package org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceDeclarationRepository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class DaoInfrastructureServiceDeclarationRepository(private val db: Database) :
    InfrastructureServiceDeclarationRepository {
    override fun getOrCreateForRun(
        service: InfrastructureServiceDeclaration,
        runId: Long
    ): InfrastructureServiceDeclaration {
        service.validate()
        return db.blockingQuery {
            val serviceDao = InfrastructureServiceDeclarationDao.Companion.getOrPut(service)
            InfrastructureServiceDeclarationsRunsTable.insert {
                it[infrastructureServiceDeclarationId] = serviceDao.id
                it[ortRunId] = runId
            }

            serviceDao.mapToModel()
        }
    }

    override fun listForRun(runId: Long): List<InfrastructureServiceDeclaration> =
        db.blockingQuery {
            val subQuery = InfrastructureServiceDeclarationsRunsTable
                .select(InfrastructureServiceDeclarationsRunsTable.infrastructureServiceDeclarationId)
                .where { InfrastructureServiceDeclarationsRunsTable.ortRunId eq runId }

            InfrastructureServiceDeclarationsTable
                .selectAll()
                .where {
                    InfrastructureServiceDeclarationsTable.id inSubQuery subQuery
                }
                .map { row ->
                    InfrastructureServiceDeclaration(
                        name = row[InfrastructureServiceDeclarationsTable.name],
                        url = row[InfrastructureServiceDeclarationsTable.url],
                        description = row[InfrastructureServiceDeclarationsTable.description],
                        usernameSecret = row[InfrastructureServiceDeclarationsTable.usernameSecret],
                        passwordSecret = row[InfrastructureServiceDeclarationsTable.passwordSecret],
                        credentialsTypes = InfrastructureServiceDeclarationDao.fromCredentialsTypeString(
                            row[InfrastructureServiceDeclarationsTable.credentialsType]
                        )
                    )
                }
        }
}
