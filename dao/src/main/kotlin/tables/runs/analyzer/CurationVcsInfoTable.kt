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

package org.ossreviewtoolkit.server.dao.tables.runs.analyzer

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.runs.CurationVcsInfo

/**
 * A table to represent Version Control System information with nullable columns.
 */
object CurationVcsInfoTable : LongIdTable("curation_vcs_info") {
    val type = enumerationByName<RepositoryType>("type", 128).nullable()
    val url = text("url").nullable()
    val revision = text("revision").nullable()
    val path = text("path").nullable()
}

class CurationVcsInfoDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CurationVcsInfoDao>(CurationVcsInfoTable)

    var type by CurationVcsInfoTable.type
    var url by CurationVcsInfoTable.url
    var revision by CurationVcsInfoTable.revision
    var path by CurationVcsInfoTable.path

    fun mapToModel() = CurationVcsInfo(id.value, type, url, revision, path)
}
