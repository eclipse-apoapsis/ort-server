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

package org.eclipse.apoapsis.ortserver.model.resolvedconfiguration

/**
 * Reference data for a curation applied to a package. This is used to associate a package with a specific resolved
 * package curation, identified by the [providerName] and the [curationRank] within that provider.
 */
data class AppliedPackageCurationRef(
    val providerName: String,

    /**
     * The 0-based ordinal position of the curation within its provider's curation list. This corresponds to the
     * `rank` column in the `resolved_package_curations` table and is used to uniquely identify a specific curation
     * within a provider.
     */
    val curationRank: Int
)
