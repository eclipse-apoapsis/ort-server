/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.services

import java.sql.Connection

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.orchestrator.CreateOrtRun
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageHeader
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

import org.jetbrains.exposed.sql.Database

import org.slf4j.MDC

/**
 * A service responsible for the communication with the Orchestrator.
 */
class OrchestratorService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository,
    configManager: ConfigManager
) {
    private val orchestratorSender by lazy { MessageSenderFactory.createSender(OrchestratorEndpoint, configManager) }

    /**
     * Create an ORT run in the database and notify the Orchestrator to handle this run.
     */
    suspend fun createOrtRun(
        repositoryId: Long,
        revision: String,
        path: String?,
        jobConfig: JobConfigurations,
        jobConfigContext: String?,
        labels: Map<String, String>?,
        environmentConfigPath: String?
    ): OrtRun {
        val traceId = MDC.get("traceId")

        val ortRun = db.dbQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
            maxAttempts = 25
            ortRunRepository.create(
                repositoryId,
                revision,
                path,
                jobConfig,
                jobConfigContext,
                labels.orEmpty(),
                traceId = traceId,
                environmentConfigPath = environmentConfigPath
            )
        }

        orchestratorSender.send(
            Message(
                header = MessageHeader(
                    traceId = traceId,
                    ortRunId = ortRun.id
                ),
                payload = CreateOrtRun(ortRun)
            )
        )

        return ortRun
    }
}
