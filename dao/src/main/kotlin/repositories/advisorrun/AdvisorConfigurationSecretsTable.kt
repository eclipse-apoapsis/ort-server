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
 * A table to represent a secret for an advisor. The [secret] refers to the name of the secret as expected by the
 * advisor implementation. The [value] is not the actual value of the secret but the name of the secret in the secret
 * storage.
 */
object AdvisorConfigurationSecretsTable : LongIdTable("advisor_configuration_secrets") {
    val advisor = text("advisor")
    val secret = text("secret")
    val value = text("value")
}

class AdvisorConfigurationSecretDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AdvisorConfigurationSecretDao>(AdvisorConfigurationSecretsTable) {
        fun find(advisor: String, secret: String, value: String): AdvisorConfigurationSecretDao? =
            find {
                AdvisorConfigurationSecretsTable.advisor eq advisor and
                        (AdvisorConfigurationSecretsTable.secret eq secret) and
                        (AdvisorConfigurationSecretsTable.value eq value)
            }.firstOrNull()

        fun getOrPut(advisor: String, secret: String, value: String): AdvisorConfigurationSecretDao =
            find(advisor, secret, value) ?: new {
                this.advisor = advisor
                this.secret = secret
                this.value = value
            }
    }

    var advisor by AdvisorConfigurationSecretsTable.advisor
    var secret by AdvisorConfigurationSecretsTable.secret
    var value by AdvisorConfigurationSecretsTable.value
}
