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

package org.eclipse.apoapsis.ortserver.workers.scanner.scanner

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.workers.scanner.PackageProvenanceCache

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PackageProvenanceCacheTest : WordSpec({
    "get" should {
        "return an empty list for an unknown provenance" {
            val cache = PackageProvenanceCache()

            cache.get(rootProvenance) should beEmpty()
        }

        "return the assigned database IDs" {
            val provenanceDaoId1 = 20240111153515L
            val provenanceDaoId2 = 20240617092022L
            val cache = PackageProvenanceCache()

            cache.putAndGetNestedProvenance(rootProvenance, provenanceDaoId1)
            cache.putAndGetNestedProvenance(rootProvenance, provenanceDaoId2)

            cache.get(rootProvenance) shouldContainExactlyInAnyOrder listOf(provenanceDaoId1, provenanceDaoId2)
        }
    }

    "putNestedProvenance" should {
        "reject a provenance with a path" {
            val nestedProvenance = nestedProvenance("some/path")
            val cache = PackageProvenanceCache()

            shouldThrow<IllegalArgumentException> {
                cache.putNestedProvenance(nestedProvenance, 42L)
            }
        }

        "return an empty collection if there are no provenances to assign" {
            val cache = PackageProvenanceCache()

            cache.putNestedProvenance(rootProvenance, 20240111161910L) should beEmpty()
        }

        "return a collection of provenance IDs to be associated with the nested provenance" {
            val nestedProvenance1 = nestedProvenance("path1")
            val nestedProvenanceId1 = 20240111162245L
            val nestedProvenance2 = nestedProvenance("path2")
            val nestedProvenanceId2 = 20240111162305L

            val cache = PackageProvenanceCache()
            cache.putAndGetNestedProvenance(nestedProvenance1, nestedProvenanceId1)
            cache.putAndGetNestedProvenance(nestedProvenance2, nestedProvenanceId2)

            val idsToAssociate = cache.putNestedProvenance(rootProvenance, 20240111162417L)

            idsToAssociate shouldContainExactlyInAnyOrder listOf(nestedProvenanceId1, nestedProvenanceId2)
        }

        "not return root provenances" {
            val cache = PackageProvenanceCache()
            cache.putAndGetNestedProvenance(rootProvenance, 42)

            cache.putNestedProvenance(rootProvenance, 43) should beEmpty()
        }
    }

    "putAndGetNestedProvenance" should {
        "return null if no nested provenance is available" {
            val cache = PackageProvenanceCache()

            cache.putAndGetNestedProvenance(rootProvenance, 42) should beNull()
        }

        "return the ID of the nested provenance to assign to the current provenance dao" {
            val subProvenance = nestedProvenance("sub")
            val nestedProvenanceId = 20240112074414L

            val cache = PackageProvenanceCache()
            cache.putNestedProvenance(rootProvenance, nestedProvenanceId)

            cache.putAndGetNestedProvenance(subProvenance, 20240112074356L) shouldBe nestedProvenanceId
        }
    }
})

/** A test resolved revision. */
private const val REVISION = "12345"

/** VCS information for the test root repository. */
private val rootVcs = VcsInfo(VcsType.GIT, "https://repo.example.org/test.git", "main")

/** The provenance for the test root repository. */
private val rootProvenance = RepositoryProvenance(rootVcs, REVISION)

/**
 * Return a [RepositoryProvenance] for a component located in a sub [path] of the root repository.
 */
private fun nestedProvenance(path: String): RepositoryProvenance =
    RepositoryProvenance(rootVcs.copy(path = path), REVISION)
