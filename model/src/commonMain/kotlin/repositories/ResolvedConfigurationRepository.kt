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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedConfiguration
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedItemsResult
import org.eclipse.apoapsis.ortserver.model.resolvedconfiguration.ResolvedPackageCurations
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration

/**
 * A repository for [ResolvedConfiguration]s.
 */
interface ResolvedConfigurationRepository {
    /**
     * Get a [ResolvedConfiguration] by id. Returns null if the [ResolvedConfiguration] is not found.
     */
    fun get(id: Long): ResolvedConfiguration?

    /**
     * Get the [ResolvedConfiguration] for an [OrtRun] by [ortRunId].
     */
    fun getForOrtRun(ortRunId: Long): ResolvedConfiguration?

    /**
     * Add the provided [packageConfigurations] to the [ResolvedConfiguration] of the [OrtRun] identified by [ortRunId].
     * If there is no [ResolvedConfiguration] for the [OrtRun] it is created.
     */
    fun addPackageConfigurations(ortRunId: Long, packageConfigurations: List<PackageConfiguration>)

    /**
     * Add the provided [packageCurations] to the [ResolvedConfiguration] of the [OrtRun] identified by [ortRunId]. If
     * there is no [ResolvedConfiguration] for the [OrtRun] it is created.
     */
    fun addPackageCurations(ortRunId: Long, packageCurations: List<ResolvedPackageCurations>)

    /**
     * Add the provided [resolvedItems] to the [ResolvedConfiguration] of the [OrtRun] identified by [ortRunId].
     * This stores both:
     * 1. The unique resolutions (issues, rule violations, vulnerabilities) to the resolved configuration
     * 2. The mappings between items and their matching resolutions for efficient statistics queries
     *
     * If there is no [ResolvedConfiguration] for the [OrtRun] it is created.
     */
    fun addResolutions(ortRunId: Long, resolvedItems: ResolvedItemsResult)
}
