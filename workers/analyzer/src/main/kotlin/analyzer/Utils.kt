/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.workers.common.mapToModel

import org.ossreviewtoolkit.model.Identifier as OrtIdentifier

/**
 * Helper function to find the shortest dependency path across scopes for each package identifier.
 */
fun getIdentifierToShortestPathsMap(
    projectIdentifier: Identifier,
    pathsByScopeMap: Map<String, Map<OrtIdentifier, List<OrtIdentifier>>>
): Map<Identifier, ShortestDependencyPath> {
    val map = mutableMapOf<Identifier, ShortestDependencyPath>()

    pathsByScopeMap.forEach { (scope, pathsMap) ->
        pathsMap.forEach { (pkgIdentifier, pathList) ->
            val existingEntry = map[pkgIdentifier.mapToModel()]

            if (existingEntry == null || existingEntry.path.size > pathList.size) {
                map[pkgIdentifier.mapToModel()] =
                    ShortestDependencyPath(projectIdentifier, scope, pathList.map { it.mapToModel() })
            }
        }
    }

    return map
}
