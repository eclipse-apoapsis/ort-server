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
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntryInfo

class ProvenanceFileStatisticsCalculatorTest : WordSpec({
    val snapshotTime = Instant.fromEpochSeconds(10_000)

    "computeStatistics" should {
        "compute statistics about the given files" {
            val files = listOf(
                vcsEntry("https://example.org/a.git", "rev1", 100, Instant.fromEpochSeconds(3000)),
                vcsEntry("https://example.org/a.git", "rev2", 150, Instant.fromEpochSeconds(4000)),
                vcsEntry("https://example.org/b.git", "rev1", 300, Instant.fromEpochSeconds(1000)),
                artifactEntry("https://example.org/c.zip", "0123abcd", 200, null),
                StorageEntryInfo(Key("unknown"), 50, contentType = null, lastModified = Instant.fromEpochSeconds(2000))
            ).map { it.toProvenanceFile() }

            val statistics = computeStatistics(files, snapshotTime)

            with(statistics) {
                this.snapshotTime shouldBe snapshotTime
                totalCount shouldBe 5
                totalSizeInBytes shouldBe 800
                averageSizeInBytes shouldBe 160
                largest.shouldNotBeNull().url shouldBe "https://example.org/b.git"
                oldestLastModified shouldBe Instant.fromEpochSeconds(1000)
                newestLastModified shouldBe Instant.fromEpochSeconds(4000)
                byKind should containExactly(
                    ProvenanceFileKindStatistics(ProvenanceFileKind.SOURCE_ARTIFACT, 1, 200),
                    ProvenanceFileKindStatistics(ProvenanceFileKind.VCS, 3, 550),
                    ProvenanceFileKindStatistics(ProvenanceFileKind.UNKNOWN, 1, 50)
                )
                topUrls should containExactly(
                    ProvenanceFileUrlStatistics("https://example.org/b.git", 1, 300),
                    ProvenanceFileUrlStatistics("https://example.org/a.git", 2, 250),
                    ProvenanceFileUrlStatistics("https://example.org/c.zip", 1, 200)
                )
            }
        }

        "handle an empty list of files" {
            val statistics = computeStatistics(emptyList(), snapshotTime)

            with(statistics) {
                totalCount shouldBe 0
                totalSizeInBytes shouldBe 0
                averageSizeInBytes shouldBe 0
                largest should beNull()
                oldestLastModified should beNull()
                newestLastModified should beNull()
                byKind shouldBe emptyList()
                topUrls shouldBe emptyList()
            }
        }

        "limit the number of top URLs" {
            val files = (1..TOP_URLS_COUNT + 5).map { index ->
                vcsEntry("https://example.org/repo$index.git", "rev", index.toLong()).toProvenanceFile()
            }

            val statistics = computeStatistics(files, snapshotTime)

            statistics.topUrls shouldHaveSize TOP_URLS_COUNT
            statistics.topUrls.first().url shouldBe "https://example.org/repo${TOP_URLS_COUNT + 5}.git"
        }
    }
})
