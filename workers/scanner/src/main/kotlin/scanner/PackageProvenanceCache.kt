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

import java.util.concurrent.ConcurrentHashMap

import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.server.dao.tables.provenance.NestedProvenanceDao
import org.ossreviewtoolkit.server.dao.tables.provenance.PackageProvenanceDao

/**
 * A class to store the package [RepositoryProvenance]s and their database IDs that were resolved by the ORT scanner.
 * These can be used to associate them with [NestedProvenanceDao]s.
 */
class PackageProvenanceCache {
    /**
     * A map of [RepositoryProvenance]s associated with the [Long] database id of the corresponding
     * [PackageProvenanceDao].
     */
    private val packageProvenances = ConcurrentHashMap<RepositoryProvenance, Long>()

    /**
     * Return the [Long] database ID of the [PackageProvenanceDao] that belongs to the provided [provenance].
     */
    fun get(provenance: RepositoryProvenance) = packageProvenances[provenance]

    /**
     * Put the provided [provenance] and [Long] database ID of the [PackageProvenanceDao] that belongs to it into the
     * cache.
     */
    fun put(provenance: RepositoryProvenance, id: Long) {
        packageProvenances[provenance] = id
    }
}
