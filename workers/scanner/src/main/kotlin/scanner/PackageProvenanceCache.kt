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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import org.eclipse.apoapsis.ortserver.dao.tables.NestedProvenanceDao
import org.eclipse.apoapsis.ortserver.dao.tables.PackageProvenanceDao

import org.ossreviewtoolkit.model.RepositoryProvenance

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(PackageProvenanceCache::class.java)

/**
 * A class to store the package [RepositoryProvenance]s and their database IDs that were resolved by the ORT scanner.
 * These can be used to associate them with [NestedProvenanceDao]s.
 */
class PackageProvenanceCache {
    /**
     * A map of [RepositoryProvenance]s associated with the [Long] database ids of the corresponding
     * [PackageProvenanceDao]s.
     */
    private val packageProvenances = mutableMapOf<RepositoryProvenance, MutableList<Long>>()

    /**
     * A map with root [RepositoryProvenance]s and the [Long] database ID of the [NestedProvenanceDao] that has been
     * resolved for this provenance.
     */
    private val nestedProvenances = mutableMapOf<RepositoryProvenance, Long>()

    /**
     * A map for storing [RepositoryProvenance]s that still need to be assigned to a nested provenance. The assignment
     * can only happen after the nested provenance becomes available.
     */
    private val pendingNestedAssignments = mutableMapOf<RepositoryProvenance, MutableList<Long>>()

    /** A mutex to guard the state of the cache against concurrent access. */
    private val mutex = Mutex()

    /**
     * Return the [Long] database IDs of the [PackageProvenanceDao]s that belong to the provided [provenance].
     * In case there are multiple root projects in a repository, each of them has its own provenance; therefore,
     * multiple results can be returned.
     */
    suspend fun get(provenance: RepositoryProvenance): List<Long> = mutex.withLock {
        logger.debug("Querying provenance {}, result is {}.", provenance, packageProvenances[provenance])
        return packageProvenances[provenance].orEmpty()
    }

    /**
     * Put the provided [provenance] and [Long] database ID of the [PackageProvenanceDao] that belongs to it into the
     * cache and return the ID of a [NestedProvenanceDao] that should be assigned to this [PackageProvenanceDao]. If
     * there is no nested provenance, return *null* instead.
     */
    suspend fun putAndGetNestedProvenance(provenance: RepositoryProvenance, id: Long): Long? = mutex.withLock {
        logger.debug("Storing provenance {} for ID {}.", provenance, id)
        packageProvenances.getOrPut(provenance) { mutableListOf() } += id

        if (!provenance.isRootProvenance()) {
            pendingNestedAssignments.getOrPut(provenance.rootProvenance()) { mutableListOf() } += id
        }

        nestedProvenances[provenance.rootProvenance()]
    }

    /**
     * Put the provided [provenance] and [Long] database ID of the [NestedProvenanceDao] assigned to it into the cache
     * and return a collection with IDs of [PackageProvenanceDao]s that need to be associated with this nested
     * provenance. This is needed in case the nested provenance is resolved after the provenances have been added to
     * the cache.
     */
    suspend fun putNestedProvenance(provenance: RepositoryProvenance, id: Long): List<Long> = mutex.withLock {
        require(provenance.vcsInfo.path.isEmpty()) {
            "A nested provenance can only be assigned to the repository root."
        }

        logger.debug(
            "Storing nested provenance ID {} for {}. Pending assignments: {}",
            id,
            provenance,
            pendingNestedAssignments[provenance]
        )

        nestedProvenances[provenance] = id
        pendingNestedAssignments.getOrDefault(provenance, emptyList())
    }
}

/**
 * Return a flag whether this [RepositoryProvenance] is a root provenance. This means that it does not reference a
 * submodule in the VCS repository.
 */
private fun RepositoryProvenance.isRootProvenance(): Boolean = vcsInfo.path.isEmpty()

/**
 * Return a [RepositoryProvenance] that points to the root VCS of this provenance.
 */
private fun RepositoryProvenance.rootProvenance(): RepositoryProvenance =
    takeIf { isRootProvenance() } ?: copy(vcsInfo = vcsInfo.copy(path = ""))
