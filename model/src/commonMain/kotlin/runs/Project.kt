/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue

data class Project(
    val identifier: Identifier,
    val cpe: String? = null,
    val definitionFilePath: String,
    val authors: Set<String>,
    val declaredLicenses: Set<String>,
    val processedDeclaredLicense: ProcessedDeclaredLicense,
    val vcs: VcsInfo,
    val vcsProcessed: VcsInfo,
    val description: String,
    val homepageUrl: String,
    val scopeNames: Set<String>
)

/** Object containing values to filter a project listing with. */
data class ProjectFilters(
    /** Substring filter for the full project identifier. Null if not set. */
    val identifier: FilterOperatorAndValue<String>? = null,

    /** Set of displayed declared license values to filter with. Null if not set. */
    val declaredLicense: FilterOperatorAndValue<Set<String>>? = null,

    /** Substring filter for the definition file path. Null if not set. */
    val definitionFilePath: FilterOperatorAndValue<String>? = null
)
