/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.services

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.ktor.server.config.ApplicationConfig

import java.util.UUID

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.orchestrator.CreateOrtRun
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSenderFactory
import org.ossreviewtoolkit.server.transport.OrchestratorEndpoint

/**
 * A service responsible for the communication with the Orchestrator.
 */
class OrchestratorService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository,
    applicationConfig: ApplicationConfig
) {
    private val config: Config = ConfigFactory.parseMap(applicationConfig.toMap())

    private val orchestratorSender by lazy { MessageSenderFactory.createSender(OrchestratorEndpoint, config) }

    /**
     * Create an ORT run in the database and notify the Orchestrator to handle this run.
     */
    suspend fun createOrtRun(
        repositoryId: Long,
        revision: String,
        jobConfig: JobConfigurations,
        labels: Map<String, String>
    ): OrtRun {
        val ortRun = db.dbQuery { ortRunRepository.create(repositoryId, revision, jobConfig, labels) }

        // TODO: Set the correct token.
        orchestratorSender.send(
            Message(
                header = MessageHeader(
                    token = "",
                    traceId = UUID.randomUUID().toString()
                ),
                payload = CreateOrtRun(ortRun)
            )
        )

        return ortRun
    }
}
