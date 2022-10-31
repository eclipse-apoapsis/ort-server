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

package org.ossreviewtoolkit.server.orchestrator

import com.typesafe.config.ConfigFactory

import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.orchestrator.AnalyzeRequest
import org.ossreviewtoolkit.server.transport.AnalyzerEndpoint
import org.ossreviewtoolkit.server.transport.Message
import org.ossreviewtoolkit.server.transport.MessageHeader
import org.ossreviewtoolkit.server.transport.MessageSenderFactory

/**
 * This service is responsible for scheduling jobs to be picked up by workers.
 */
class SchedulerService {
    // TODO: Use Orchestrator specific configuration without manually loading it.
    private val analyzerSender by lazy { MessageSenderFactory.createSender(AnalyzerEndpoint, ConfigFactory.load()) }

    fun scheduleAnalyzerJob(
        messageHeader: MessageHeader,
        repository: Repository,
        ortRun: OrtRun,
        analyzerJob: AnalyzerJob
    ) {
        analyzerSender.send(
            Message(
                header = messageHeader,
                payload = AnalyzeRequest(repository, ortRun, analyzerJob)
            )
        )
    }
}
