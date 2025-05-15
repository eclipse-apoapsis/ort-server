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

package org.eclipse.apoapsis.ortserver.workers.scanner

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceSubRepositoryDao
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoTable
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceStorage
import org.ossreviewtoolkit.utils.ort.runBlocking

class OrtServerNestedProvenanceStorage(
    private val db: Database,
    private val packageProvenanceCache: PackageProvenanceCache,
    private val vcsPluginConfigs: String?
) : NestedProvenanceStorage {
    override fun writeNestedProvenance(
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
            vcsPluginConfigs = this@OrtServerNestedProvenanceStorage.vcsPluginConfigs
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
                .select(NestedProvenancesTable.columns)
                .where {
                    VcsInfoTable.type eq resolvedVcs.type.name and
                            (VcsInfoTable.url eq resolvedVcs.url) and
                            (VcsInfoTable.revision eq resolvedVcs.revision) and
                            (
                                    NestedProvenancesTable.vcsPluginConfigs eq
                                            this@OrtServerNestedProvenanceStorage.vcsPluginConfigs
                                    )
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
        runBlocking {
            packageProvenanceCache.get(provenance).forEach { packageProvenanceId ->
                PackageProvenanceDao[packageProvenanceId].nestedProvenance = nestedProvenanceDao
            }
        }

        associateWithPendingSubProvenances(provenance, nestedProvenanceDao)
    }

    /**
     * Check whether there are package provenances for sub paths of the given [root] provenance that need to be
     * associated with the newly created [nestedProvenanceDao]. If so, do the association now. This function handles
     * the corner case that package provenances have already been added to the [PackageProvenanceCache] before the
     * nested provenance resolution result becomes available.
     */
    private fun associateWithPendingSubProvenances(
        root: RepositoryProvenance,
        nestedProvenanceDao: NestedProvenanceDao
    ) {
        val pendingProvenanceIds = runBlocking {
            packageProvenanceCache.putNestedProvenance(root, nestedProvenanceDao.id.value)
        }

        pendingProvenanceIds.forEach { provenanceId ->
            PackageProvenanceDao[provenanceId].nestedProvenance = nestedProvenanceDao
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
