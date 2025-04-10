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

package org.eclipse.apoapsis.ortserver.model.runs

import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData

/**
 * A data class representing a package, and the shortest dependency path that the package is found in (relative to a
 * project found in a run).
 */
data class PackageRunData(
    /** A package. */
    val pkg: Package,

    /** Package ID */
    val pkgId: Long,

    /** The shortest dependency path for the package. */
    val shortestDependencyPaths: List<ShortestDependencyPath>,

    /** The curations for the package. */
    val curations: List<PackageCurationData> = emptyList()
)
