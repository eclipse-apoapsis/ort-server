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

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.scanner.ScannerRun

/**
 * A repository of [scanner runs][ScannerRun].
 */
interface ScannerRunRepository {
    /**
     * Create an empty [ScannerRun]. This function is supposed to be called before the ORT scanner is invoked, so that
     * data can be associated to the scanner run while the ORT scanner is running.
     */
    fun create(scannerJobId: Long): ScannerRun

    /**
     * Update the scanner run identified by [id] with the provided [startTime], [endTime], [environment], [config],
     * [scanners], and [issues]. This function can be called only once to finalize a scanner run and throws an exception
     * if it is called multiple times for the same scanner run.
     */
    fun update(
        id: Long,
        startTime: Instant,
        endTime: Instant,
        environment: Environment,
        config: ScannerConfiguration,
        scanners: Map<Identifier, Set<String>>,
        issues: Map<Identifier, Set<Issue>>
    ): ScannerRun

    /**
     * Get a scanner run by [id]. Returns null if the scanner run is not found.
     */
    fun get(id: Long): ScannerRun?

    /**
     * Get a scanner run by [scannerJobId]. Returns null if the scanner run is not found.
     */
    fun getByJobId(scannerJobId: Long): ScannerRun?
}
