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

package org.eclipse.apoapsis.ortserver.dao.repositories.contentSection

import org.eclipse.apoapsis.ortserver.dao.utils.transformToDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.ContentManagementSection

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A table that represents Markdown-formatted text to be displayed in UI sections.
 */
object ContentManagementSectionTable : IdTable<String>("content_management_sections") {
    /**
     * Unique identifier for the section.
     */
    override val id: Column<EntityID<String>> = text("id").entityId()

    val isEnabled = bool("is_enabled").default(false)

    val markdown = text("markdown")

    val updatedAt = timestamp("updated_at")
}

class ContentManagementSectionDao(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, ContentManagementSectionDao>(ContentManagementSectionTable)

    var isEnabled by ContentManagementSectionTable.isEnabled
    var markdown by ContentManagementSectionTable.markdown
    var updatedAt by ContentManagementSectionTable.updatedAt.transformToDatabasePrecision()

    fun mapToModel() = ContentManagementSection(
        id = id.value,
        isEnabled = isEnabled,
        markdown = markdown,
        updatedAt = updatedAt
    )
}
