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

package org.eclipse.apoapsis.ortserver.model.repositories

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.AnalyzerRun
import org.eclipse.apoapsis.ortserver.model.runs.DependencyGraph
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath

/**
 * A repository of [analyzer runs][AnalyzerRun].
 */
interface AnalyzerRunRepository {
    /**
     * Create an analyzer run.
     */
    fun create(
        analyzerJobId: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: AnalyzerConfiguration,
        projects: Set<Project>,
        packages: Set<Package>,
        issues: List<Issue>,
        dependencyGraphs: Map<String, DependencyGraph>,
        shortestDependencyPaths: Map<Identifier, List<ShortestDependencyPath>> = emptyMap()
    ): AnalyzerRun

    /**
     * Get an analyzer run by [id]. Returns null if the analyzer run is not found.
     */
    fun get(id: Long): AnalyzerRun?

    /**
     * Get an analyzer run by [analyzerJobId]. Returns null if the analyzer run is not found.
     */
    fun getByJobId(analyzerJobId: Long): AnalyzerRun?
}
