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

import org.eclipse.apoapsis.ortserver.model.runs.repository.Curations
import org.eclipse.apoapsis.ortserver.model.runs.repository.Excludes
import org.eclipse.apoapsis.ortserver.model.runs.repository.Includes
import org.eclipse.apoapsis.ortserver.model.runs.repository.LicenseChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.ProvenanceSnippetChoices
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryAnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.RepositoryConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.repository.Resolutions

/**
 * A repository of [repository configurations][RepositoryConfiguration].
 */
interface RepositoryConfigurationRepository {
    /**
     * Create a repository configuration.
     */
    @Suppress("LongParameterList")
    fun create(
        ortRunId: Long,
        analyzerConfig: RepositoryAnalyzerConfiguration?,
        excludes: Excludes,
        includes: Includes,
        resolutions: Resolutions,
        curations: Curations,
        packageConfigurations: List<PackageConfiguration>,
        licenseChoices: LicenseChoices,
        provenanceSnippetChoices: List<ProvenanceSnippetChoices>
    ): RepositoryConfiguration

    /**
     * Get an analyzer run by [id]. Returns null if the repository configuration is not found.
     */
    fun get(id: Long): RepositoryConfiguration?
}
