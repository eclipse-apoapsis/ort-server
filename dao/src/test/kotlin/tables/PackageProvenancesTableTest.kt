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

package org.eclipse.apoapsis.ortserver.dao.tables

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.tables.shared.VcsInfoDao
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ArtifactProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.RepositoryProvenance
import org.eclipse.apoapsis.ortserver.model.runs.scanner.UnknownProvenance

class PackageProvenancesTableTest : WordSpec({
    "mapToModel" should {
        fun createDao(
            errorMessage: String? = null,
            remoteArtifact: RemoteArtifact? = null,
            vcsInfo: VcsInfo? = null,
            resolvedRevision: String? = null
        ) = mockk<PackageProvenanceDao> {
            every { this@mockk.errorMessage } returns errorMessage
            every { this@mockk.resolvedRevision } returns resolvedRevision
            every { mapToModel() } answers { callOriginal() }

            every { artifact } answers {
                when (remoteArtifact) {
                    null -> null
                    else -> mockk<RemoteArtifactDao> { every { mapToModel() } returns remoteArtifact }
                }
            }
            every { vcs } answers {
                when (vcsInfo) {
                    null -> null
                    else -> mockk<VcsInfoDao> { every { mapToModel() } returns vcsInfo }
                }
            }
        }

        "create an UnknownProvenance if the error message is set" {
            val dao = createDao(errorMessage = "Error message.")

            dao.mapToModel() shouldBe UnknownProvenance
        }

        "create an ArtifactProvenance if the the artifact is set" {
            val remoteArtifact = RemoteArtifact(url = "url", hashValue = "hashValue", hashAlgorithm = "hashAlgorithm")
            val dao = createDao(remoteArtifact = remoteArtifact)

            dao.mapToModel() shouldBe ArtifactProvenance(sourceArtifact = remoteArtifact)
        }

        "create a RepositoryProvenance if the vcs is set" {
            val vcsInfo = VcsInfo(type = RepositoryType.GIT, url = "url", revision = "revision", path = "path")
            val resolvedRevision = "resolvedRevision"
            val dao = createDao(vcsInfo = vcsInfo, resolvedRevision = resolvedRevision)

            dao.mapToModel() shouldBe RepositoryProvenance(vcsInfo, resolvedRevision)
        }

        "fail if vcs is set but resolvedRevision is not set" {
            val vcsInfo = VcsInfo(type = RepositoryType.GIT, url = "url", revision = "revision", path = "path")
            val dao = createDao(vcsInfo = vcsInfo)

            shouldThrow<IllegalStateException> { dao.mapToModel() }
        }

        "create an UnknownProvenance if no properties are set" {
            val dao = createDao()

            dao.mapToModel() shouldBe UnknownProvenance
        }
    }
})
