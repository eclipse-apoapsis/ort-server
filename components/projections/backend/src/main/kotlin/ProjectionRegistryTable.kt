/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.projections

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/** The table that stores the state of all projections and serves as the work queue for building them. */
object ProjectionRegistryTable : Table("projection_registry") {
    val ortRunId = long("ort_run_id")
    val projectionType = text("projection_type")
    val schemaVersion = integer("schema_version")
    val status = text("status").transform({ enumValueOf<ProjectionStatus>(it) }, { it.name })
    val leaseOwner = text("lease_owner").nullable()
    val leaseUntil = timestamp("lease_until").nullable()
    val attempts = integer("attempts")
    val nextAttempt = timestamp("next_attempt").nullable()
    val error = text("error").nullable()
    val createdAt = timestamp("created_at")
    val lastAccessedAt = timestamp("last_accessed_at")

    override val primaryKey = PrimaryKey(ortRunId, projectionType)
}
