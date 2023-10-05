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

package org.ossreviewtoolkit.server.workers.common.context

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmptyArray
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.every
import io.mockk.mockk

import kotlin.io.path.fileSize

import org.ossreviewtoolkit.server.config.ConfigException
import org.ossreviewtoolkit.server.config.ConfigFileProviderFactoryForTesting
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactoryForTesting
import org.ossreviewtoolkit.server.config.Path
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting

class WorkerContextFactoryTest : WordSpec({
    "ortRun" should {
        "return the OrtRun object" {
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()

            val context = helper.context()

            context.ortRun shouldBe run
        }

        "throw an exception if the run ID cannot be resolved" {
            val helper = ContextFactoryTestHelper()

            every { helper.ortRunRepository.get(any()) } returns null

            val context = helper.context()

            shouldThrow<IllegalArgumentException> {
                context.ortRun
            }
        }
    }

    "hierarchy" should {
        "return the hierarchy of the current repository" {
            val repositoryId = 20230607144801L
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()
            every { run.repositoryId } returns repositoryId

            val hierarchy = mockk<Hierarchy>()
            every { helper.repositoryRepository.getHierarchy(repositoryId) } returns hierarchy

            val context = helper.context()

            context.hierarchy shouldBe hierarchy
        }
    }

    "createTempDir" should {
        "return a temporary directory" {
            val helper = ContextFactoryTestHelper()
            helper.context().use { context ->
                val dir1 = context.createTempDir()
                val dir2 = context.createTempDir()

                dir1.isDirectory shouldBe true
                dir1 shouldNotBe dir2
            }
        }

        "remove the content of the temporary directory when the context is closed" {
            val helper = ContextFactoryTestHelper()
            val tempDir = helper.context().use { context ->
                val dir = context.createTempDir()

                dir.resolve("testFile.txt").writeText("This is a test file.")
                val subDir = dir.resolve("sub")
                subDir.mkdir() shouldBe true
                subDir.resolve("sub.txt").writeText("A test file in a sub folder.")

                dir
            }

            tempDir.exists() shouldBe false
        }
    }

    "resolveSecret" should {
        "resolve a secret" {
            val secret = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            context.resolveSecret(secret) shouldBe SecretsProviderFactoryForTesting.PASSWORD_SECRET.value
        }

        "cache the value of a secret that has been resolved" {
            val secret = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()
            context.resolveSecret(secret)

            val secretsProvider = SecretsProviderFactoryForTesting.instance()
            secretsProvider.writeSecret(
                SecretsProviderFactoryForTesting.SERVICE_PATH,
                org.ossreviewtoolkit.server.secrets.Secret("changedValue")
            )

            context.resolveSecret(secret) shouldBe SecretsProviderFactoryForTesting.SERVICE_SECRET.value
        }
    }

    "resolveSecrets" should {
        "resolve multiple secrets" {
            val secret1 = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)
            val secret2 = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)
            val secret3 = createSecret(SecretsProviderFactoryForTesting.TOKEN_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            val secretValues = context.resolveSecrets(secret1, secret2, secret3)

            secretValues shouldContainExactly mapOf(
                secret1 to SecretsProviderFactoryForTesting.PASSWORD_SECRET.value,
                secret2 to SecretsProviderFactoryForTesting.SERVICE_SECRET.value,
                secret3 to SecretsProviderFactoryForTesting.TOKEN_SECRET.value
            )
        }

        "cache the resolved secrets" {
            val secret1 = createSecret(SecretsProviderFactoryForTesting.PASSWORD_PATH.path)
            val secret2 = createSecret(SecretsProviderFactoryForTesting.SERVICE_PATH.path)

            val helper = ContextFactoryTestHelper()
            val context = helper.context()

            context.resolveSecrets(secret1, secret2)

            val secretsProvider = SecretsProviderFactoryForTesting.instance()
            secretsProvider.writeSecret(
                SecretsProviderFactoryForTesting.SERVICE_PATH,
                org.ossreviewtoolkit.server.secrets.Secret("changedValue")
            )

            context.resolveSecret(secret1) shouldBe SecretsProviderFactoryForTesting.PASSWORD_SECRET.value
        }
    }

    "downloadConfigurationFile" should {
        "download a single configuration file" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val file = context.downloadConfigurationFile(Path("config1.txt"))

                file.readText() shouldBe "Configuration1"
            }
        }

        "remove downloaded configuration files when the context is closed" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val file1 = context.downloadConfigurationFile(Path("config1.txt"))
            val file2 = context.downloadConfigurationFile(Path("config2.txt"))

            context.close()

            file1.isFile shouldBe false
            file2.isFile shouldBe false
        }

        "cache files that have already been downloaded" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val file1 = context.downloadConfigurationFile(Path("config1.txt"))
                val file2 = context.downloadConfigurationFile(Path("config1.txt"))

                file1 shouldBe file2
            }
        }
    }

    "downloadConfigurationFiles" should {
        "download multiple configuration files" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val path1 = Path("config1.txt")
                val path2 = Path("config2.txt")
                val files = context.downloadConfigurationFiles(listOf(path1, path2))

                files shouldHaveSize 2

                files[path1]?.readText() shouldBe "Configuration1"
                files[path2]?.readText() shouldBe "Configuration2"
            }
        }

        "remove downloaded configuration files when the context is closed" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            val files = context.downloadConfigurationFiles(listOf(Path("config1.txt"), Path("config2.txt")))

            context.close()

            files.values.forAll { it.isFile shouldBe false }
        }

        "cache files that have already been downloaded" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()

            helper.context().use { context ->
                val path = Path("config1.txt")
                val file1 = context.downloadConfigurationFile(path)
                val files = context.downloadConfigurationFiles(listOf(path))

                files[path] shouldBe file1
            }
        }

        "handle exceptions when downloading multiple configuration files gracefully" {
            val helper = ContextFactoryTestHelper()
            helper.expectRunRequest()
            val context = helper.context()

            runCatching {
                val file1 = context.downloadConfigurationFile(Path("config1.txt"))
                val tempDir = file1.parentFile
                val fileSize = file1.toPath().fileSize()

                shouldThrow<ConfigException> {
                    context.downloadConfigurationFiles(listOf(Path("config2.txt"), Path("willThrow")))
                }

                context.close()

                // Check that config2.txt is no longer present in the temporary directory.
                val foundFiles = tempDir.listFiles { file ->
                    file.toPath().fileSize() == fileSize && file.readText() == "Configuration2"
                }.shouldNotBeNull()
                foundFiles should beEmptyArray()
            }.onFailure {
                context.close()
            }
        }
    }
})

private const val RUN_ID = 20230607142948L

/** The path under which test configuration files are stored. */
private const val CONFIG_FILE_DIRECTORY = "src/test/resources/config"

/** The configuration used by the test factory. */
private val config = createConfigManager()

/**
 * Return an initialized [Config] object that configures the test secret provider factory.
 */
private fun createConfigManager(): ConfigManager {
    val configManagerProperties = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME
    )
    val properties = mapOf(
        SecretStorage.CONFIG_PREFIX to mapOf(SecretStorage.NAME_PROPERTY to SecretsProviderFactoryForTesting.NAME),
        ConfigManager.CONFIG_MANAGER_SECTION to configManagerProperties
    )
    return ConfigManager.create(ConfigFactory.parseMap(properties))
}

/**
 * Create a [Secret] with the given [path]. All other properties are irrelevant.
 */
private fun createSecret(path: String): Secret =
    Secret(0L, path, "irrelevant", null, null, null, null)

/**
 * A test helper class managing a [WorkerContextFactory] instance and its dependencies.
 */
private class ContextFactoryTestHelper(
    /** Mock for the [OrtRunRepository]. */
    val ortRunRepository: OrtRunRepository = mockk(),

    /** Mock for the [RepositoryRepository]. */
    val repositoryRepository: RepositoryRepository = mockk(),

    /** The factory to be tested. */
    val factory: WorkerContextFactory = WorkerContextFactory(config, ortRunRepository, repositoryRepository)
) {
    /**
     * Prepare the mock [OrtRunRepository] to be queried for the test run ID. Return a mock run that is also returned
     * by the repository.
     */
    fun expectRunRequest(): OrtRun {
        val run = mockk<OrtRun> {
            every { resolvedJobConfigContext } returns CONFIG_FILE_DIRECTORY
        }
        every { ortRunRepository.get(RUN_ID) } returns run

        return run
    }

    /**
     * Invoke the test factory to create a context for the test run ID.
     */
    fun context(): WorkerContext = factory.createContext(RUN_ID)
}
