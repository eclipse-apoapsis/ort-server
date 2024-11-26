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

import org.jetbrains.exposed.sql.Table

/**
 * A junction table to link [AdvisorConfigurationsTable] with [AdvisorConfigurationSecretsTable].
 */
object AdvisorConfigurationsSecretsTable : Table("advisor_configurations_secrets") {
    val advisorConfigurationId = reference("advisor_configuration_id", AdvisorConfigurationsTable)
    val advisorConfigurationSecretId = reference("advisor_configuration_secret_id", AdvisorConfigurationSecretsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(advisorConfigurationId, advisorConfigurationSecretId, name = "${tableName}_pkey")
}
