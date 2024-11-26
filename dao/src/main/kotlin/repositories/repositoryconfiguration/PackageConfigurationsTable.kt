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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import org.eclipse.apoapsis.ortserver.dao.mapAndDeduplicate
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifiersTable
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to store a package configuration, which is part of a
 * [RepositoryConfiguration][RepositoryConfigurationsTable].
 */
object PackageConfigurationsTable : LongIdTable("package_configurations") {
    val identifierId = reference("identifier_id", IdentifiersTable)
    val vcsMatcherId = reference("vcs_matcher_id", VcsMatchersTable).nullable()

    val sourceArtifactUrl = text("source_artifact_url").nullable()
}

class PackageConfigurationDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PackageConfigurationDao>(PackageConfigurationsTable) {
        fun findByPackageConfiguration(packageConfiguration: PackageConfiguration): PackageConfigurationDao? =
            find {
                PackageConfigurationsTable.sourceArtifactUrl eq packageConfiguration.sourceArtifactUrl
            }.firstOrNull {
                it.identifier.mapToModel() == packageConfiguration.id &&
                        it.vcsMatcher?.mapToModel() == packageConfiguration.vcs &&
                        it.pathExcludes.map(PathExcludeDao::mapToModel) == packageConfiguration.pathExcludes &&
                        it.licenseFindingCurations
                            .map(LicenseFindingCurationDao::mapToModel) == packageConfiguration.licenseFindingCurations
            }

        fun getOrPut(packageConfiguration: PackageConfiguration): PackageConfigurationDao =
            findByPackageConfiguration(packageConfiguration) ?: new {
                sourceArtifactUrl = packageConfiguration.sourceArtifactUrl
                identifier = IdentifierDao.getOrPut(packageConfiguration.id)
                vcsMatcher = packageConfiguration.vcs?.let { VcsMatcherDao.getOrPut(it) }
                pathExcludes = mapAndDeduplicate(packageConfiguration.pathExcludes, PathExcludeDao::getOrPut)
                licenseFindingCurations = mapAndDeduplicate(
                    packageConfiguration.licenseFindingCurations,
                    LicenseFindingCurationDao::getOrPut
                )
            }
    }

    var identifier by IdentifierDao referencedOn PackageConfigurationsTable.identifierId
    var vcsMatcher by VcsMatcherDao optionalReferencedOn PackageConfigurationsTable.vcsMatcherId

    var sourceArtifactUrl by PackageConfigurationsTable.sourceArtifactUrl

    var pathExcludes by PathExcludeDao via PackageConfigurationsPathExcludesTable
    var licenseFindingCurations by LicenseFindingCurationDao via PackageConfigurationsLicenseFindingCurationsTable

    fun mapToModel() = PackageConfiguration(
        id = identifier.mapToModel(),
        sourceArtifactUrl = sourceArtifactUrl,
        vcs = vcsMatcher?.mapToModel(),
        pathExcludes = pathExcludes.map(PathExcludeDao::mapToModel),
        licenseFindingCurations = licenseFindingCurations.map(LicenseFindingCurationDao::mapToModel)
    )
}
