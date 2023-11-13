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

package org.ossreviewtoolkit.server.workers.scanner

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceStorage
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceSubRepositoryDao
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenancesTable
import org.ossreviewtoolkit.server.dao.tables.provenance.PackageProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoTable
import org.ossreviewtoolkit.server.model.runs.VcsInfo
import org.ossreviewtoolkit.server.workers.common.mapToModel
import org.ossreviewtoolkit.server.workers.common.mapToOrt

class OrtServerNestedProvenanceStorage(
    private val db: Database,
    private val packageProvenanceCache: PackageProvenanceCache
) : NestedProvenanceStorage {
    override fun putNestedProvenance(
        root: RepositoryProvenance,
        result: NestedProvenanceResolutionResult
    ) = db.blockingQuery {
        val resolvedVcs = root.getResolvedVcs()

        storeResult(resolvedVcs, root, result)
    }

    private fun storeResult(
        resolvedVcs: VcsInfo,
        root: RepositoryProvenance,
        result: NestedProvenanceResolutionResult
    ) {
        val vcsDao = VcsInfoDao.getOrPut(resolvedVcs)

        val nestedProvenanceDao = NestedProvenanceDao.new {
            rootVcs = vcsDao
            rootResolvedRevision = root.resolvedRevision
            hasOnlyFixedRevisions = result.hasOnlyFixedRevisions
        }

        result.nestedProvenance.subRepositories.forEach { (path, repositoryProvenance) ->
            val nestedVcsDao = VcsInfoDao.getOrPut(repositoryProvenance.vcsInfo.mapToModel())

            NestedProvenanceSubRepositoryDao.new {
                nestedProvenance = nestedProvenanceDao
                vcs = nestedVcsDao
                resolvedRevision = repositoryProvenance.resolvedRevision
                this.path = path
            }
        }

        associateWithPackageProvenance(root, nestedProvenanceDao)
    }

    override fun readNestedProvenance(root: RepositoryProvenance): NestedProvenanceResolutionResult? =
        db.blockingQuery {
            val resolvedVcs = root.getResolvedVcs()

            NestedProvenancesTable.innerJoin(VcsInfoTable)
                .slice(NestedProvenancesTable.columns)
                .select {
                    VcsInfoTable.type eq resolvedVcs.type.name and
                            (VcsInfoTable.url eq resolvedVcs.url) and
                            (VcsInfoTable.revision eq resolvedVcs.revision)
                }.orderBy(NestedProvenancesTable.id to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { NestedProvenanceDao.wrapRow(it) }
                ?.let { nestedProvenanceDao ->
                    associateWithPackageProvenance(root, nestedProvenanceDao)
                    nestedProvenanceDao.mapToOrt()
                }
        }

    private fun associateWithPackageProvenance(
        provenance: RepositoryProvenance,
        nestedProvenanceDao: NestedProvenanceDao
    ) {
        packageProvenanceCache.get(provenance)?.let { packageProvenanceId ->
            PackageProvenanceDao[packageProvenanceId].nestedProvenance = nestedProvenanceDao
        }
    }
}

private fun RepositoryProvenance.getResolvedVcs() = vcsInfo.copy(revision = resolvedRevision).mapToModel()

internal fun NestedProvenanceDao.mapToOrt(): NestedProvenanceResolutionResult {
    val nestedProvenance = NestedProvenance(
        root = RepositoryProvenance(
            vcsInfo = rootVcs.mapToModel().mapToOrt(),
            resolvedRevision = rootResolvedRevision
        ),
        subRepositories = subRepositories.associate {
            it.path to RepositoryProvenance(
                vcsInfo = it.vcs.mapToModel().mapToOrt(),
                resolvedRevision = it.resolvedRevision
            )
        }
    )

    return NestedProvenanceResolutionResult(nestedProvenance, hasOnlyFixedRevisions)
}
