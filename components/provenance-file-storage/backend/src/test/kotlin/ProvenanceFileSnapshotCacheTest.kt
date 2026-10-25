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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield

import org.eclipse.apoapsis.ortserver.storage.Key

class ProvenanceFileSnapshotCacheTest : WordSpec({
    val fileA = vcsEntry("https://example.org/a.git", "rev", 100).toProvenanceFile()
    val fileB = vcsEntry("https://example.org/b.git", "rev", 200).toProvenanceFile()

    /** A helper class that provides a cache with a fixed clock and counts the loads of the files. */
    class Fixture(var files: List<ProvenanceFile> = listOf(fileA, fileB)) {
        val clock = FixedClock(Instant.fromEpochSeconds(1000))
        var loadCount = 0
        val cache = ProvenanceFileSnapshotCache(clock, ttl = 5.minutes) {
            loadCount++
            files
        }
    }

    "get" should {
        "load the files on first access" {
            val fixture = Fixture()

            val snapshot = fixture.cache.get()

            snapshot.files should containExactly(fileA, fileB)
            snapshot.statistics.snapshotTime shouldBe fixture.clock.now
            snapshot.statistics.totalSizeInBytes shouldBe 300
            fixture.loadCount shouldBe 1
        }

        "reuse the snapshot within the TTL" {
            val fixture = Fixture()
            fixture.cache.get()

            fixture.clock.now += 4.minutes
            fixture.cache.get()

            fixture.loadCount shouldBe 1
        }

        "reload the files after the TTL" {
            val fixture = Fixture()
            fixture.cache.get()

            fixture.files = listOf(fileA)
            fixture.clock.now += 5.minutes
            val snapshot = fixture.cache.get()

            snapshot.files should containExactly(fileA)
            fixture.loadCount shouldBe 2
        }

        "reload the files if a refresh is requested" {
            val fixture = Fixture()
            fixture.cache.get()

            fixture.clock.now += 1.seconds
            fixture.cache.get(refresh = true)

            fixture.loadCount shouldBe 2
        }

        "share a single load between concurrent callers" {
            val loadStarted = CompletableDeferred<Unit>()
            val loadCanFinish = CompletableDeferred<Unit>()
            var loadCount = 0
            val cache = ProvenanceFileSnapshotCache(FixedClock(Instant.fromEpochSeconds(1000))) {
                loadCount++
                loadStarted.complete(Unit)
                loadCanFinish.await()
                listOf(fileA)
            }

            val snapshots = coroutineScope {
                val first = async { cache.get() }
                loadStarted.await()
                val others = (1..3).map { async { cache.get() } }
                yield()
                loadCanFinish.complete(Unit)
                (listOf(first) + others).awaitAll()
            }

            loadCount shouldBe 1
            snapshots.distinct().shouldBeSingleton()
        }
    }

    "remove" should {
        "remove the file from the snapshot and update the statistics" {
            val fixture = Fixture()
            val loadTime = fixture.cache.get().statistics.snapshotTime

            fixture.cache.remove(Key(fileA.key))
            val snapshot = fixture.cache.get()

            snapshot.files should containExactly(fileB)
            snapshot.statistics.totalCount shouldBe 1
            snapshot.statistics.totalSizeInBytes shouldBe 200
            snapshot.statistics.snapshotTime shouldBe loadTime
            fixture.loadCount shouldBe 1
        }

        "do nothing if no snapshot has been loaded" {
            val fixture = Fixture()

            fixture.cache.remove(Key(fileA.key))

            fixture.loadCount shouldBe 0
        }
    }
})
