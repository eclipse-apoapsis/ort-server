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

package org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.and

/**
 * A table to represent an option for an advisor.
 */
object AdvisorConfigurationOptionsTable : LongIdTable("advisor_configuration_options") {
    val advisor = text("advisor")
    val option = text("option")
    val value = text("value")
}

class AdvisorConfigurationOptionDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorConfigurationOptionDao>(AdvisorConfigurationOptionsTable) {
        fun find(advisor: String, option: String, value: String): AdvisorConfigurationOptionDao? =
            find {
                AdvisorConfigurationOptionsTable.advisor eq advisor and
                        (AdvisorConfigurationOptionsTable.option eq option) and
                        (AdvisorConfigurationOptionsTable.value eq value)
            }.firstOrNull()

        fun getOrPut(advisor: String, option: String, value: String): AdvisorConfigurationOptionDao =
            find(advisor, option, value) ?: new {
                this.advisor = advisor
                this.option = option
                this.value = value
            }
    }

    var advisor by AdvisorConfigurationOptionsTable.advisor
    var option by AdvisorConfigurationOptionsTable.option
    var value by AdvisorConfigurationOptionsTable.value
}
