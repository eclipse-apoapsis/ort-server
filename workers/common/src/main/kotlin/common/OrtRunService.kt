/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common

import kotlinx.coroutines.runBlocking

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.server.dao.dbQuery
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository

class OrtRunService(
    private val db: Database,
    private val repositoryConfigurationRepository: RepositoryConfigurationRepository
) {
    /**
     * Fetch the repository data from the database and construct an ORT [Repository] object from a provided ORT run.
     */
    fun getOrtRepositoryInformation(ortRun: OrtRun) = runBlocking {
        db.dbQuery {
            val vcsId = ortRun.vcsId
            requireNotNull(vcsId) {
                "VCS information is missing from ORT run '${ortRun.id}'."
            }

            val vcsProcessedId = ortRun.vcsProcessedId
            requireNotNull(vcsProcessedId) {
                "VCS processed information is missing from ORT run '${ortRun.id}'."
            }

            val nestedRepositoryIds = ortRun.nestedRepositoryIds
            requireNotNull(nestedRepositoryIds) {
                "Nested repositories information is missing from ORT run '${ortRun.id}'."
            }

            val vcsInfo = VcsInfoDao[vcsId].mapToModel()
            val vcsProcessedInfo = VcsInfoDao[vcsProcessedId].mapToModel()
            val nestedRepositories =
                nestedRepositoryIds.map { Pair(it.key, VcsInfoDao[it.value].mapToModel().mapToOrt()) }.toMap()

            val repositoryConfig =
                ortRun.repositoryConfigId?.let { repositoryConfigurationRepository.get(it)?.mapToOrt() }
                    ?: RepositoryConfiguration()

            Repository(
                vcs = vcsInfo.mapToOrt(),
                vcsProcessed = vcsProcessedInfo.mapToOrt(),
                nestedRepositories = nestedRepositories,
                config = repositoryConfig
            )
        }
    }
}
