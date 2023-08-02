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
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.server.secrets.SecretsProviderFactoryForTesting

class WorkerContextFactoryTest : WordSpec({
    beforeSpec {
        mockkObject(ConfigManager)
    }

    afterSpec {
        unmockkAll()
    }

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

    "configManager" should {
        "return a ConfigManager using the resolved context" {
            val resolvedContext = "resolvedConfigContext"
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()
            every { run.resolvedConfigContext } returns resolvedContext

            val configManager = mockk<ConfigManager>()
            every { ConfigManager.create(config, Context(resolvedContext)) } returns configManager

            val context = helper.context()

            context.configManager() shouldBe configManager
        }

        "return a ConfigManager using the original context" {
            val originalContext = "originalConfigContext"
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()
            every { run.configContext } returns originalContext

            val configManager = mockk<ConfigManager>()
            every {
                ConfigManager.create(config, Context(originalContext), resolveContext = true)
            } returns configManager

            val context = helper.context()

            context.configManager(resolveContext = true) shouldBe configManager
        }

        "return a ConfigManager using the default context" {
            val helper = ContextFactoryTestHelper()

            val run = helper.expectRunRequest()
            every { run.resolvedConfigContext } returns null

            val configManager = mockk<ConfigManager>()
            every { ConfigManager.create(config, ConfigManager.DEFAULT_CONTEXT) } returns configManager

            val context = helper.context()

            context.configManager() shouldBe configManager
        }
    }
})

private const val RUN_ID = 20230607142948L

/** The configuration used by the test factory. */
private val config = createConfig()

/**
 * Return an initialized [Config] object that configures the test secret provider factory.
 */
private fun createConfig(): Config {
    val properties = mapOf(
        SecretStorage.CONFIG_PREFIX to mapOf(SecretStorage.NAME_PROPERTY to SecretsProviderFactoryForTesting.NAME)
    )
    return ConfigFactory.parseMap(properties)
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
        val run = mockk<OrtRun>()
        every { ortRunRepository.get(RUN_ID) } returns run

        return run
    }

    /**
     * Invoke the test factory to create a context for the test run ID.
     */
    fun context(): WorkerContext = factory.createContext(RUN_ID)
}
