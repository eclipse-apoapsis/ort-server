/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult

import org.jetbrains.exposed.sql.Database

/**
 * A service to interact with ORT runs.
 */
class OrtRunService(
    private val db: Database,
    private val ortRunRepository: OrtRunRepository
) {
    suspend fun listOrtRuns(
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT,
        filters: OrtRunFilters? = null
    ): ListQueryResult<OrtRun> = db.dbQuery {
        ortRunRepository.list(parameters, filters)
    }

    suspend fun deleteOrtRun(ortRunId: Long): Unit = db.dbQuery {
        if (ortRunRepository.delete(ortRunId) == 0) {
            throw ResourceNotFoundException("ORT run with id '$ortRunId' not found.")
        }
    }

    suspend fun deleteOrtRun(repositoryId: Long, ortRunIndex: Long) = db.dbQuery {
        if (ortRunRepository.deleteByRepositoryIdAndOrtRunIndex(repositoryId, ortRunIndex) == 0) {
            throw ResourceNotFoundException(
                "ORT run with repositoryId '$repositoryId' and ortRunIndex '$ortRunIndex' not found."
            )
        }
    }
}
