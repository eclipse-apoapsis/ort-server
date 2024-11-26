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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

/**
 * A table to represent a key-value pair, which belongs to a label for a [DefectsTable].
 */
object DefectLabelsTable : LongIdTable("defect_labels") {
    val defectId = reference("defect_id", DefectsTable)

    val key = text("key")
    val value = text("value")
}

class DefectLabelDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<DefectLabelDao>(DefectLabelsTable)

    var defect by DefectDao referencedOn DefectLabelsTable.defectId

    var key by DefectLabelsTable.key
    var value by DefectLabelsTable.value
}
