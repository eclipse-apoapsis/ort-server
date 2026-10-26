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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

/**
 * The [includes] and [excludes] for paths requested for an ORT run.
 */
@Serializable
data class PathIncludesExcludes(
    /** The paths to include. */
    val includes: List<PathInclude> = emptyList(),

    /** The paths to exclude. */
    val excludes: List<PathExclude> = emptyList()
)

/**
 * The [includes] and [excludes] for scopes requested for an ORT run.
 */
@Serializable
data class ScopeIncludesExcludes(
    /** The scopes to include. */
    val includes: List<ScopeInclude> = emptyList(),

    /** The scopes to exclude. */
    val excludes: List<ScopeExclude> = emptyList()
)

/**
 * A pattern defining paths that should be included.
 */
@Serializable
data class PathInclude(
    /** A glob pattern specifying the paths to include. */
    val pattern: String,

    /** The reason for including the paths. */
    val reason: PathIncludeReason,

    /** An optional comment providing further details about the inclusion. */
    val comment: String? = null
)

/**
 * A pattern defining paths that should be excluded.
 */
@Serializable
data class PathExclude(
    /** A glob pattern specifying the paths to exclude. */
    val pattern: String,

    /** The reason for excluding the paths. */
    val reason: PathExcludeReason,

    /** An optional comment providing further details about the exclusion. */
    val comment: String? = null
)

/**
 * A pattern defining scopes that should be included.
 */
@Serializable
data class ScopeInclude(
    /** A regular expression specifying the scopes to include. */
    val pattern: String,

    /** The reason for including the scopes. */
    val reason: ScopeIncludeReason,

    /** An optional comment providing further details about the inclusion. */
    val comment: String? = null
)

/**
 * A pattern defining scopes that should be excluded.
 */
@Serializable
data class ScopeExclude(
    /** A regular expression specifying the scopes to exclude. */
    val pattern: String,

    /** The reason for excluding the scopes. */
    val reason: ScopeExcludeReason,

    /** An optional comment providing further details about the exclusion. */
    val comment: String? = null
)

/**
 * The possible reasons for including a path, matching those of ORT.
 */
enum class PathIncludeReason {
    SOURCE_OF,
    OTHER
}

/**
 * The possible reasons for excluding a path, matching those of ORT.
 */
enum class PathExcludeReason {
    BUILD_TOOL_OF,
    DATA_FILE_OF,
    DOCUMENTATION_OF,
    EXAMPLE_OF,
    OPTIONAL_COMPONENT_OF,
    OTHER,
    PROVIDED_BY,
    TEST_OF,
    TEST_TOOL_OF
}

/**
 * The possible reasons for including a scope, matching those of ORT.
 */
enum class ScopeIncludeReason {
    SOURCE_OF,
    OTHER
}

/**
 * The possible reasons for excluding a scope, matching those of ORT.
 */
enum class ScopeExcludeReason {
    BUILD_TOOL_OF,
    BUILD_DEPENDENCY_OF,
    DEV_DEPENDENCY_OF,
    DOCUMENTATION_DEPENDENCY_OF,
    PROVIDED_BY,
    PROVIDED_DEPENDENCY_OF,
    TEST_TOOL_OF,
    TEST_DEPENDENCY_OF,
    RUNTIME_DEPENDENCY_OF
}
