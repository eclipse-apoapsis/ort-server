/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration

import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a [PackageCurationProvider], the resolved [PackageCuration]s, and the [rank] of the provider
 * within the [ResolvedConfiguration].
 */
object ResolvedPackageCurationProvidersTable : LongIdTable("resolved_package_curation_providers") {
    val resolvedConfigurationId = reference("resolved_configuration_id", ResolvedConfigurationsTable)
    val packageCurationProviderConfigId =
        reference("package_curation_provider_config_id", PackageCurationProviderConfigsTable)

    val rank = integer("rank")
}

class ResolvedPackageCurationProviderDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ResolvedPackageCurationProviderDao>(ResolvedPackageCurationProvidersTable)

    var resolvedConfiguration by ResolvedConfigurationDao referencedOn
            ResolvedPackageCurationProvidersTable.resolvedConfigurationId
    var packageCurationProviderConfig by PackageCurationProviderConfigDao referencedOn
            ResolvedPackageCurationProvidersTable.packageCurationProviderConfigId

    var rank by ResolvedPackageCurationProvidersTable.rank

    val packageCurations by ResolvedPackageCurationDao referrersOn
            ResolvedPackageCurationsTable.resolvedPackageCurationProviderId

    fun mapToModel() = ResolvedPackageCurations(
        provider = packageCurationProviderConfig.mapToModel(),
        // TODO: For simplicity the curations are currently sorted in memory, but ideally this should be done by the
        //       database already. Also see: https://github.com/JetBrains/Exposed/issues/1362
        curations = packageCurations.sortedBy { it.rank }.map { it.packageCuration.mapToModel() }
    )
}
