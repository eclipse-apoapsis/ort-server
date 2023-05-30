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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.scanner.provenance.NestedProvenance
import org.ossreviewtoolkit.scanner.provenance.NestedProvenanceResolutionResult
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension

class OrtServerNestedProvenanceStorageTest : WordSpec() {
    private lateinit var storage: OrtServerNestedProvenanceStorage

    init {
        extension(DatabaseTestExtension { db -> storage = OrtServerNestedProvenanceStorage(db) })

        "putNestedProvenance" should {
            "store a nested provenance in the database" {
                val root = createRepositoryProvenance()
                val result = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(root),
                    hasOnlyFixedRevisions = true
                )

                storage.putNestedProvenance(root, result)

                storage.readNestedProvenance(root) shouldBe result
            }

            "overwrite a previously stored nested provenance" {
                val root = createRepositoryProvenance()
                val result1 = NestedProvenanceResolutionResult(
                    nestedProvenance = createNestedProvenance(root, mapOf("path" to root)),
                    hasOnlyFixedRevisions = true
                )
                val result2 = NestedProvenanceResolutionResult(createNestedProvenance(root), true)

                storage.putNestedProvenance(root, result1)
                storage.putNestedProvenance(root, result2)

                storage.readNestedProvenance(root) shouldBe result2
            }
        }
    }
}

private fun createVcsInfo(
    type: VcsType = VcsType.GIT,
    url: String = "https://github.com/apache/logging-log4j2.git",
    revision: String = "be881e503e14b267fb8a8f94b6d15eddba7ed8c4"
) = VcsInfo(type, url, revision)

private fun createRepositoryProvenance(
    vcsInfo: VcsInfo = createVcsInfo(),
    resolvedRevision: String = vcsInfo.revision
) = RepositoryProvenance(vcsInfo, resolvedRevision)

private fun createNestedProvenance(
    root: KnownProvenance,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) = NestedProvenance(root, subRepositories)
