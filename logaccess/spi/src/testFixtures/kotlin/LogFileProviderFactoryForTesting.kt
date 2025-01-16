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
import java.util.Collections
import java.util.EnumSet

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

/**
 * An implementation of [LogFileProviderFactory] that can be used to test interactions with log file providers.
 *
 * Via the companion object, static log files can be specified to be returned for specific ORT runs, sources, and
 * parameters. All requests handled by this provider are recorded and can be queried.
 */
class LogFileProviderFactoryForTesting : LogFileProviderFactory {
    companion object {
        /** The name of this test implementation. */
        const val NAME = "LogFileProviderForTesting"

        /** A map with log files and their properties that can be served by this test implementation. */
        private val logFiles = mutableMapOf<LogFileCriteria, File>()

        /** A list for tracking the requests handled by this provider. */
        private val recordedRequests = Collections.synchronizedList(mutableListOf<LogFileCriteria>())

        /**
         * Register a [log file][file] to be returned by this implementation for the given [criteria].
         */
        fun addLogFile(criteria: LogFileCriteria, file: File) {
            logFiles[criteria] = file
        }

        /**
         * Reset the data in this provider implementation. This includes the configured log files and the recorded
         * requests.
         */
        fun reset() {
            logFiles.clear()
            recordedRequests.clear()
        }

        /**
         * Return a list with all the requests for log files that have been handled by this provider implementation.
         * The requests are stored in the order they have been processed. The [reset] function clears this list.
         */
        fun requests(): List<LogFileCriteria> = recordedRequests
    }

    override val name: String = NAME

    override fun createProvider(config: ConfigManager): LogFileProvider {
        return object : LogFileProvider {
            override suspend fun downloadLogFile(
                ortRunId: Long,
                source: LogSource,
                levels: Set<LogLevel>,
                startTime: Instant,
                endTime: Instant,
                directory: File,
                fileName: String
            ): File {
                require(config.getString("name") == NAME) {
                    "Invalid configuration passed to LogFileProviderFactory."
                }

                recordedRequests += LogFileCriteria(ortRunId, source, levels, startTime, endTime)
                val logFile = requireNotNull(
                    logFiles.entries.find { e -> e.key.matches(ortRunId, source, levels, startTime, endTime) }
                ).value

                val target = directory.resolve(fileName)
                return logFile.copyTo(target)
            }
        }
    }
}

/**
 * A data class that is used to specify criteria for log files. Download requests against a provider are matched
 * against the properties defined here; only if a match is found, the log file is returned.
 */
data class LogFileCriteria(
    /** The expected ORT run ID. */
    val ortRunId: Long,

    /** The [LogSource] of the log file. */
    val source: LogSource,

    /** A set with the expected log levels. */
    val levels: Set<LogLevel> = EnumSet.of(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR),

    /**
     * The expected start time for the logs to download. A value of *null* means that this criterion is not checked.
     */
    val startTime: Instant? = null,

    /**
     * The expected end time for the logs to download. A value of *null* means that this criterion is not checked.
     */
    val endTime: Instant? = null
) {
    /**
     * Check whether the properties in this object match the given parameters.
     */
    fun matches(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
    ): Boolean =
        ortRunId == this.ortRunId &&
                source == this.source &&
                levels == this.levels &&
                (this.startTime == null || startTime == this.startTime) &&
                (this.endTime == null || endTime == this.endTime)
}
