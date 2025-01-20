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
import java.nio.file.Path
import java.util.ServiceLoader

import kotlin.io.path.createTempDirectory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path as ConfigPath
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively

import org.slf4j.LoggerFactory

/**
 * A class allowing the download of log files via a managed [LogFileProvider].
 *
 * On instantiation, the class obtains the name of the desired [LogFileProvider] from the given configuration and then
 * tries to load it from the classpath. Requests to download log files are then delegated to this provider.
 *
 * The API offered by this class supports obtaining multiple log files for a given ORT run at once. The files are
 * obtained from the managed provider and added to a Zip archive, which is then returned to the caller.
 */
class LogFileService private constructor(
    /** The underlying [LogFileProvider]. */
    private val provider: LogFileProvider,

    /** The path where to create temporary files. */
    private val tempDir: Path?
) {
    companion object {
        /** The name of the section in the configuration file that is read by this class. */
        const val LOG_FILE_SERVICE_SECTION = "logFileService"

        /** The name of the configuration property defining the name of the log file provider implementation. */
        private const val PROVIDER_TYPE_PROPERTY = "name"

        /** The service loader for loading log file providers dynamically. */
        private val PROVIDER_LOADER = ServiceLoader.load(LogFileProviderFactory::class.java)

        private val logger = LoggerFactory.getLogger(LogFileService::class.java)

        /**
         * Return an initialized [LogFileService] instance based on the given [configManager]. Optionally, a
         * [directory for temporary file operations][tempDir] can be provided; if this is *null*, use the default
         * temporary directory instead.
         */
        fun create(configManager: ConfigManager, tempDir: Path? = null): LogFileService {
            val subConfigManager = runCatching {
                configManager.subConfig(ConfigPath(LOG_FILE_SERVICE_SECTION))
            }.getOrElse { exception ->
                throw LogFileServiceException(
                    "Missing '$LOG_FILE_SERVICE_SECTION' section in configuration.",
                    exception
                )
            }

            val providerType = subConfigManager.getStringOrNull(PROVIDER_TYPE_PROPERTY)
                ?: throw LogFileServiceException(
                    "Missing '$PROVIDER_TYPE_PROPERTY' property in '$LOG_FILE_SERVICE_SECTION' section.",
                    null
                )
            logger.info("Creating LogFileProvider of type '{}'.", providerType)

            val providerFactory = PROVIDER_LOADER.find { it.name == providerType }
                ?: throw LogFileServiceException("LogFileProvider '$providerType' cannot be resolved.", null)
            val provider = providerFactory.createProvider(subConfigManager)

            return LogFileService(provider, tempDir)
        }

        /**
         * Generate the name of a log file based on the given [source].
         */
        private fun logFileName(source: LogSource): String = "${source.name.lowercase()}.log"
    }

    /**
     * Create an archive file containing the logs for the given [ortRunId] for the specified [sources] and [level].
     * Assume that the log data is in the given time range between [startTime] and [endTime]. Note that it is in the
     * responsibility of the caller to delete the archive file when it is no longer needed.
     */
    suspend fun createLogFilesArchive(
        ortRunId: Long,
        sources: Set<LogSource>,
        level: LogLevel,
        startTime: Instant,
        endTime: Instant
    ): File {
        val downloadDir = createTempDirectory(tempDir, "log-download").toFile()
        try {
            downloadLogFiles(ortRunId, sources, level, startTime, endTime, downloadDir)

            val archiveFile = kotlin.io.path.createTempFile(tempDir, "logs", ".zip").toFile()
            return downloadDir.packZip(archiveFile, overwrite = true, fileFilter = { it.length() > 0 })
        } finally {
            downloadDir.safeDeleteRecursively()
        }
    }

    /**
     * Download the log files for the given [ortRunId] and [sources] with the specified parameters to the given
     * [targetDir] using the managed provider. This is done asynchronously.
     */
    private suspend fun downloadLogFiles(
        ortRunId: Long,
        sources: Set<LogSource>,
        level: LogLevel,
        startTime: Instant,
        endTime: Instant,
        targetDir: File
    ) {
        val levels = LogLevel.levelOrHigher(level)

        withContext(Dispatchers.IO) {
            sources.forEach { source ->
                launch { downloadLogFile(ortRunId, source, levels, startTime, endTime, targetDir) }
            }
        }
    }

    /**
     * Download a single log file for the given [ortRunId] and [source] with the given [levels] in the time range
     * defined by [startTime] and [endTime] to the given [targetDir].
     */
    private suspend fun downloadLogFile(
        ortRunId: Long,
        source: LogSource,
        levels: Set<LogLevel>,
        startTime: Instant,
        endTime: Instant,
        targetDir: File
    ) {
        logger.info("Downloading log file for {} for ORT run {}.", source.name, ortRunId)

        @Suppress("TooGenericExceptionCaught")
        try {
            val logFile =
                provider.downloadLogFile(ortRunId, source, levels, startTime, endTime, targetDir, logFileName(source))

            logger.debug(
                "Log file for {} for ORT run {} was downloaded to {}.",
                source.name,
                ortRunId,
                logFile.absolutePath
            )
        } catch (e: Exception) {
            throw LogFileServiceException("Download of log file for $source for ORT run $ortRunId failed.", e)
        }
    }
}

/**
 * A specialized exception class for reporting errors related to the [LogFileService]. The service catches exceptions
 * thrown by the underlying [LogFileProvider] and wraps them into instances of this class.
 */
class LogFileServiceException(message: String, cause: Throwable?) : Exception(message, cause)
