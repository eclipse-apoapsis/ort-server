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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.storage.Key

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class ProvenanceStorageKeyTest : WordSpec({
    "toStorageKey" should {
        "generate the key for an artifact provenance" {
            artifactProvenance.toStorageKey() shouldBe
                Key("source-artifact|$ARTIFACT_URL|Hash(value=$HASH_VALUE, algorithm=SHA-1)")
        }

        "generate the key for a repository provenance" {
            repositoryProvenance.toStorageKey() shouldBe Key("vcs|Git|$REPOSITORY_URL|$RESOLVED_REVISION")
        }
    }

    "parseStorageKey" should {
        "restore the information of an artifact provenance" {
            parseStorageKey(artifactProvenance.toStorageKey()) shouldBe ParsedProvenanceKey.SourceArtifact(
                url = ARTIFACT_URL,
                hashValue = HASH_VALUE,
                hashAlgorithm = "SHA-1"
            )
        }

        "restore the information of a repository provenance" {
            parseStorageKey(repositoryProvenance.toStorageKey()) shouldBe ParsedProvenanceKey.Vcs(
                url = REPOSITORY_URL,
                vcsType = "Git",
                revision = RESOLVED_REVISION
            )
        }

        "handle URLs containing the separator character" {
            val url = "https://example.org/repo|with|pipes.git"
            val provenance = repositoryProvenance.copy(vcsInfo = repositoryProvenance.vcsInfo.copy(url = url))

            parseStorageKey(provenance.toStorageKey()) shouldBe ParsedProvenanceKey.Vcs(
                url = url,
                vcsType = "Git",
                revision = RESOLVED_REVISION
            )
        }

        "handle artifacts without a hash" {
            val provenance = ArtifactProvenance(RemoteArtifact(ARTIFACT_URL, Hash.NONE))

            parseStorageKey(provenance.toStorageKey()) shouldBe ParsedProvenanceKey.SourceArtifact(
                url = ARTIFACT_URL,
                hashValue = "",
                hashAlgorithm = null
            )
        }

        "use the raw hash if it has an unexpected format" {
            parseStorageKey(Key("source-artifact|$ARTIFACT_URL|$HASH_VALUE")) shouldBe
                ParsedProvenanceKey.SourceArtifact(
                    url = ARTIFACT_URL,
                    hashValue = HASH_VALUE,
                    hashAlgorithm = null
                )
        }

        "return null for keys with an unknown prefix" {
            parseStorageKey(Key("unknown|$REPOSITORY_URL|$RESOLVED_REVISION")) should beNull()
        }

        "return null for keys with too few components" {
            parseStorageKey(Key("source-artifact|$ARTIFACT_URL")) should beNull()
            parseStorageKey(Key("vcs|Git|$REPOSITORY_URL")) should beNull()
            parseStorageKey(Key("")) should beNull()
        }
    }
})

private const val ARTIFACT_URL = "https://example.org/artifact.zip"
private const val HASH_VALUE = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
private const val REPOSITORY_URL = "https://example.org/repo.git"
private const val RESOLVED_REVISION = "0123456789abcdef0123456789abcdef01234567"

private val artifactProvenance = ArtifactProvenance(
    sourceArtifact = RemoteArtifact(ARTIFACT_URL, Hash(HASH_VALUE, HashAlgorithm.SHA1))
)

private val repositoryProvenance = RepositoryProvenance(
    vcsInfo = VcsInfo(type = VcsType.GIT, url = REPOSITORY_URL, revision = "main"),
    resolvedRevision = RESOLVED_REVISION
)
