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
package org.eclipse.apoapsis.ortserver.model.runs.repository

import kotlinx.serialization.Serializable

/**
 * The includes and excludes requested for an ORT run in addition to those from the repository configuration file,
 * grouped by what they match: [paths] or [scopes].
 */
@Serializable
data class RunIncludesExcludes(
    /** The includes and excludes for paths. */
    val paths: PathIncludesExcludes = PathIncludesExcludes(),

    /** The includes and excludes for scopes. */
    val scopes: ScopeIncludesExcludes = ScopeIncludesExcludes()
)

/**
 * The [includes] and [excludes] for paths requested for an ORT run.
 */
@Serializable
data class PathIncludesExcludes(
    val includes: List<PathInclude> = emptyList(),
    val excludes: List<PathExclude> = emptyList()
)

/**
 * The [includes] and [excludes] for scopes requested for an ORT run.
 */
@Serializable
data class ScopeIncludesExcludes(
    val includes: List<ScopeInclude> = emptyList(),
    val excludes: List<ScopeExclude> = emptyList()
)
