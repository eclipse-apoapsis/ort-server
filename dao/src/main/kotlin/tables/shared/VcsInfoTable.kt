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

package org.eclipse.apoapsis.ortserver.dao.tables.shared

import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent Version Control System information.
 */
object VcsInfoTable : LongIdTable("vcs_info") {
    val type = text("type")
    val url = text("url")
    val revision = text("revision")
    val path = text("path")
}

class VcsInfoDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<VcsInfoDao>(VcsInfoTable) {
        fun findByVcsInfo(vcsInfo: VcsInfo): VcsInfoDao? =
            find {
                VcsInfoTable.type eq vcsInfo.type.name and
                        (VcsInfoTable.url eq vcsInfo.url) and
                        (VcsInfoTable.revision eq vcsInfo.revision) and
                        (VcsInfoTable.path eq vcsInfo.path)
            }.firstOrNull()

        fun getOrPut(vcsInfo: VcsInfo): VcsInfoDao =
            findByVcsInfo(vcsInfo) ?: new {
                type = vcsInfo.type.name
                url = vcsInfo.url
                revision = vcsInfo.revision
                path = vcsInfo.path
            }
    }

    var type by VcsInfoTable.type
    var url by VcsInfoTable.url
    var revision by VcsInfoTable.revision
    var path by VcsInfoTable.path

    fun mapToModel() = VcsInfo(type = RepositoryType.forName(type), url = url, revision = revision, path = path)
}
