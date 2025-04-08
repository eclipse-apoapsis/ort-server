/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.services

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.contentSection.ContentManagementSectionDao
import org.eclipse.apoapsis.ortserver.model.ContentManagementSection

import org.jetbrains.exposed.sql.Database

class ContentManagementService(private val db: Database) {
    suspend fun findSectionById(id: String): ContentManagementSection? = db.dbQuery(readOnly = true) {
        ContentManagementSectionDao[id].mapToModel()
    }

    suspend fun updateSectionById(
        id: String,
        isEnabled: Boolean,
        markdown: String
    ): ContentManagementSection = db.dbQuery {
        val section = ContentManagementSectionDao[id]

        section.isEnabled = isEnabled
        section.markdown = markdown
        section.updatedAt = Clock.System.now()

        ContentManagementSectionDao[id].mapToModel()
    }
}
