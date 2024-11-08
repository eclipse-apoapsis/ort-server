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

package org.eclipse.apoapsis.ortserver.workers.common.common.env.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import java.io.File
import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigException
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigLoader
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentDefinitionFactory
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.MavenDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

class EnvironmentConfigLoaderTest : StringSpec() {
    init {
        "An empty configuration is returned if there is no configuration file" {
            val tempDir = tempdir()
            val helper = TestHelper()

            val config = helper.loader().resolveAndParse(tempDir)

            config.infrastructureServices should beEmpty()
        }

        "A default configuration file is loaded if no custom file is requested" {
            val helper = TestHelper()
            val config = copyConfig(".ort.env.definitions.yml")

            val resolvedFileName = helper.loader().resolveEnvironmentConfigFile(config.parentFile)

            resolvedFileName.shouldNotBeNull {
                name shouldBe EnvironmentConfigLoader.DEFAULT_CONFIG_FILE_PATH
            }
        }

        "A custom configuration file is loaded if requested" {
            val helper = TestHelper()
            val config = copyConfig(".ort.env.definitions.yml", ".ort.env.definitions.yml")

            val resolvedFileName = helper.loader().resolveEnvironmentConfigFile(
                config.parentFile,
                ".ort.env.definitions.yml"
            )

            resolvedFileName.shouldNotBeNull {
                name shouldBe ".ort.env.definitions.yml"
            }
        }

        "A default configuration file is loaded if a custom file is requested but does not exist" {
            val helper = TestHelper()
            val config = copyConfig(".ort.env.definitions.yml")

            val resolvedFileName = helper.loader().resolveEnvironmentConfigFile(
                config.parentFile,
                ".ort.env.definitions.yml"
            )

            resolvedFileName.shouldNotBeNull {
                name shouldBe EnvironmentConfigLoader.DEFAULT_CONFIG_FILE_PATH
            }
        }

        "A configuration file can be handled that does not contain any services" {
            val helper = TestHelper()

            val config = parseConfig(".ort.env.no-services.yml", helper).resolve(helper)

            config shouldBe ResolvedEnvironmentConfig()
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

            val config = parseConfig(".ort.env.simple.yml", helper).resolve(helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Empty credentials type can be specified" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret).copy(credentialsTypes = emptySet())
            )

            val config = parseConfig(".ort.env.no-credentials-types.yml", helper).resolve(helper)

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

            val config = parseConfig(".ort.env.simple.yml", helper).resolve(helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Secrets are only queried if necessary" {
            val helper = TestHelper()
            helper.createSecret("testUser", repository = repository)
            helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            parseConfig(".ort.env.simple.yml", helper).resolve(helper)

            verify(exactly = 0) {
                helper.secretRepository.listForProduct(any(), any())
                helper.secretRepository.listForOrganization(any(), any())
            }
        }

        "Unresolved secrets cause an exception in strict mode" {
            val helper = TestHelper()
            helper.createSecret("testPassword1", organization = organization)

            val exception = shouldThrow<EnvironmentConfigException> {
                parseConfig(".ort.env.simple.yml", helper).resolve(helper)
            }

            exception.message shouldContain "testUser"
            exception.message shouldContain "testPassword2"
        }

        "Services with unresolved secrets are ignored in non-strict mode" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val expectedServices = listOf(createTestService(2, userSecret, pass2Secret))

            val config = parseConfig(".ort.env.non-strict.yml", helper).resolve(helper)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
        }

        "Environment definitions are processed" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val service = createTestService(1, userSecret, pass1Secret)

            val config = parseConfig(".ort.env.definitions.yml", helper).resolve(helper)

            config.shouldContainDefinition<MavenDefinition>(service) { it.id == "repo1" }
        }

        "Invalid definitions cause exceptions" {
            val helper = TestHelper()
            helper.createSecret("testUser", repository = repository)
            helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val exception = shouldThrow<EnvironmentConfigException> {
                parseConfig(".ort.env.definitions-errors.yml", helper).resolve(helper)
            }

            exception.message shouldContain "'Non-existing service'"
            exception.message shouldContain "Missing service reference"
            exception.message shouldContain "Unsupported definition type 'unknown'"
            exception.message shouldContain "Missing required properties"
        }

        "Invalid definitions are ignored in non-strict mode" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            helper.createSecret("testPassword2", repository = repository)

            val service = createTestService(1, userSecret, pass1Secret)

            val config = parseConfig(".ort.env.definitions-errors-non-strict.yml", helper).resolve(helper)

            config.shouldContainDefinition<MavenDefinition>(service) { it.id == "repo1" }
        }

        "Services can be resolved in the hierarchy" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val passSecret = helper.createSecret("testPassword1", repository = repository)

            val prodService = createTestService(2, userSecret, passSecret)
            val orgService = createTestService(3, userSecret, passSecret)
            val shadowedOrgService = createTestService(2, userSecret, passSecret)
                .copy(url = "https://another-repo.example.org/test.git")
            helper.withProductService(prodService)
                .withOrganizationService(orgService)
                .withOrganizationService(shadowedOrgService)

            val config = parseConfig(".ort.env.definitions-hierarchy-services.yml", helper).resolve(helper)

            config.shouldContainDefinition<MavenDefinition>(prodService) { it.id == "repo2" }
            config.shouldContainDefinition<MavenDefinition>(orgService) { it.id == "repo3" }
        }

        "Environment variable definitions with missing secrets cause exceptions" {
            val helper = TestHelper()
            helper.createSecret("testSecret1", repository = repository)

            val exception = shouldThrow<EnvironmentConfigException> {
                parseConfig(".ort.env.variables.yml", helper).resolve(helper)
            }

            exception.message shouldContain "testSecret2"
        }

        "Environment variable definitions are processed" {
            val helper = TestHelper()
            val secret1 = helper.createSecret("testSecret1", repository = repository)
            val secret2 = helper.createSecret("testSecret2", repository = repository)

            val config = parseConfig(".ort.env.variables.yml", helper).resolve(helper)

            config.environmentVariables shouldContainExactlyInAnyOrder listOf(
                SecretVariableDefinition("variable1", secret1),
                SecretVariableDefinition("variable2", secret2)
            )
        }

        "Environment variable definitions with missing secrets are ignored in non-strict mode" {
            val helper = TestHelper()
            val secret1 = helper.createSecret("testSecret1", repository = repository)

            val config = parseConfig(".ort.env.variables-non-strict.yml", helper).resolve(helper)

            config.environmentVariables shouldContainExactlyInAnyOrder listOf(
                SecretVariableDefinition("variable1", secret1),
            )
        }

        "A configuration can be resolved" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass1Secret = helper.createSecret("testPassword1", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val declarations = listOf(
                InfrastructureServiceDeclaration(
                    serviceName(1),
                    serviceUrl(1),
                    serviceDescription(1),
                    userSecret.name,
                    pass1Secret.name,
                    EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
                ),
                InfrastructureServiceDeclaration(
                    serviceName(2),
                    serviceUrl(2),
                    serviceDescription(2),
                    userSecret.name,
                    pass2Secret.name,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE)
                )
            )
            val envDefinitions = mapOf(
                "maven" to listOf(mapOf("service" to serviceName(1), "id" to "repo1"))
            )
            val variables = listOf(
                EnvironmentVariableDeclaration("USERNAME", userSecret.name),
                EnvironmentVariableDeclaration("PASSWORD", pass1Secret.name)
            )
            val envConfig = EnvironmentConfig(declarations, envDefinitions, variables, strict = false)

            val expectedServices = listOf(
                createTestService(1, userSecret, pass1Secret),
                createTestService(2, userSecret, pass2Secret)
            )
            val expectedVariables = listOf(
                SecretVariableDefinition("USERNAME", userSecret),
                SecretVariableDefinition("PASSWORD", pass1Secret)
            )

            val config = helper.loader().resolve(envConfig, hierarchy)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
            config.shouldContainDefinition<MavenDefinition>(expectedServices[0]) { it.id == "repo1" }
            config.environmentVariables shouldContainExactlyInAnyOrder expectedVariables
        }

        "Resolving of a configuration works correctly if `strict` is false" {
            val helper = TestHelper()
            val userSecret = helper.createSecret("testUser", repository = repository)
            val pass2Secret = helper.createSecret("testPassword2", repository = repository)

            val serviceDeclarations = listOf(
                InfrastructureServiceDeclaration(
                    serviceName(1),
                    serviceUrl(1),
                    serviceDescription(1),
                    userSecret.name,
                    "anUnknownSecret"
                ),
                InfrastructureServiceDeclaration(
                    serviceName(2),
                    serviceUrl(2),
                    serviceDescription(2),
                    userSecret.name,
                    pass2Secret.name,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE)
                )
            )
            val variableDeclarations = listOf(
                EnvironmentVariableDeclaration("USERNAME", userSecret.name),
                EnvironmentVariableDeclaration("PASSWORD", "someOtherUnknownSecret")
            )
            val envConfig =
                EnvironmentConfig(serviceDeclarations, environmentVariables = variableDeclarations, strict = false)

            val expectedServices = listOf(createTestService(2, userSecret, pass2Secret))
            val expectedVariables = listOf(SecretVariableDefinition("USERNAME", userSecret))

            val config = helper.loader().resolve(envConfig, hierarchy)

            config.infrastructureServices shouldContainExactlyInAnyOrder expectedServices
            config.environmentVariables shouldContainExactlyInAnyOrder expectedVariables
        }

        "Resolving a configuration works correctly if `strict` is true and there are invalid service declarations" {
            val helper = TestHelper()

            val declarations = listOf(
                InfrastructureServiceDeclaration(
                    serviceName(1),
                    serviceUrl(1),
                    serviceDescription(1),
                    "unknownUser",
                    "unknownPassword"
                )
            )
            val envConfig = EnvironmentConfig(declarations, strict = true)

            shouldThrow<EnvironmentConfigException> {
                helper.loader().resolve(envConfig, hierarchy)
            }
        }

        "Resolving a configuration works correctly if `strict` is true and there are invalid variable declarations" {
            val helper = TestHelper()

            val variableDeclarations = listOf(
                EnvironmentVariableDeclaration("USERNAME", "unknownSecret")
            )
            val envConfig = EnvironmentConfig(environmentVariables = variableDeclarations, strict = true)

            shouldThrow<EnvironmentConfigException> {
                helper.loader().resolve(envConfig, hierarchy)
            }
        }

        "Simple environment variable definitions are processed" {
            val helper = TestHelper()

            val config = parseConfig(".ort.env.direct-variables.yml", helper).resolve(helper)

            config.environmentVariables shouldContainExactlyInAnyOrder listOf(
                SimpleVariableDefinition("variable1", value = "testValue1"),
                SimpleVariableDefinition("variable2", value = "testValue2")
            )
        }
    }

    /**
     * Copy the test configuration with the given [name] from the resources to a temporary directory, with the given
     * [targetName].
     */
    private fun copyConfig(
        name: String,
        targetName: String = EnvironmentConfigLoader.DEFAULT_CONFIG_FILE_PATH
    ): File {
        val tempDir = tempdir()
        val target = File(tempDir, targetName)

        val success = javaClass.getResource("/$name")?.let {
            File(it.toURI()).copyTo(target)
        }

        success shouldNot beNull()

        return target
    }

    /**
     * Parse the test configuration with the given [name] from the resources using the given [helper].
     */
    private fun parseConfig(name: String, helper: TestHelper): EnvironmentConfig {
        val fileToParse = copyConfig(name)

        return helper.loader().parseEnvironmentConfigFile(fileToParse)
    }

    /**
     * Resolve the test configuration using the given [helper].
     */
    private fun EnvironmentConfig.resolve(helper: TestHelper): ResolvedEnvironmentConfig =
        helper.loader().resolve(this, hierarchy)
}

/**
 * A test helper class managing the dependencies required by the object under test.
 */
private class TestHelper(
    /** Mock for the repository for secrets. */
    val secretRepository: SecretRepository = mockk(),

    /** Mock for the repository for infrastructure services. */
    val serviceRepository: InfrastructureServiceRepository = mockk()
) {
    /** Stores the secrets referenced by tests. */
    private val secrets = mutableListOf<Secret>()

    /** Stores infrastructure services assigned to the current product. */
    private val productServices = mutableListOf<InfrastructureService>()

    /** Stores infrastructure services assigned to the current organization. */
    private val organizationServices = mutableListOf<InfrastructureService>()

    /**
     * Create a new [EnvironmentConfigLoader] instance with the dependencies managed by this object.
     */
    fun loader(): EnvironmentConfigLoader {
        initSecretRepository()
        initServiceRepository()

        return EnvironmentConfigLoader(secretRepository, serviceRepository, EnvironmentDefinitionFactory())
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
     * Add the given [service] to the list of product services. It will be returned by the mock service repository
     * when it is queried for product services.
     */
    fun withProductService(service: InfrastructureService): TestHelper {
        productServices += service
        return this
    }

    /**
     * Add the given [service] to the list of organization services. It will be returned by the mock service repository
     * when it is queried for organization services.
     */
    fun withOrganizationService(service: InfrastructureService): TestHelper {
        organizationServices += service
        return this
    }

    /**
     * Prepare the mock for the [SecretRepository] to answer queries based on the secrets that have been defined.
     */
    private fun initSecretRepository() {
        every {
            secretRepository.listForRepository(repository.id)
        } returns mockk<ListQueryResult<Secret>> {
            every { data } returns secrets.filter { it.repository != null }
        }
        every {
            secretRepository.listForProduct(product.id)
        } returns mockk<ListQueryResult<Secret>> {
            every { data } returns secrets.filter { it.product != null }
        }
        every {
            secretRepository.listForOrganization(organization.id)
        } returns mockk<ListQueryResult<Secret>> {
            every { data } returns secrets.filter { it.organization != null }
        }
    }

    /**
     * Prepare the mock for the [InfrastructureServiceRepository] to answer queries for the product and organization
     * services based on the data that has been defined.
     */
    private fun initServiceRepository() {
        every { serviceRepository.listForProduct(hierarchy.product.id) } returns productServices
        every { serviceRepository.listForOrganization(hierarchy.organization.id) } returns
                ListQueryResult(organizationServices, ListQueryParameters.DEFAULT, organizationServices.size.toLong())
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
        name = serviceName(index),
        url = serviceUrl(index),
        description = serviceDescription(index),
        usernameSecret = userSecret,
        passwordSecret = passSecret,
        organization = null,
        product = null,
        credentialsTypes = setOf(CredentialsType.entries[index % 2])
    )

/**
 * Generate the name of a test service with the given [index].
 */
private fun serviceName(index: Int): String = "Test service$index"

/**
 * Generate the URL of a test service with the given [index].
 */
private fun serviceUrl(index: Int): String = "https://repo.example.org/test/service$index"

/**
 * Generate the description of a test service with the given [index].
 */
private fun serviceDescription(index: Int) = "Test service $index"

/**
 * Check whether this [ResolvedEnvironmentConfig] contains an environment definition of a specific type that references
 * the given [service] and passes the given [check].
 */
private inline fun <reified T : EnvironmentServiceDefinition> ResolvedEnvironmentConfig.shouldContainDefinition(
    service: InfrastructureService,
    check: (T) -> Boolean
) {
    environmentDefinitions.find { definition ->
        definition is T && definition.service == service && check(definition)
    } shouldNot beNull()
}
