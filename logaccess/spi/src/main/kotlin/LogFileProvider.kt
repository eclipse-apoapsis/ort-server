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

package org.eclipse.apoapsis.ortserver.logaccess

import java.io.File

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

/**
 * The Service Provider Interface for the log file provider abstraction.
 *
 * This interface allows downloading the log files for the single workers of a specific ORT run.
 */
interface LogFileProvider {
    /**
     * Download a log file for the given [source] of the specified [ortRunId]. Include only log statements of the
     * provided [levels]. Obtain log data between the given [startTime] and [endTime]. The time range is provided
     * by the caller in case it is needed by a concrete implementation. Store the results in the given [directory] in
     * a file with the given [fileName].
     */
    suspend fun downloadLogFile(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        directory: File,
        fileName: String
    ): File
}
