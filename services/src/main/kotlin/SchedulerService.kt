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

package org.ossreviewtoolkit.server.services

import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Repository

/**
 * This service is responsible for scheduling jobs to be picked up by workers. Different implementation can use
 * different means to schedule jobs, for example sending a message via a message service.
 */
interface SchedulerService {
    suspend fun scheduleAnalyzerJob(repository: Repository, ortRun: OrtRun, analyzerJob: AnalyzerJob): Result<Unit>
}

/**
 * This implementation of [SchedulerService] does nothing. It can be used in a setup where workers actively pull jobs
 * via the server API.
 */
class NoOpSchedulerService : SchedulerService {
    override suspend fun scheduleAnalyzerJob(repository: Repository, ortRun: OrtRun, analyzerJob: AnalyzerJob) =
        Result.success(Unit)
}
