/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.advisor

import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorRun

class AdvisorWorkerDao(
    private val advisorJobRepository: AdvisorJobRepository,
    private val advisorRunRepository: AdvisorRunRepository
) {
    fun getAdvisorJob(advisorJobId: Long) = advisorJobRepository.get(advisorJobId)

    fun storeAdvisorRun(advisorRun: AdvisorRun) {
        advisorRunRepository.create(
            advisorJobId = advisorRun.advisorJobId,
            startTime = advisorRun.startTime,
            endTime = advisorRun.endTime,
            environment = advisorRun.environment,
            config = advisorRun.config,
            advisorRecords = advisorRun.advisorRecords
        )
    }
}
