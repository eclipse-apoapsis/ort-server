/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

@Serializable
data class Package(
    val identifier: Identifier,
    val purl: String,
    val cpe: String? = null,
    val authors: Set<String>,
    val declaredLicenses: Set<String>,
    val processedDeclaredLicense: ProcessedDeclaredLicense,
    val description: String,
    val homepageUrl: String,
    val binaryArtifact: RemoteArtifact,
    val sourceArtifact: RemoteArtifact,
    val vcs: VcsInfo,
    val vcsProcessed: VcsInfo,
    val isMetadataOnly: Boolean = false,
    val isModified: Boolean = false,
    val shortestDependencyPaths: List<ShortestDependencyPath>
)

/**
 * Object containing values to filter a packages listing with.
 */
@Serializable
data class PackageFilters(
    /** Substring filter for identifier. Null if not set. */
    val identifier: FilterOperatorAndValue<String>? = null,

    /** Substring filter for purl. Null if not set. */
    val purl: FilterOperatorAndValue<String>? = null,

    /** Set of SPDX license expressions to filter with. Null if not set. */
    val processedDeclaredLicense: FilterOperatorAndValue<Set<String>>? = null
)
