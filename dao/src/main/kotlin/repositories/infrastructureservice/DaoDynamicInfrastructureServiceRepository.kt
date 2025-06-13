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
import org.eclipse.apoapsis.ortserver.model.DynamicInfrastructureService
import org.eclipse.apoapsis.ortserver.model.repositories.DynamicInfrastructureServiceRepository

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class DaoDynamicInfrastructureServiceRepository(private val db: Database) : DynamicInfrastructureServiceRepository {
    override fun getOrCreateForRun(service: DynamicInfrastructureService, runId: Long): DynamicInfrastructureService {
        service.validate()
        return db.blockingQuery {
            val serviceDao = DynamicInfrastructureServicesDao.Companion.getOrPut(service)
            DynamicInfrastructureServicesRunsTable.insert {
                it[dynamicInfrastructureServiceId] = serviceDao.id
                it[ortRunId] = runId
            }

            serviceDao.mapToModel()
        }
    }

    override fun listForRun(runId: Long): List<DynamicInfrastructureService> =
        db.blockingQuery {
            val subQuery = DynamicInfrastructureServicesRunsTable
                .select(DynamicInfrastructureServicesRunsTable.dynamicInfrastructureServiceId)
                .where { DynamicInfrastructureServicesRunsTable.ortRunId eq runId }

            DynamicInfrastructureServicesTable
                .selectAll()
                .where {
                    DynamicInfrastructureServicesTable.id inSubQuery subQuery
                }
                .map { row ->
                    DynamicInfrastructureService(
                        name = row[DynamicInfrastructureServicesTable.name],
                        url = row[DynamicInfrastructureServicesTable.url],
                        usernameSecretName = row[DynamicInfrastructureServicesTable.usernameSecretName],
                        passwordSecretName = row[DynamicInfrastructureServicesTable.passwordSecretName],
                        credentialsTypes = DynamicInfrastructureServicesDao.fromCredentialsTypeString(
                            row[DynamicInfrastructureServicesTable.credentialsType]
                        )
                    )
                }
        }
}
