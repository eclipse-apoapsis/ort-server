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

import org.ossreviewtoolkit.server.model.runs.advisor.AdvisorConfiguration

/**
 * A table to represent an advisor configuration.
 */
object AdvisorConfigurationsTable : LongIdTable("advisor_configurations") {
    val advisorRunId = reference("advisor_run_id", AdvisorRunsTable.id)
    val githubDefectsConfigurationId =
        reference("github_defects_configuration_id", GithubDefectsConfigurationsTable.id).nullable()
    val nexusIqConfigurationId = reference("nexus_iq_configuration_id", NexusIqConfigurationsTable.id).nullable()
    val osvConfigurationId = reference("osv_configuration_id", OsvConfigurationsTable.id).nullable()
    val vulnerableCodeConfigurationId =
        reference("vulnerable_code_configuration_id", VulnerableCodeConfigurationsTable.id).nullable()
}

class AdvisorConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorConfigurationDao>(AdvisorConfigurationsTable)

    var advisorRun by AdvisorRunDao referencedOn AdvisorConfigurationsTable.advisorRunId
    var githubDefectsConfiguration by GithubDefectsConfigurationDao optionalReferencedOn
            AdvisorConfigurationsTable.githubDefectsConfigurationId
    var nexusIqConfiguration by NexusIqConfigurationDao optionalReferencedOn
            AdvisorConfigurationsTable.nexusIqConfigurationId
    var osvConfiguration by OsvConfigurationDao optionalReferencedOn AdvisorConfigurationsTable.osvConfigurationId
    var vulnerableCodeConfiguration by VulnerableCodeConfigurationDao optionalReferencedOn
            AdvisorConfigurationsTable.vulnerableCodeConfigurationId

    val options by AdvisorConfigurationsOptionDao referrersOn AdvisorConfigurationsOptionsTable.advisorConfigurationId

    fun mapToModel() = AdvisorConfiguration(
        githubDefectsConfiguration = githubDefectsConfiguration?.mapToModel(),
        nexusIqConfiguration = nexusIqConfiguration?.mapToModel(),
        osvConfiguration = osvConfiguration?.mapToModel(),
        vulnerableCodeConfiguration = vulnerableCodeConfiguration?.mapToModel(),
        options = options.associate { it.key to it.value }
    )
}
