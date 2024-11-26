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

import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.repository.VcsInfoCurationData

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent a VCS info curation data, used within a [PackageCurationData][PackageCurationDataTable].
 */
object VcsInfoCurationDataTable : LongIdTable("vcs_info_curation_data") {
    val type = text("type").nullable()
    val url = text("url").nullable()
    val revision = text("revision").nullable()
    val path = text("path").nullable()
}

class VcsInfoCurationDataDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<VcsInfoCurationDataDao>(VcsInfoCurationDataTable) {
        fun findByVcsInfoCurationData(vcsCurationData: VcsInfoCurationData): VcsInfoCurationDataDao? =
            find {
                with(VcsInfoCurationDataTable) {
                    type eq vcsCurationData.type?.name and
                            (url eq vcsCurationData.url) and
                            (revision eq vcsCurationData.revision) and
                            (path eq vcsCurationData.path)
                }
            }.firstOrNull()

        fun getOrPut(vcsCurationData: VcsInfoCurationData): VcsInfoCurationDataDao =
            findByVcsInfoCurationData(vcsCurationData) ?: new {
                type = vcsCurationData.type?.name
                url = vcsCurationData.url
                revision = vcsCurationData.revision
                path = vcsCurationData.path
            }
    }

    var type by VcsInfoCurationDataTable.type
    var url by VcsInfoCurationDataTable.url
    var revision by VcsInfoCurationDataTable.revision
    var path by VcsInfoCurationDataTable.path

    fun mapToModel() = VcsInfoCurationData(type?.let(RepositoryType::forName), url, revision, path)
}
