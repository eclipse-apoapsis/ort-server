/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.config

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.FileNotFoundException

import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath

import org.ossreviewtoolkit.server.utils.config.getStringOrNull

class ConfigManagerTest : WordSpec({
    "create" should {
        "throw an exception if no provider for config files is specified" {
            val managerMap = mapOf(
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
                "foo" to "bar"
            )
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to managerMap)
            val config = ConfigFactory.parseMap(configMap)

            val exception = shouldThrow<ConfigException> {
                ConfigManager.create(config, Context("someContext"))
            }

            exception.message shouldContain ConfigManager.FILE_PROVIDER_NAME_PROPERTY
        }

        "throw an exception if no provider for config secrets is specified" {
            val managerMap = mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
                "foo" to "bar"
            )
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to managerMap)
            val config = ConfigFactory.parseMap(configMap)

            val exception = shouldThrow<ConfigException> {
                ConfigManager.create(config, Context("someContext"))
            }

            exception.message shouldContain ConfigManager.SECRET_PROVIDER_NAME_PROPERTY
        }

        "throw an exception if no section for the config manager is present" {
            val configMap = mapOf("foo" to "bar")
            val config = ConfigFactory.parseMap(configMap)

            val exception = shouldThrow<ConfigException> {
                ConfigManager.create(config, Context("someContext"))
            }

            exception.message shouldContain ConfigManager.CONFIG_MANAGER_SECTION
        }

        "throw an exception if the file provider cannot be found on the classpath" {
            val providerName = "unknownFileProvider"
            val managerMap = mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to providerName,
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME
            )
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to managerMap)
            val config = ConfigFactory.parseMap(configMap)

            val exception = shouldThrow<ConfigException> {
                ConfigManager.create(config, Context("someContext"))
            }

            exception.message shouldContain providerName
        }

        "throw an exception if the secret provider cannot be found on the classpath" {
            val providerName = "unknownSecretProvider"
            val managerMap = mapOf(
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to providerName,
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME
            )
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to managerMap)
            val config = ConfigFactory.parseMap(configMap)

            val exception = shouldThrow<ConfigException> {
                ConfigManager.create(config, Context("someContext"))
            }

            exception.message shouldContain providerName
        }

        "resolve the context if requested" {
            val manager = createConfigManager(resolveContext = true)

            manager.containsFile(Path("root.txt")) shouldBe true

            manager.context.name shouldStartWith ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX
        }

        "handle exceptions from the provider when resolving the context" {
            shouldThrow<ConfigException> {
                createConfigManager(Context(ConfigFileProviderFactoryForTesting.ERROR_VALUE), resolveContext = true)
            }
        }

        "pass an initialized secret provider to the file provider" {
            val providerMap = createConfigProviderProperties(resolveContext = false) +
                    mapOf(ConfigFileProviderFactoryForTesting.SECRET_PROPERTY to TEST_SECRET_NAME)
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)
            val config = ConfigFactory.parseMap(configMap)

            val configManager = ConfigManager.create(config, testContext())

            configManager.containsFile(Path("somePath")) shouldBe false
        }
    }

    "getFile" should {
        "return a stream for a configuration file" {
            val manager = createConfigManager()

            val fileContent = manager.getFile(Path("root.txt")).use { String(it.readAllBytes()).trim() }

            fileContent shouldBe "Root config file."
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            val exception = shouldThrow<ConfigException> {
                manager.getFile(Path("nonExistingPath"))
            }

            exception.cause should beInstanceOf<FileNotFoundException>()
        }
    }

    "getFileString" should {
        "return the content of a configuration file as string" {
            val manager = createConfigManager()

            val fileContent = manager.getFileAsString(Path("root.txt")).trim()

            fileContent shouldBe "Root config file."
        }

        "handle exceptions while reading the stream" {
            val manager = spyk(createConfigManager())
            every { manager.getFile(any()) } returns mockk()

            // Since an uninitialized mock is returned as stream, it will throw on each method call.
            shouldThrow<ConfigException> {
                manager.getFileAsString(Path("root.txt"))
            }
        }
    }

    "containsFile" should {
        "return true for an existing configuration file" {
            val manager = createConfigManager()

            manager.containsFile(Path("root.txt")) shouldBe true
        }

        "return false for a non-existing configuration file" {
            val manager = createConfigManager()

            manager.containsFile(Path("nonExistingFile")) shouldBe false
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.containsFile(Path(ConfigFileProviderFactoryForTesting.ERROR_VALUE))
            }
        }
    }

    "listFiles" should {
        "return a set with Paths representing configuration files in a sub folder" {
            val manager = createConfigManager()

            val paths = manager.listFiles(Path("sub"))

            paths shouldContainExactlyInAnyOrder listOf(Path("sub/sub1.txt"), Path("sub/sub2.txt"))
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.listFiles(Path("nonExistingPath"))
            }
        }
    }

    "getSecret" should {
        "return the value of a secret" {
            val manager = createConfigManager()

            val secret = manager.getSecret(Path(TEST_SECRET_NAME))

            secret shouldBe TEST_SECRET_VALUE
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.getSecret(Path("nonExistingSecret"))
            }
        }
    }

    "config" should {
        "be accessible" {
            val testKey = "test.property.key"
            val testValue = "Success"
            val configMap = createConfigManagerProperties(resolveContext = false) + mapOf(testKey to testValue)
            val config = ConfigFactory.parseMap(configMap)

            val configManager = ConfigManager.create(config, ConfigManager.DEFAULT_CONTEXT)

            configManager.getString(testKey) shouldBe testValue
            configManager.getStringOrNull("foo") should beNull()
        }
    }
})

private const val TEST_SECRET_NAME = "top-secret"
private const val TEST_SECRET_VALUE = "licenseToTest"

/**
 * Create a [ConfigManager] instance that is configured to use test provider implementations. Pass the given
 * [context] and [resolveContext] flag.
 */
private fun createConfigManager(context: Context = testContext(), resolveContext: Boolean = false): ConfigManager {
    val configMap = createConfigManagerProperties(resolveContext)
    val config = ConfigFactory.parseMap(configMap)

    return ConfigManager.create(config, context, resolveContext)
}

/**
 * Return a [Map] with properties that are required to create a [ConfigManager] instance. Pass the given
 * [resolveContext] flag.
 */
private fun createConfigManagerProperties(resolveContext: Boolean): Map<String, Map<String, Any>> {
    val configManagerMap = createConfigProviderProperties(resolveContext)

    return mapOf(ConfigManager.CONFIG_MANAGER_SECTION to configManagerMap)
}

/**
 * Return a [Map] with the properties related to the configuration providers. This basically defines the content of
 * the `configManager` section in the configuration. Pass the given [resolveContext] flag.
 */
private fun createConfigProviderProperties(resolveContext: Boolean): Map<String, Any> {
    return mapOf(
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigFileProviderFactoryForTesting.FORCE_RESOLVED_PROPERTY to resolveContext,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to mapOf(TEST_SECRET_NAME to TEST_SECRET_VALUE)
    )
}

/**
 * Return a [Context] for the test [ConfigFileProvider] that points to the config directory in the test resources.
 */
private fun testContext(): Context {
    val configResource = ConfigManagerTest::class.java.getResource("/config").shouldNotBeNull()
    return Context(configResource.toURI().toPath().absolutePathString())
}
