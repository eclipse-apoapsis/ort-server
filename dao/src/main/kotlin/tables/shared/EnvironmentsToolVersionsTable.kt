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

import org.jetbrains.exposed.sql.Table

/**
 * An intermediate table to store references from [EnvironmentsTable] and [ToolVersionsTable].
 */
object EnvironmentsToolVersionsTable : Table("environments_tool_versions") {
    val environmentId = reference("environment_id", EnvironmentsTable)
    val toolVersionId = reference("tool_version_id", ToolVersionsTable)

    override val primaryKey: PrimaryKey
        get() = PrimaryKey(environmentId, toolVersionId, name = "${tableName}_pkey")
}
