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

package org.eclipse.apoapsis.ortserver.workers.common

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.model.config.Resolutions

class ExtensionsTest : WordSpec({
    data class ConfigClass(val name: String, val value: String)

    val path = "path"
    val defaultPath = "defaultPath"
    val fallbackString = "fallback"
    val fallbackValue = ConfigClass(name = "fallback", value = "value")

    val configFile = ConfigClass("config", "value")

    val configFileYaml = """
        name: "config"
        value: "value"
    """.trimIndent()

    val invalidConfigFileYaml = "invalid"

    val configException = ConfigException("message")

    "mapOptions" should {
        "apply the transform to all option entries" {
            val pluginConfigs = mapOf(
                "plugin1" to ResolvablePluginConfig(
                    options = mapOf("key1" to "value1", "key2" to "value2"),
                    secrets = emptyMap()
                ),
                "plugin2" to ResolvablePluginConfig(
                    options = mapOf("key3" to "value3", "key4" to "value4"),
                    secrets = emptyMap()
                )
            )

            pluginConfigs.mapOptions { it.key + it.value } should containExactly(
                "plugin1" to ResolvablePluginConfig(
                    options = mapOf("key1" to "key1value1", "key2" to "key2value2"),
                    secrets = emptyMap()
                ),
                "plugin2" to ResolvablePluginConfig(
                    options = mapOf("key3" to "key3value3", "key4" to "key4value4"),
                    secrets = emptyMap()
                )
            )
        }

        "not apply the transform to the secrets" {
            val pluginConfigs = mapOf(
                "plugin1" to ResolvablePluginConfig(
                    options = emptyMap(),
                    secrets = mapOf(
                        "key1" to ResolvableSecret("value1", SecretSource.ADMIN),
                        "key2" to ResolvableSecret("value2", SecretSource.ADMIN)
                    )
                )
            )

            pluginConfigs.mapOptions { it.key + it.value } should containExactly(
                "plugin1" to ResolvablePluginConfig(
                    options = emptyMap(),
                    secrets = mapOf(
                        "key1" to ResolvableSecret("value1", SecretSource.ADMIN),
                        "key2" to ResolvableSecret("value2", SecretSource.ADMIN)
                    )
                )
            )
        }
    }

    "readConfigFileValueWithDefault" should {
        "deserialize the file at path if path is not null" {
            val context = Context("resolvedContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(path)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFileValueWithDefault(path, defaultPath, fallbackValue, context) shouldBe configFile
        }

        "throw an exception if the file at path cannot be read" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } throws configException
            }

            shouldThrow<ConfigException> {
                configManager.readConfigFileValueWithDefault(path, defaultPath, fallbackValue, null)
            } shouldBe configException
        }

        "throw an exception if the file at path cannot be deserialized" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } returns invalidConfigFileYaml.byteInputStream()
            }

            shouldThrow<IOException> {
                configManager.readConfigFileValueWithDefault(path, defaultPath, fallbackValue, null)
            }
        }

        "deserialize the file at the default path if path is null" {
            val context = Context("theContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(defaultPath)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFileValueWithDefault(null, defaultPath, fallbackValue, context) shouldBe configFile
        }

        "return the fallback value if the file at default path cannot be read" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(defaultPath)) } throws configException
            }

            configManager.readConfigFileValueWithDefault(null, defaultPath, fallbackValue, null) shouldBe
                    fallbackValue
        }

        "throw an exception if the file at default path cannot be deserialized" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(defaultPath)) } returns invalidConfigFileYaml.byteInputStream()
            }

            shouldThrow<IOException> {
                configManager.readConfigFileValueWithDefault(null, defaultPath, fallbackValue, null) shouldBe
                        fallbackValue
            }
        }
    }

    "readConfigFileValue" should {
        "deserialize the config file" {
            val context = Context("myConfigContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(path)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFileValue<ConfigClass>(path, context) shouldBe configFile
        }

        "call the exception handler if a ConfigException occurs" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } throws configException
            }

            var capturedException: ConfigException? = null
            configManager.readConfigFileValue("path", null) { capturedException = it }

            capturedException shouldBe configException
        }

        "throw an exception if the config file cannot be deserialized" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } returns invalidConfigFileYaml.byteInputStream()
            }

            shouldThrow<IOException> {
                configManager.readConfigFileValue<ConfigClass>("path", null) shouldBe configFile
            }
        }
    }

    "readConfigFileWithDefault" should {
        "read the file at path if path is not null" {
            val context = Context("resolvedContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(path)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFileWithDefault(
                path,
                defaultPath,
                fallbackString,
                context
            ) shouldBe configFileYaml
        }

        "throw an exception if the file at path cannot be read" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } throws configException
            }

            shouldThrow<ConfigException> {
                configManager.readConfigFileWithDefault(path, defaultPath, fallbackString, null)
            } shouldBe configException
        }

        "read the file at the default path if path is null" {
            val context = Context("theContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(defaultPath)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFileWithDefault(
                null,
                defaultPath,
                fallbackString,
                context
            ) shouldBe configFileYaml
        }

        "return the fallback value if the file at default path cannot be read" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(defaultPath)) } throws configException
            }

            configManager.readConfigFileWithDefault(
                null,
                defaultPath,
                fallbackString,
                null
            ) shouldBe fallbackString
        }
    }

    "readConfigFile" should {
        "read the config file" {
            val context = Context("myConfigContext")

            val configManager = mockk<ConfigManager> {
                every { getFile(context, Path(path)) } returns configFileYaml.byteInputStream()
            }

            configManager.readConfigFile(path, context) shouldBe configFileYaml
        }

        "call the exception handler if a ConfigException occurs" {
            val configManager = mockk<ConfigManager> {
                every { getFile(any(), Path(path)) } throws configException
            }

            var capturedException: ConfigException? = null
            configManager.readConfigFile("path", null) {
                capturedException = it
                ""
            }

            capturedException shouldBe configException
        }
    }

    "resolvedConfigurationContext" should {
        "return null if no resolved context is available" {
            val ortRun = mockk<OrtRun> {
                every { resolvedJobConfigContext } returns null
            }
            val workerContext = mockk<WorkerContext> {
                every { this@mockk.ortRun } returns ortRun
            }

            workerContext.resolvedConfigurationContext should beNull()
        }

        "return the resolved context if it is available" {
            val resolvedContext = "theResolvedContext"
            val ortRun = mockk<OrtRun> {
                every { resolvedJobConfigContext } returns resolvedContext
            }
            val workerContext = mockk<WorkerContext> {
                every { this@mockk.ortRun } returns ortRun
            }

            workerContext.resolvedConfigurationContext shouldBe Context(resolvedContext)
        }
    }

    "createResolutionProvider" should {
        "merge repository config and global resolutions" {
            val repoIssueMessage = "repo issue"
            val globalIssueMessage = "global issue"

            val globalResolutionsYaml = """
                ---
                issues:
                  - message: "$globalIssueMessage"
                    reason: "CANT_FIX_ISSUE"
                    comment: "Global resolution."
            """.trimIndent()

            val ortRun = mockk<OrtRun> {
                every { resolvedJobConfigContext } returns null
                every { organizationId } returns 1L
                every { resolvedJobConfigs } returns null
            }

            val adminConfigService = mockk<AdminConfigService> {
                every { loadAdminConfig(any(), any()) } returns AdminConfig.DEFAULT
            }

            val workerContext = mockk<WorkerContext> {
                every { this@mockk.ortRun } returns ortRun
                every { this@mockk.configManager } returns mockk<ConfigManager> {
                    every { getFile(any(), any()) } returns ByteArrayInputStream(globalResolutionsYaml.toByteArray())
                }
            }

            val ortResult = OrtResult.EMPTY.copy(
                repository = OrtResult.EMPTY.repository.copy(
                    config = OrtResult.EMPTY.repository.config.copy(
                        resolutions = Resolutions(
                            issues = listOf(
                                IssueResolution(
                                    message = repoIssueMessage,
                                    reason = IssueResolutionReason.CANT_FIX_ISSUE,
                                    comment = "Repo resolution."
                                )
                            )
                        )
                    )
                )
            )

            val resolutionProvider = workerContext.createResolutionProvider(
                RepositoryId(ortRun.repositoryId),
                ortResult,
                adminConfigService
            )

            val repoIssue = Issue(
                timestamp = Instant.now(),
                source = "test",
                message = repoIssueMessage,
                severity = Severity.ERROR
            )
            val globalIssue = Issue(
                timestamp = Instant.now(),
                source = "test",
                message = globalIssueMessage,
                severity = Severity.ERROR
            )
            val unmatchedIssue = Issue(
                timestamp = Instant.now(),
                source = "test",
                message = "unmatched issue",
                severity = Severity.ERROR
            )

            resolutionProvider.getResolutionsFor(repoIssue).shouldNotBeEmpty()
            resolutionProvider.getResolutionsFor(globalIssue).shouldNotBeEmpty()
            resolutionProvider.getResolutionsFor(unmatchedIssue).shouldBeEmpty()
        }
    }
})
