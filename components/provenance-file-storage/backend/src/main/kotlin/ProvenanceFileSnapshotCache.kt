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

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.eclipse.apoapsis.ortserver.storage.Key

/** A snapshot of the listing of a storage, consisting of the [files] and the [statistics] computed for them. */
internal class ProvenanceFileSnapshot(
    /** The files contained in the storage. */
    val files: List<ProvenanceFile>,

    /** The statistics about the [files]. */
    val statistics: ProvenanceFileStatistics
)

/**
 * A cache for a [ProvenanceFileSnapshot] of a storage. Listing a storage can be expensive, and the admin UI requests
 * pages and statistics of the same listing repeatedly. Therefore, a snapshot is kept for the given [ttl] before the
 * storage is listed again. Concurrent requests for an expired snapshot share a single reload.
 *
 * The cache is local to the current instance. Changes made by other instances, such as workers storing new files,
 * become visible after at most [ttl], or when a refresh is explicitly requested.
 *
 * The lock of the cache is held while the storage is listed, so removing a file waits for a running reload to finish.
 */
internal class ProvenanceFileSnapshotCache(
    /** The clock used to determine the age of a snapshot. */
    private val clock: Clock,

    /** The time after which a snapshot expires. */
    private val ttl: Duration = DEFAULT_TTL,

    /** The function to load the files from the storage. */
    private val loadFiles: suspend () -> List<ProvenanceFile>
) {
    companion object {
        /** The default time after which a snapshot expires. */
        val DEFAULT_TTL = 5.minutes
    }

    /** The mutex to synchronize the loading and updating of the snapshot. */
    private val mutex = Mutex()

    /** The current snapshot, or *null* if no snapshot has been loaded yet. */
    @Volatile
    private var snapshot: ProvenanceFileSnapshot? = null

    /**
     * Return the current snapshot. Load a new snapshot if there is none yet, if it has expired, or if [refresh] is
     * *true*. In the latter case, a snapshot is only reused if it was loaded after this function was called.
     */
    suspend fun get(refresh: Boolean = false): ProvenanceFileSnapshot {
        val requestTime = clock.now()

        snapshot?.takeIf { !refresh && !it.isExpired() }?.let { return it }

        return mutex.withLock {
            // Another caller may have loaded a new snapshot while this one was waiting for the lock.
            snapshot?.takeIf { if (refresh) it.statistics.snapshotTime >= requestTime else !it.isExpired() }
                ?: load()
        }
    }

    /**
     * Remove the file stored under the given [key] from the current snapshot, if any, and update the statistics
     * accordingly. This makes deletions visible immediately without having to list the storage again.
     */
    suspend fun remove(key: Key) {
        mutex.withLock {
            snapshot = snapshot?.let { current ->
                val files = current.files.filterNot { it.key == key.key }
                ProvenanceFileSnapshot(files, computeStatistics(files, current.statistics.snapshotTime))
            }
        }
    }

    /** Load a new snapshot and store it in this cache. The caller must hold the [mutex]. */
    private suspend fun load(): ProvenanceFileSnapshot {
        val snapshotTime = clock.now()
        val files = loadFiles()

        return ProvenanceFileSnapshot(files, computeStatistics(files, snapshotTime)).also { snapshot = it }
    }

    private fun ProvenanceFileSnapshot.isExpired() = clock.now() - statistics.snapshotTime >= ttl
}
