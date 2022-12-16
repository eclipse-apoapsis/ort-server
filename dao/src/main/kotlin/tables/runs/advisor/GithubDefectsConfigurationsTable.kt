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

package org.ossreviewtoolkit.server.dao.tables.runs.advisor

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.runs.advisor.GithubDefectsConfiguration

/**
 * A table to represent a configuration for the GitHub Defects advisor.
 */
object GithubDefectsConfigurationsTable : LongIdTable("github_defects_configurations") {
    val endpointUrl = text("endpoint_url").nullable()
    val labelFilter = text("label_filter").nullable()
    val maxNumberOfIssuesPerRepository = integer("max_number_of_issues_per_repository").nullable()
    val parallelRequests = integer("parallel_requests").nullable()
}

class GithubDefectsConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<GithubDefectsConfigurationDao>(GithubDefectsConfigurationsTable)

    var endpointUrl by GithubDefectsConfigurationsTable.endpointUrl
    var labelFilter by GithubDefectsConfigurationsTable.labelFilter
        .transform({ it?.joinToString(",") }, { it?.split(",") })
    var maxNumberOfIssuesPerRepository by GithubDefectsConfigurationsTable.maxNumberOfIssuesPerRepository
    var parallelRequests by GithubDefectsConfigurationsTable.parallelRequests

    fun mapToModel() = GithubDefectsConfiguration(
        endpointUrl = endpointUrl,
        labelFilter = labelFilter.orEmpty(),
        maxNumberOfIssuesPerRepository = maxNumberOfIssuesPerRepository,
        parallelRequests = parallelRequests
    )
}
