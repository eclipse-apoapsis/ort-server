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

package org.ossreviewtoolkit.server.workers.common.common.env.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfig
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigException
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigLoader

class EnvironmentConfigLoaderTest : StringSpec() {
    init {
        "An empty configuration is returned if there is no configuration file" {
            val tempDir = tempdir()
            val helper = TestHelper()

            val config = helper.loader().parse(tempDir, hierarchy)

            config.infrastructureServices should beEmpty()
        }

        "A configuration file can be read" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret)
            )

            val config = loadConfig(".ort.env.simple.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Secrets can be resolved from products and organizations" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", product = product)
            val pass2Secret = helper.createSecret("testPassword2", organization = organization)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret)
            )

            val config = loadConfig(".ort.env.simple.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Secrets are only queried if necessary" {
            val helper = TestHelper()
            helper.createSecret("testUser", repository = repository)
            helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            loadConfig(".ort.env.simple.yml", helper)

            verify(exactly = 0) {
                helper.secretRepository.listForProduct(any(), any())
                helper.secretRepository.listForOrganization(any(), any())
            }
        }

        "Unresolved secrets cause an exception in strict mode" {
            val helper = TestHelper()
            helper.createSecret("testPassword1", organization = organization)

            val exception = shouldThrow<EnvironmentConfigException> {
                loadConfig(".ort.env.simple.yml", helper)
            }

            exception.message shouldContain "testUser"
            exception.message shouldContain "testPassword2"
        }

        "Services with unresolved secrets are ignored in non-strict mode" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(createTestService(2, userSecret, pass2Secret))

            val config = loadConfig(".ort.env.non-strict.yml", helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }
    }

    /**
     * Read the test configuration with the given [name] from the resources using the given [helper].
     */
    private fun loadConfig(name: String, helper: TestHelper): EnvironmentConfig {
        val tempDir = tempdir()

        javaClass.getResourceAsStream("/$name")?.use { stream ->
            val target = tempDir.resolve(EnvironmentConfigLoader.CONFIG_FILE_PATH)
            target.outputStream().use { out ->
                stream.copyTo(out)
            }
        }

        return helper.loader().parse(tempDir, hierarchy)
    }
}

/**
 * A test helper class managing the dependencies required by the object under test.
 */
private class TestHelper(
    /** Mock for the repository for secrets. */
    val secretRepository: SecretRepository = mockk()
) {
    /** Stores the secrets referenced by tests. */
    private val secrets = mutableListOf<Secret>()

    /**
     * Create a new [EnvironmentConfigLoader] instance with the dependencies managed by this object.
     */
    fun loader(): EnvironmentConfigLoader {
        initSecretRepository()

        return EnvironmentConfigLoader(secretRepository)
    }

    /**
     * Create a test secret with the given [name] and associate it with the given structures. The mock for the
     * [SecretRepository] is also prepared to return this secret when asked for the corresponding structure.
     */
    fun createSecret(
        name: String,
        repository: Repository? = null,
        product: Product? = null,
        organization: Organization? = null
    ): Secret =
        Secret(
            id = 0,
            path = name,
            name = name,
            description = null,
            organization = organization,
            product = product,
            repository = repository
        ).also { secrets.add(it) }

    /**
     * Prepare the mock for the [SecretRepository] to answer queries based on the secrets that have been defined.
     */
    private fun initSecretRepository() {
        every {
            secretRepository.listForRepository(repository.id)
        } returns secrets.filter { it.repository != null }
        every {
            secretRepository.listForProduct(product.id)
        } returns secrets.filter { it.product != null }
        every {
            secretRepository.listForOrganization(organization.id)
        } returns secrets.filter { it.organization != null }
    }
}

private val organization = Organization(20230621115936L, "Test organization")
private val product = Product(20230621120012L, organization.id, "Test product")
private val repository = Repository(
    20230621120048L,
    organization.id,
    product.id,
    RepositoryType.GIT,
    "https://repo.example.org/test.git"
)

/** A test [Hierarchy] used by the tests. */
private val hierarchy = Hierarchy(repository, product, organization)

/**
 * Create a test service based on the given [index] with the given [userSecret] and [passSecret].
 */
private fun createTestService(index: Int, userSecret: Secret, passSecret: Secret): InfrastructureService =
    InfrastructureService(
        name = "Test service$index",
        url = "https://repo.example.org/test/service$index",
        description = "Test service $index",
        usernameSecret = userSecret,
        passwordSecret = passSecret,
        organization = null,
        product = null
    )
