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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.sequences.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.mockk

import java.io.File
import java.util.EnumSet

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource

import org.ossreviewtoolkit.utils.common.unpack

class LogFileServiceTest : WordSpec({
    "create" should {
        "throw an exception if the configuration section is missing" {
            val cm = createConfigManager(ConfigFactory.empty())

            shouldThrow<LogFileServiceException> {
                LogFileService.create(cm)
            }
        }

        "throw an exception if no LogFileProvider is configured" {
            val config = ConfigFactory.parseMap(mapOf(LogFileService.LOG_FILE_SERVICE_SECTION to mapOf("p" to "v")))
            val cm = createConfigManager(config)

            shouldThrow<LogFileServiceException> {
                LogFileService.create(cm)
            }
        }

        "throw an exception if the configured LogFileProvider cannot be resolved" {
            val providerName = "nonExistingLogFileProvider"
            val configMap = mapOf(LogFileService.LOG_FILE_SERVICE_SECTION to mapOf("name" to providerName))
            val config = ConfigFactory.parseMap(configMap)
            val cm = createConfigManager(config)

            val exception = shouldThrow<LogFileServiceException> {
                LogFileService.create(cm)
            }

            exception.message shouldContain providerName
        }
    }

    "createLogFilesArchive" should {
        "create a correct archive with log files" {
            val temp = tempdir()
            val archiveDir = tempdir()

            val logFileService = LogFileService.create(configManager, archiveDir.toPath())

            LogFileProviderFactoryForTesting.addLogFile(
                createLogFileCriteria(LogSource.CONFIG),
                temp.createLogFile(LogSource.CONFIG)
            )
            LogFileProviderFactoryForTesting.addLogFile(
                createLogFileCriteria(LogSource.ANALYZER),
                temp.createLogFile(LogSource.ANALYZER)
            )
            LogFileProviderFactoryForTesting.addLogFile(
                createLogFileCriteria(LogSource.REPORTER),
                temp.createLogFile(LogSource.REPORTER)
            )

            val logArchive = logFileService.createLogFilesArchive(
                RUN_ID,
                EnumSet.of(LogSource.CONFIG, LogSource.ANALYZER, LogSource.REPORTER),
                LogLevel.WARN,
                START_TIME,
                END_TIME
            )

            logArchive.parentFile shouldBe archiveDir
            val archiveContentDir = temp.resolve("archive")
            logArchive.unpack(archiveContentDir)

            val expectedLogFiles = listOf("config.log", "analyzer.log", "reporter.log")
            archiveContentDir.walk().maxDepth(1).filter { it.isFile }
                .mapTo(mutableListOf()) { it.name } shouldContainExactlyInAnyOrder expectedLogFiles

            fun checkLogFile(name: String, source: LogSource) {
                val logFile = archiveContentDir.resolve(name)
                logFile.readText() shouldBe generateLog(source)
            }

            checkLogFile("config.log", LogSource.CONFIG)
            checkLogFile("analyzer.log", LogSource.ANALYZER)
            checkLogFile("reporter.log", LogSource.REPORTER)
        }

        "clean up temporary files, even in case of an error" {
            val temp = tempdir()
            val archiveDir = tempdir()

            val logFileService = LogFileService.create(configManager, archiveDir.toPath())

            LogFileProviderFactoryForTesting.addLogFile(
                createLogFileCriteria(LogSource.CONFIG),
                temp.createLogFile(LogSource.CONFIG)
            )

            shouldThrow<LogFileServiceException> {
                logFileService.createLogFilesArchive(
                    RUN_ID,
                    EnumSet.of(LogSource.CONFIG, LogSource.SCANNER),
                    LogLevel.WARN,
                    START_TIME,
                    END_TIME
                )
            }

            archiveDir.walk().maxDepth(1).filter { it.isFile } should beEmpty()
        }
    }
})

private const val RUN_ID = 20231114L
private val START_TIME = Instant.parse("2023-11-14T15:16:11.05Z")
private val END_TIME = Instant.parse("2023-11-14T15:26:59.94Z")

/** A test [ConfigManager] instance. */
private val configManager = createTestConfigManager()

/**
 * Create a [ConfigManager] object with a configuration that selects the test log file provider.
 */
private fun createTestConfigManager(): ConfigManager {
    val configMap = mapOf(
        LogFileService.LOG_FILE_SERVICE_SECTION to mapOf(
            "name" to LogFileProviderFactoryForTesting.NAME
        )
    )
    val config = ConfigFactory.parseMap(configMap)

    return createConfigManager(config)
}

/**
 * Create a [ConfigManager] for testing that wraps the given [config].
 */
private fun createConfigManager(config: Config) =
    ConfigManager(config, { mockk() }, { mockk() }, false)

/**
 * Create a [LogFileCriteria] instance for the given [source] and [levels].
 */
private fun createLogFileCriteria(
    source: LogSource,
    levels: Set<LogLevel> = EnumSet.of(LogLevel.ERROR, LogLevel.WARN)
): LogFileCriteria =
    LogFileCriteria(RUN_ID, source, levels, START_TIME, END_TIME)

/**
 * Create a dummy log file under this directory for the given [source].
 */
private fun File.createLogFile(source: LogSource): File {
    return resolve("${RUN_ID}_${source.name}.log").also {
        it.writeText(generateLog(source))
    }
}

/**
 * Return a string with dummy log data for the given [source].
 */
private fun generateLog(source: LogSource): String =
    "Logs for '${source.name}'."
