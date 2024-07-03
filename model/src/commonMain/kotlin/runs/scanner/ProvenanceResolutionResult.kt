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

package org.eclipse.apoapsis.ortserver.model.runs.scanner

import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo

/**
 * This class holds the results of the provenance resolution for the package denoted by [id]. The provenance resolution
 * consists of the root provenance resolution and the nested provenance resolution. The root provenance resolution finds
 * the source code origin of the package. If this is a VCS repository, the nested provenance resolution finds any
 * nested repositories, like Git submodules.
 */
data class ProvenanceResolutionResult(
    /**
     * The identifier of the package.
     */
    val id: Identifier,

    /**
     * The resolved provenance of the package.
     */
    val packageProvenance: KnownProvenance? = null,

    /**
     * The (recursive) sub-repositories of the [packageProvenance]. The keys refer to the paths of the sub-repositories
     * inside the [packageProvenance].
     */
    val subRepositories: Map<String, VcsInfo> = emptyMap(),

    /**
     * An [Issue] describing an error during the resolution of the [packageProvenance], or null if there was no
     * error.
     */
    val packageProvenanceResolutionIssue: Issue? = null,

    /**
     * An [Issue] describing an error during the resolution of the [subRepositories], or null if there was no error.
     */
    val nestedProvenanceResolutionIssue: Issue? = null
) {
    /**
     * Get all [KnownProvenance]s from the [packageProvenance] and [subRepositories].
     */
    fun getProvenances(): Set<KnownProvenance> =
        buildSet {
            packageProvenance?.let { add(it) }
            subRepositories.values.forEach { add(RepositoryProvenance(it, it.revision)) }
        }
}
