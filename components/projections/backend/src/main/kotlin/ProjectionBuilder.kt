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

/** An interface for building the projections of a specific type for an ORT run. */
interface ProjectionBuilder {
    /** The type of the projections this builder creates. It must be unique among all builders. */
    val type: String

    /**
     * The version of the schema of the projections this builder creates. It must be incremented whenever the data
     * stored for a projection changes in an incompatible way, so that existing projections are rebuilt on their next
     * request.
     */
    val schemaVersion: Int

    /**
     * Build the projection for the ORT run with the given [ortRunId]. This function is called inside a database
     * transaction which also marks the projection as ready. It must replace any data that already exists for the run,
     * because a projection can be rebuilt, for example after its schema version has changed.
     */
    suspend fun build(ortRunId: Long)
}
