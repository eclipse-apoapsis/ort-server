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

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceResolutionResult
import org.ossreviewtoolkit.scanner.provenance.PackageProvenanceStorage
import org.ossreviewtoolkit.scanner.provenance.ResolvedArtifactProvenance
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.UnresolvedPackageProvenance
import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.PackageProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.PackageProvenancesTable
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.RemoteArtifactDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.VcsInfoDao
import org.ossreviewtoolkit.server.workers.common.mapToModel

class OrtServerPackageProvenanceStorage : PackageProvenanceStorage {
    override fun readProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact
    ): PackageProvenanceResolutionResult? = blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel())
        val sourceArtifactDao = RemoteArtifactDao.findByRemoteArtifact(sourceArtifact.mapToModel())

        PackageProvenanceDao.find(
            PackageProvenancesTable.identifierId eq identifierDao?.id?.value and
                    (PackageProvenancesTable.artifactId eq sourceArtifactDao?.id?.value)
        ).singleOrNull()?.mapToModel()
    }.getOrThrow()

    override fun readProvenance(id: Identifier, vcs: VcsInfo): PackageProvenanceResolutionResult? = blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel())
        val vcsInfoDao = VcsInfoDao.findByVcsInfo(vcs.mapToModel())

        PackageProvenanceDao.find(
            PackageProvenancesTable.identifierId eq identifierDao?.id?.value and
                    (PackageProvenancesTable.vcsId eq vcsInfoDao?.id?.value)
        ).singleOrNull()?.mapToModel()
    }.getOrThrow()

    override fun readProvenances(id: Identifier): List<PackageProvenanceResolutionResult> = blockingQuery {
        val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel())

        PackageProvenanceDao.find(PackageProvenancesTable.identifierId eq identifierDao?.id?.value)
            .mapNotNull { it.mapToModel() }
    }.getOrThrow()

    override fun putProvenance(
        id: Identifier,
        sourceArtifact: RemoteArtifact,
        result: PackageProvenanceResolutionResult
    ) {
        blockingQuery {
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

            PackageProvenancesTable.deleteWhere {
                identifierId eq identifierDao.id.value and
                        (artifactId.isNotNull()) and
                        (artifactId eq artifactDao.id.value)
            }

            PackageProvenanceDao.new {
                identifier = identifierDao
                artifact = artifactDao
                if (result is UnresolvedPackageProvenance) {
                    errorMessage = result.message
                }
            }
        }.getOrThrow()
    }

    override fun putProvenance(
        id: Identifier,
        vcs: VcsInfo,
        result: PackageProvenanceResolutionResult
    ) {
        blockingQuery {
            val identifierDao = IdentifierDao.findByIdentifier(id.mapToModel()) ?: IdentifierDao.new {
                type = id.type
                namespace = id.namespace
                name = id.name
                version = id.version
            }

            val vcsDao = VcsInfoDao.findByVcsInfo(vcs.mapToModel()) ?: VcsInfoDao.new {
                type = vcs.type.mapToModel()
                url = vcs.url
                revision = vcs.revision
                path = vcs.path
            }

            PackageProvenancesTable.deleteWhere {
                identifierId eq identifierDao.id.value and
                        (vcsId.isNotNull()) and
                        (vcsId eq vcsDao.id.value)
            }

            PackageProvenanceDao.new {
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
        }.getOrThrow()
    }
}

fun PackageProvenanceDao.mapToModel(): PackageProvenanceResolutionResult? = when {
    errorMessage is String -> UnresolvedPackageProvenance(errorMessage.orEmpty())
    artifact is RemoteArtifactDao -> artifact?.let {
        ResolvedArtifactProvenance(
            provenance = ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = it.url,
                    hash = Hash(
                        value = it.hashValue,
                        algorithm = HashAlgorithm.fromString(it.hashAlgorithm)
                    )
                )
            )
        )
    }
    vcs is VcsInfoDao -> vcs?.let {
        ResolvedRepositoryProvenance(
            provenance = RepositoryProvenance(
                vcsInfo = VcsInfo(
                    path = it.path,
                    url = it.url,
                    revision = it.revision,
                    type = VcsType.forName(it.type.name)
                ),
                resolvedRevision = resolvedRevision.orEmpty()
            ),
            clonedRevision = clonedRevision.orEmpty(),
            isFixedRevision = isFixedRevision ?: false
        )
    }
    else -> null
}
