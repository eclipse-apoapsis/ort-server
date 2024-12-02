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

import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [ORT runs][OrtRun].
 */
interface OrtRunRepository {
    /**
     * Create an ORT run.
     */
    fun create(
        repositoryId: Long,
        revision: String,
        path: String?,
        jobConfigs: JobConfigurations,
        jobConfigContext: String? = null,
        labels: Map<String, String>,
        traceId: String?,
        environmentConfigPath: String?,
    ): OrtRun

    /**
     * Get an ORT run by [id]. Returns null if the ORT run is not found.
     */
    fun get(id: Long): OrtRun?

    /**
     * Get an ORT run by its [index][ortRunIndex] within a [repository][repositoryId].
     */
    fun getByIndex(repositoryId: Long, ortRunIndex: Long): OrtRun?

    /**
     * Get the id of an ORT run by its [index][ortRunIndex] within a [repository][repositoryId].
     * This function is more efficient than [getByIndex], as it only retrieves the ID of the ORT run or
     * returns null if the ORT run is not found.
     */
    fun getIdByIndex(repositoryId: Long, ortRunIndex: Long): Long?

    /**
     * List all ORT runs according to the given [parameters] and [filters].
     */
    fun list(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: OrtRunFilters? = null
    ): ListQueryResult<OrtRun>

    /**
     * List all ORT runs for a [repository][repositoryId] according to the given [parameters].
     */
    fun listForRepository(
        repositoryId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<OrtRun>

    /**
     * List all ORT runs for a [repository][repositoryId] according to the given [parameters],
     * but only return a summary of each run.
     */
    fun listSummariesForRepository(
        repositoryId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<OrtRunSummary>

    /**
     * Update an ORT run by [id] with the [present][OptionalValue.Present] values. If [issues] or [labels] are
     * provided, they are added to the already existing ones.
     */
    fun update(
        id: Long,
        status: OptionalValue<OrtRunStatus> = OptionalValue.Absent,
        jobConfigs: OptionalValue<JobConfigurations> = OptionalValue.Absent,
        resolvedJobConfigs: OptionalValue<JobConfigurations> = OptionalValue.Absent,
        resolvedJobConfigContext: OptionalValue<String?> = OptionalValue.Absent,
        issues: OptionalValue<Collection<Issue>> = OptionalValue.Absent,
        labels: OptionalValue<Map<String, String>> = OptionalValue.Absent
    ): OrtRun

    /**
     * Delete an ORT run by [id].
     */
    fun delete(id: Long): Int

    /**
     * Delete the ORT runs of a repository, specified by the [repositoryId].
     */
    fun deleteByRepository(repositoryId: Long): Int
}
