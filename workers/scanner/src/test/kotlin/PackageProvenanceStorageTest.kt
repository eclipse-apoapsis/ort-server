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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.provenance.ResolvedArtifactProvenance
import org.ossreviewtoolkit.scanner.provenance.ResolvedRepositoryProvenance
import org.ossreviewtoolkit.scanner.provenance.UnresolvedPackageProvenance
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures

class PackageProvenanceStorageTest : StringSpec() {
    private val packageProvenanceStorage = OrtServerPackageProvenanceStorage()

    private lateinit var fixtures: Fixtures

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
            }
        )

        "put should create an artifact provenance in the database" {
            val id = createIdentifier()
            val sourceArtifact = createRemoteArtifact()
            val provenance = createArtifactProvenance(sourceArtifact)

            packageProvenanceStorage.putProvenance(id, sourceArtifact, provenance)

            val dbEntry = packageProvenanceStorage.readProvenance(id, sourceArtifact)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe provenance
        }

        "put should create an repository provenance in the database" {
            val id = createIdentifier()
            val vcsInfo = createVcsInfo()
            val provenance = createRepositoryProvenance(vcsInfo)

            packageProvenanceStorage.putProvenance(id, vcsInfo, provenance)

            val dbEntry = packageProvenanceStorage.readProvenance(id, vcsInfo)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe provenance
        }

        "put should replace an existing provenance in the database" {
            val id = createIdentifier()
            val vcsInfo = createVcsInfo()
            val provenance = createRepositoryProvenance(vcsInfo)

            packageProvenanceStorage.putProvenance(id, vcsInfo, provenance)

            val errorProvenance = createErrorProvenance("Provenance error")

            packageProvenanceStorage.putProvenance(id, vcsInfo, errorProvenance)

            val dbEntry = packageProvenanceStorage.readProvenance(id, vcsInfo)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe errorProvenance
        }

        "Reading all results" should {
            "return all stored results" {
                val id = createIdentifier()
                val sourceArtifact = createRemoteArtifact()
                val artifactProvenance = createArtifactProvenance(sourceArtifact)
                packageProvenanceStorage.putProvenance(id, sourceArtifact, artifactProvenance)

                val vcsInfo = createVcsInfo()
                val repositoryProvenance = createRepositoryProvenance(vcsInfo)
                packageProvenanceStorage.putProvenance(id, vcsInfo, repositoryProvenance)

                packageProvenanceStorage.readProvenances(id) should containExactlyInAnyOrder(
                        artifactProvenance,
                        repositoryProvenance
                )
            }
        }
    }
}

private fun createIdentifier() = Identifier("Maven:org.apache.logging.log4j:log4j-api:2.14.1")

private fun createRemoteArtifact() =
        RemoteArtifact(
                url = "https://repo1.maven.org/maven2/org/apache/logging/" +
                        "log4j/log4j-api/2.14.1/log4j-api-2.14.1-sources.jar",
                hash = Hash("b2327c47ca413c1ec183575b19598e281fcd74d8", HashAlgorithm.SHA1)
        )

private fun createVcsInfo() =
        VcsInfo(
                type = VcsType.GIT,
                url = "https://github.com/apache/logging-log4j2.git",
                revision = "be881e503e14b267fb8a8f94b6d15eddba7ed8c4",
                path = "testVcsPath"
        )

private fun createArtifactProvenance(artifactProvenance: RemoteArtifact) =
    ResolvedArtifactProvenance(
        provenance = ArtifactProvenance(
            RemoteArtifact(
                url = artifactProvenance.url,
                hash = Hash(
                    value = artifactProvenance.hash.value,
                    algorithm = artifactProvenance.hash.algorithm
                )
            )
        ),
    )

private fun createRepositoryProvenance(vcsInfo: VcsInfo) =
    ResolvedRepositoryProvenance(
        provenance = RepositoryProvenance(
            resolvedRevision = vcsInfo.revision,
            vcsInfo = VcsInfo(
                path = vcsInfo.path,
                url = vcsInfo.url,
                revision = vcsInfo.revision,
                type = VcsType(vcsInfo.type.toString())
            )
        ),
        clonedRevision = vcsInfo.revision,
        isFixedRevision = true
    )

private fun createErrorProvenance(errorMessage: String) =
    UnresolvedPackageProvenance(
        message = errorMessage
    )
