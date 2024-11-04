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

package org.eclipse.apoapsis.ortserver.config

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

import kotlin.io.path.absolutePathString
import kotlin.io.path.toPath

import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

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
                val configManager = ConfigManager.create(config)
                configManager.containsFile(Context("someContext"), Path("somePath"))
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
                val configManager = ConfigManager.create(config)
                configManager.getSecret(Path("somePath"))
            }

            exception.message shouldContain ConfigManager.SECRET_PROVIDER_NAME_PROPERTY
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
                val configManager = ConfigManager.create(config)
                configManager.containsFile(Context("someContext"), Path("somePath"))
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
                val configManager = ConfigManager.create(config)
                configManager.getSecret(Path("someSecret"))
            }

            exception.message shouldContain providerName
        }

        "pass an initialized secret provider to the file provider" {
            val providerMap = createConfigProviderProperties() +
                    mapOf(ConfigFileProviderFactoryForTesting.SECRET_PROPERTY to TEST_SECRET_NAME)
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)
            val config = ConfigFactory.parseMap(configMap)

            val configManager = ConfigManager.create(config)

            configManager.containsFile(testContext(), Path("somePath")) shouldBe false
        }

        "instantiate providers lazily" {
            val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to emptyMap<String, Any>())
            val config = ConfigFactory.parseMap(configMap)

            ConfigManager.create(config)
        }

        "not throw an exception if the configManager section is missing" {
            ConfigManager.create(ConfigFactory.empty())
        }
    }

    "resolveContext" should {
        "return the resolved context" {
            val context = "test-context"
            val manager = createConfigManager()

            val resolvedContext = manager.resolveContext(Context(context))

            resolvedContext.name shouldBe ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX + context
        }

        "use the empty context" {
            val manager = createConfigManager()

            val resolvedContext = manager.resolveContext(null)

            resolvedContext.name shouldBe ConfigFileProviderFactoryForTesting.RESOLVED_PREFIX +
                    ConfigManager.EMPTY_CONTEXT.name
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.resolveContext(Context(ConfigFileProviderFactoryForTesting.ERROR_VALUE))
            }
        }
    }

    "getFile" should {
        "return a stream for a configuration file" {
            val manager = createConfigManager()

            val fileContent = manager.getFile(testContext(), Path("root.txt")).use {
                String(it.readAllBytes()).trim()
            }

            fileContent shouldBe "Root config file."
        }

        "return a stream for a configuration from the default context" {
            val manager = createConfigManager()

            val fileContent = manager.getFile(null, Path("test.txt")).use {
                String(it.readAllBytes()).trim()
            }

            fileContent shouldBe "Test config file."
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            val exception = shouldThrow<ConfigException> {
                manager.getFile(testContext(), Path("nonExistingPath"))
            }

            exception.cause should beInstanceOf<FileNotFoundException>()
        }
    }

    "getFileString" should {
        "return the content of a configuration file as string" {
            val manager = createConfigManager()

            val fileContent = manager.getFileAsString(testContext(), Path("root.txt")).trim()

            fileContent shouldBe "Root config file."
        }

        "return the content of a configuration file from the default context as string" {
            val manager = createConfigManager()

            val fileContent = manager.getFileAsString(null, Path("test.txt")).trim()

            fileContent shouldBe "Test config file."
        }

        "handle exceptions while reading the stream" {
            val manager = spyk(createConfigManager())
            every { manager.getFile(any(), any()) } returns mockk()

            // Since an uninitialized mock is returned as stream, it will throw on each method call.
            shouldThrow<ConfigException> {
                manager.getFileAsString(testContext(), Path("root.txt"))
            }
        }
    }

    "downloadFile" should {
        "download a configuration file to a temporary directory" {
            val manager = createConfigManager()

            val file = manager.downloadFile(testContext(), Path("root.txt"))

            try {
                val fileContent = file.readText().trim()
                fileContent shouldBe "Root config file."
            } finally {
                file.delete() shouldBe true
            }
        }

        "download a configuration file from the default context to a temporary directory" {
            val manager = createConfigManager()

            val file = manager.downloadFile(null, Path("test.txt"))

            try {
                val fileContent = file.readText().trim()
                fileContent shouldBe "Test config file."
            } finally {
                file.delete() shouldBe true
            }
        }

        "download a configuration file to a specific directory" {
            val directory = tempdir()
            val manager = createConfigManager()

            val file = manager.downloadFile(testContext(), Path("root.txt"), directory)

            file.parentFile shouldBe directory
        }

        "use the file name from the configuration path" {
            val directory = tempdir()
            val manager = createConfigManager()

            val file = manager.downloadFile(testContext(), Path("sub/sub1.txt"), directory)

            file.name shouldBe "sub1.txt"
        }

        "allow overriding the file name" {
            val targetName = "config.yml"
            val directory = tempdir()
            val manager = createConfigManager()

            val file = manager.downloadFile(testContext(), Path("sub/sub1.txt"), directory, targetName)

            file.parentFile shouldBe directory
            file.name shouldBe targetName
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.downloadFile(testContext(), Path("nonExistingFile"))
            }
        }

        "handle exceptions from storing the temporary file" {
            val directory = File("this/is/a/non-existing/path")
            val manager = createConfigManager()

            val exception = shouldThrow<ConfigException> {
                manager.downloadFile(testContext(), Path("root.txt"), directory)
            }

            exception.cause should beInstanceOf<IOException>()
        }
    }

    "containsFile" should {
        "return true for an existing configuration file" {
            val manager = createConfigManager()

            manager.containsFile(testContext(), Path("root.txt")) shouldBe true
        }

        "return false for a non-existing configuration file" {
            val manager = createConfigManager()

            manager.containsFile(testContext(), Path("nonExistingFile")) shouldBe false
        }

        "return true for an existing configuration file in the default context" {
            val manager = createConfigManager()

            manager.containsFile(null, Path("test.txt")) shouldBe true
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.containsFile(testContext(), Path(ConfigFileProviderFactoryForTesting.ERROR_VALUE))
            }
        }
    }

    "listFiles" should {
        "return a set with Paths representing configuration files in a sub folder" {
            val manager = createConfigManager()

            val paths = manager.listFiles(testContext(), Path("sub"))

            paths shouldContainExactlyInAnyOrder listOf(Path("sub/sub1.txt"), Path("sub/sub2.txt"))
        }

        "return a set with Paths representing configuration files in the default context" {
            val manager = createConfigManager()

            val paths = manager.listFiles(null, Path("."))

            paths shouldContainExactlyInAnyOrder listOf(Path("./test.txt"))
        }

        "handle exceptions from the provider" {
            val manager = createConfigManager()

            shouldThrow<ConfigException> {
                manager.listFiles(testContext(), Path("nonExistingPath"))
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

        "return the value of a secret from the configuration" {
            val secretKey = "testSecret"
            val secretValue = "secretValue"
            val properties = createConfigManagerProperties() + mapOf(secretKey to secretValue)
            val manager = createConfigManager(properties)

            val secret = manager.getSecret(Path(secretKey))

            secret shouldBe secretValue
        }

        "prefer secrets from the configuration over the secret provider" {
            val secretValue = "overriddenSecretValue"
            val properties = createConfigManagerProperties() + mapOf(TEST_SECRET_NAME to secretValue)
            val manager = createConfigManager(properties)

            val secret = manager.getSecret(Path(TEST_SECRET_NAME))

            secret shouldBe secretValue
        }

        "support switching off reading secrets from the configuration" {
            val providerProperties = createConfigProviderProperties() + mapOf(
                ConfigManager.SECRET_FROM_CONFIG_PROPERTY to false
            )
            val properties = mapOf(
                ConfigManager.CONFIG_MANAGER_SECTION to providerProperties,
                TEST_SECRET_NAME to "someOtherSecretValue"
            )
            val manager = createConfigManager(properties)

            val secret = manager.getSecret(Path(TEST_SECRET_NAME))

            secret shouldBe TEST_SECRET_VALUE
        }
    }

    "config" should {
        "be accessible" {
            val testKey = "test.property.key"
            val testValue = "Success"
            val configMap = createConfigManagerProperties() + mapOf(testKey to testValue)
            val config = ConfigFactory.parseMap(configMap)

            val configManager = ConfigManager.create(config)

            configManager.getString(testKey) shouldBe testValue
            configManager.getStringOrNull("foo") should beNull()
        }

        "be accessible without a configManager section" {
            val testKey = "test.key"
            val testValue = "Success"
            val configMap = mapOf(testKey to testValue)
            val config = ConfigFactory.parseMap(configMap)

            val configManager = ConfigManager.create(config)

            configManager.getString(testKey) shouldBe testValue
        }
    }

    "subConfig" should {
        "throw an exception for an undefined path" {
            val path = Path("nonExistingPath")
            val configManager = createConfigManager()

            val exception = shouldThrow<ConfigException> {
                configManager.subConfig(path)
            }

            exception.message shouldContain path.path
        }

        "access config properties under the sub path" {
            val testKey = "test.property.key"
            val testValue = "Success"
            val configMap = createConfigManagerProperties() + mapOf(testKey to testValue)
            val configManager = createConfigManager(configMap)

            val subManager = configManager.subConfig(Path("test"))

            subManager.getString("property.key") shouldBe testValue
        }

        "access secrets under the sub path" {
            val subPath = "sub"
            val secretKey = "testSecret"
            val secretValue = "secretValue"
            val properties = createConfigManagerProperties() + mapOf("$subPath.$secretKey" to secretValue)
            val manager = createConfigManager(properties)

            val subManager = manager.subConfig(Path(subPath))
            val secret = subManager.getSecret(Path(secretKey))

            secret shouldBe secretValue
        }

        "use the same file provider" {
            val subPath = "sub"
            val properties = createConfigManagerProperties() + mapOf(subPath to mapOf("someKey" to "someValue"))
            val manager = createConfigManager(properties)

            val subManager = manager.subConfig(Path(subPath))
            val configFile = subManager.getFileAsString(testContext(), Path("root.txt")).trim()

            configFile shouldBe "Root config file."
        }

        "use the same secret provider" {
            val subPath = "subSecrets"
            val providerProperties = createConfigProviderProperties() + mapOf(
                ConfigManager.SECRET_FROM_CONFIG_PROPERTY to false
            )
            val properties = mapOf(
                ConfigManager.CONFIG_MANAGER_SECTION to providerProperties,
                subPath to mapOf(TEST_SECRET_NAME to "someOtherSecretValue")
            )
            val manager = createConfigManager(properties)

            val subManager = manager.subConfig(Path(subPath))
            val secret = subManager.getSecret(Path(TEST_SECRET_NAME))

            secret shouldBe TEST_SECRET_VALUE
        }
    }
})

private const val TEST_SECRET_NAME = "top-secret"
private const val TEST_SECRET_VALUE = "licenseToTest"

/**
 * Create a [ConfigManager] instance with the given [configuration][configMap].
 */
private fun createConfigManager(
    configMap: Map<String, Any> = createConfigManagerProperties()
): ConfigManager {
    val config = ConfigFactory.parseMap(configMap)

    return ConfigManager.create(config)
}

/**
 * Return a [Map] with properties that are required to create a [ConfigManager] instance.
 */
private fun createConfigManagerProperties(): Map<String, Map<String, Any>> {
    val configManagerMap = createConfigProviderProperties()

    return mapOf(ConfigManager.CONFIG_MANAGER_SECTION to configManagerMap)
}

/**
 * Return a [Map] with the properties related to the configuration providers. This basically defines the content of
 * the `configManager` section in the configuration.
 */
private fun createConfigProviderProperties(): Map<String, Any> {
    return mapOf(
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
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
