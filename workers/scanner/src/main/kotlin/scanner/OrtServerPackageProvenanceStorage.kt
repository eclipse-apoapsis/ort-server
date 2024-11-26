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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.ScannerRunsPackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenancesTable
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel
import org.eclipse.apoapsis.ortserver.workers.common.mapToOrt

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.ResolvedArtifactProvenance
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.UnresolvedPackageProvenance
import org.ossreviewtoolkit.utils.ort.runBlocking

/**
 * An ORT Server specific implementation of the `PackageProvenanceStorage`. Read and put package provenances are
 * associated to the scanner run with the provided [scannerRunId].
 */
class OrtServerPackageProvenanceStorage(
    private val db: Database,
    private val scannerRunId: Long,
    private val cache: PackageProvenanceCache
) : PackageProvenanceStorage {
    override fun readProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact
    ): PackageProvenanceResolutionResult? = db.blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel()) ?: return@blockingQuery null
        val sourceArtifactDao =
            RemoteArtifactDao.findByRemoteArtifact(sourceArtifact.mapToModel()) ?: return@blockingQuery null

        val provenanceDao = getLatestProvenance(
            identifierId = identifierDao.id.value,
            condition = PackageProvenancesTable.artifactId eq sourceArtifactDao.id.value
        )

        if (isAcceptedResult(provenanceDao)) {
            associateProvenanceWithScannerRun(provenanceDao)
        }

        provenanceDao?.mapToOrt()
    }

    override fun readProvenance(id: Identifier, vcs: VcsInfo): PackageProvenanceResolutionResult? = db.blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel()) ?: return@blockingQuery null
        val vcsInfoDao = VcsInfoDao.findByVcsInfo(vcs.mapToModel()) ?: return@blockingQuery null

        val provenanceDao = getLatestProvenance(
            identifierId = identifierDao.id.value,
            condition = PackageProvenancesTable.vcsId eq vcsInfoDao.id.value
        )

        if (isAcceptedResult(provenanceDao)) {
            associateProvenanceWithScannerRun(provenanceDao)
        }

        provenanceDao?.mapToOrt()
    }

    /**
     * Return the latest [PackageProvenanceDao] that matches the provided [identifierId] and [condition], or nul if
     * there is no match.
     */
    private fun getLatestProvenance(identifierId: Long?, condition: Op<Boolean>) =
        PackageProvenanceDao.find(PackageProvenancesTable.identifierId eq identifierId and condition)
            .orderBy(PackageProvenancesTable.id to SortOrder.DESC).limit(1).singleOrNull()

    override fun readProvenances(id: Identifier): List<PackageProvenanceResolutionResult> = db.blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel())

        PackageProvenanceDao.find(PackageProvenancesTable.identifierId eq identifierDao?.id?.value)
            .mapNotNull { it.mapToOrt() }
    }

    override fun writeProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact,
        result: PackageProvenanceResolutionResult
    ) {
        db.blockingQuery {
            val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel()) ?: IdentifierDao.new {
                type = id.type
                namespace = id.namespace
                name = id.name
                version = id.version
            }

            val artifactDao = RemoteArtifactDao.findByRemoteArtifact(sourceArtifact.mapToModel())
                ?: RemoteArtifactDao.new {
                    url = sourceArtifact.url
                    hashValue = sourceArtifact.hash.value
                    hashAlgorithm = sourceArtifact.hash.algorithm.toString()
                }

            val provenanceDao = PackageProvenanceDao.new {
                identifier = identifierDao
                artifact = artifactDao
                if (result is UnresolvedPackageProvenance) {
                    errorMessage = result.message
                }
            }

            associateProvenanceWithScannerRun(provenanceDao)
        }
    }

    override fun writeProvenance(
        id: Identifier,
        vcs: VcsInfo,
        result: PackageProvenanceResolutionResult
    ) {
        db.blockingQuery {
            val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel()) ?: IdentifierDao.new {
                type = id.type
                namespace = id.namespace
                name = id.name
                version = id.version
            }

            val vcsDao = VcsInfoDao.findByVcsInfo(vcs.mapToModel()) ?: VcsInfoDao.new {
                type = vcs.type.mapToModel().name
                url = vcs.url
                revision = vcs.revision
                path = vcs.path
            }

            val provenanceDao = PackageProvenanceDao.new {
                identifier = identifierDao
                this.vcs = vcsDao
                if (result is ResolvedRepositoryProvenance) {
                    this.resolvedRevision = result.provenance.resolvedRevision
                    this.isFixedRevision = result.isFixedRevision
                    this.clonedRevision = result.clonedRevision
                } else if (result is UnresolvedPackageProvenance) {
                    this.errorMessage = result.message
                }
            }

            associateProvenanceWithScannerRun(provenanceDao)
        }
    }

    override fun deleteProvenances(id: Identifier) {
        // Do not implement the function as it is currently only used by a helper CLI command.
        throw UnsupportedOperationException("deleteProvenance is not implemented.")
    }

    private fun associateProvenanceWithScannerRun(provenanceDao: PackageProvenanceDao) {
        ScannerRunsPackageProvenancesTable.insertIfNotExists(
            scannerRunId = scannerRunId,
            packageProvenanceId = provenanceDao.id.value
        )

        runBlocking {
            (provenanceDao.mapToOrt() as? ResolvedRepositoryProvenance)?.provenance?.let {
                cache.putAndGetNestedProvenance(it, provenanceDao.id.value)?.let { nestedId ->
                    provenanceDao.nestedProvenance = NestedProvenanceDao[nestedId]
                }
            }
        }
    }
}

fun PackageProvenanceDao.mapToOrt(): PackageProvenanceResolutionResult? = when {
    errorMessage is String -> UnresolvedPackageProvenance(errorMessage.orEmpty())

    artifact is RemoteArtifactDao -> artifact?.let {
        ResolvedArtifactProvenance(
            provenance = ArtifactProvenance(
                sourceArtifact = it.mapToModel().mapToOrt()
            )
        )
    }

    vcs is VcsInfoDao -> vcs?.let {
        ResolvedRepositoryProvenance(
            provenance = RepositoryProvenance(
                vcsInfo = it.mapToModel().mapToOrt(),
                resolvedRevision = resolvedRevision.orEmpty()
            ),
            clonedRevision = clonedRevision.orEmpty(),
            isFixedRevision = isFixedRevision ?: false
        )
    }

    else -> null
}

/**
 * Return `true` if ORT would accept the result, return `false` if ORT would re-attempt the provenance
 * resolution.
 */
@OptIn(ExperimentalContracts::class)
private fun isAcceptedResult(provenanceDao: PackageProvenanceDao?): Boolean {
    contract {
        returns(true) implies (provenanceDao != null)
    }

    val result = provenanceDao?.mapToOrt()
    return when {
        result == null -> false
        result is UnresolvedPackageProvenance -> false
        result is ResolvedRepositoryProvenance && !result.isFixedRevision -> false
        else -> true
    }
}
