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

package org.eclipse.apoapsis.ortserver.model.repositories

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorRun

/**
 * A repository of [advisor runs][AdvisorRun].
 */
interface AdvisorRunRepository {
    /**
     * Create an advisor run.
     */
    fun create(
        advisorJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: AdvisorConfiguration,
        results: Map<Identifier, List<AdvisorResult>>
    ): AdvisorRun

    /**
     * Get an advisor run by [id]. Returns null if the advisor run is not found.
     */
    fun get(id: Long): AdvisorRun?

    /**
     * Get an advisor run by [advisorJobId]. Returns null if the advisor run is not found.
     */
    fun getByJobId(advisorJobId: Long): AdvisorRun?
}
