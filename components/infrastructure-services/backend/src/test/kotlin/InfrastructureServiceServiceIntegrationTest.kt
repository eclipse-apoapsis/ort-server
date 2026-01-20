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

package org.eclipse.apoapsis.ortserver.components.infrastructureservices

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldInclude

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.model.validation.ValidationException
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

private const val SERVICE_NAME = "TestInfrastructureService"
private const val SERVICE_URL = "https://repo.example.org/infra/test"
private const val SERVICE_DESC = "This is a test infrastructure service"

class InfrastructureServiceServiceIntegrationTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var fixtures: Fixtures
    lateinit var organization: Organization
    lateinit var product: Product
    lateinit var repository: Repository
    lateinit var orgUserSecret: Secret
    lateinit var orgPassSecret: Secret
    lateinit var prodUserSecret: Secret
    lateinit var prodPassSecret: Secret
    lateinit var repoUserSecret: Secret
    lateinit var repoPassSecret: Secret
    lateinit var secretService: SecretService
    lateinit var service: InfrastructureServiceService

    suspend fun createOrganizationSecret(name: String, organizationId: Long = organization.id) =
        secretService.createSecret(name, "value", null, OrganizationId(organizationId))

    suspend fun createProductSecret(name: String, productId: Long = product.id) =
        secretService.createSecret(name, "value", null, ProductId(productId))

    suspend fun createRepositorySecret(name: String, repositoryId: Long = repository.id) =
        secretService.createSecret(name, "value", null, RepositoryId(repositoryId))

    fun createInfrastructureService(
        name: String = SERVICE_NAME,
        url: String = SERVICE_URL,
        description: String? = SERVICE_DESC,
        usernameSecret: String? = null,
        passwordSecret: String? = null,
        organization: Organization? = null,
        product: Product? = null,
        repository: Repository? = null,
        credentialsTypes: Set<CredentialsType> = setOf(CredentialsType.NETRC_FILE)
    ): InfrastructureService =
        InfrastructureService(
            name,
            url,
            description,
            usernameSecret ?: when {
                organization != null -> orgUserSecret.name
                product != null -> prodUserSecret.name
                repository != null -> repoUserSecret.name
                else -> error("At least one of organization, product or repository must be set.")
            },
            passwordSecret ?: when {
                organization != null -> orgPassSecret.name
                product != null -> prodPassSecret.name
                repository != null -> repoPassSecret.name
                else -> error("At least one of organization, product or repository must be set.")
            },
            organization,
            product,
            repository,
            credentialsTypes
        )

    suspend fun createInfrastructureService(infrastructureService: InfrastructureService): InfrastructureService =
        service.createForId(
            id = infrastructureService.organization?.let { OrganizationId(it.id) }
                ?: infrastructureService.product?.let { ProductId(it.id) }
                ?: infrastructureService.repository?.let { RepositoryId(it.id) }
                ?: error("At least one of organization, product or repository must be set."),
            name = infrastructureService.name,
            url = infrastructureService.url,
            description = infrastructureService.description,
            usernameSecretRef = infrastructureService.usernameSecret,
            passwordSecretRef = infrastructureService.passwordSecret,
            credentialsTypes = infrastructureService.credentialsTypes
        )

    fun createInfrastructureServiceDeclaration(
        name: String = SERVICE_NAME,
        url: String = SERVICE_URL,
        description: String = SERVICE_DESC,
        usernameSecret: Secret = orgUserSecret,
        passwordSecret: Secret = orgPassSecret,
        credentialsTypes: Set<CredentialsType> = setOf(CredentialsType.NETRC_FILE)
    ): InfrastructureServiceDeclaration =
        InfrastructureServiceDeclaration(
            name,
            url,
            description,
            usernameSecret.name,
            passwordSecret.name,
            credentialsTypes
        )

    beforeEach {
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )

        service = InfrastructureServiceService(dbExtension.db)

        fixtures = dbExtension.fixtures
        organization = dbExtension.fixtures.organization
        product = dbExtension.fixtures.product
        repository = dbExtension.fixtures.repository

        orgUserSecret = createOrganizationSecret("user")
        orgPassSecret = createOrganizationSecret("pass")
        prodUserSecret = createProductSecret("user")
        prodPassSecret = createProductSecret("pass")
        repoUserSecret = createRepositorySecret("user")
        repoPassSecret = createRepositorySecret("pass")
    }

    "createForId" should {
        "create an infrastructure service for an organization" {
            val createResult = service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                orgUserSecret.name,
                orgPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            createResult shouldBe InfrastructureService(
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                orgUserSecret.name,
                orgPassSecret.name,
                organization,
                null,
                null,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )
        }

        "create an infrastructure service for a product" {
            val createResult = service.createForId(
                ProductId(product.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                prodUserSecret.name,
                prodPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            createResult shouldBe InfrastructureService(
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                prodUserSecret.name,
                prodPassSecret.name,
                null,
                product,
                null,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )
        }

        "create an infrastructure service for a repository" {
            val createResult = service.createForId(
                RepositoryId(repository.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                repoUserSecret.name,
                repoPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            createResult shouldBe InfrastructureService(
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                repoUserSecret.name,
                repoPassSecret.name,
                null,
                null,
                repository,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )
        }
    }

    "updateForId" should {
        "update the properties of a service" {
            val newUserSecret = createOrganizationSecret("newUser")
            val newpassSecret = createOrganizationSecret("newPass")

            service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                orgUserSecret.name,
                orgPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            val updateResult = service.updateForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                "http://new.example.org".asPresent(),
                "new description".asPresent(),
                newUserSecret.name.asPresent(),
                newpassSecret.name.asPresent(),
                EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE).asPresent()
            )

            updateResult shouldBe InfrastructureService(
                SERVICE_NAME,
                "http://new.example.org",
                "new description",
                newUserSecret.name,
                newpassSecret.name,
                organization,
                null,
                null,
                EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )
        }
    }

    "deleteForId" should {
        "delete an infrastructure service" {
            service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                orgUserSecret.name,
                orgPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            service.deleteForId(OrganizationId(organization.id), SERVICE_NAME)

            service.getForId(OrganizationId(organization.id), SERVICE_NAME) should beNull()
        }
    }

    "getForId" should {
        "return an infrastructure service" {
            val infrastructureService = service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                orgUserSecret.name,
                orgPassSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            service.getForId(OrganizationId(organization.id), SERVICE_NAME) shouldBe infrastructureService
        }

        "return null for a non-existing infrastructure service" {
            service.getForId(OrganizationId(organization.id), "non-existing") should beNull()
        }
    }

    "listForId" should {
        "return a list with the infrastructure services of the organization" {
            val userSecret1 = createOrganizationSecret("user1")
            val passSecret1 = createOrganizationSecret("pass1")
            val userSecret2 = createOrganizationSecret("user2")
            val passSecret2 = createOrganizationSecret("pass2")
            val userSecret3 = createOrganizationSecret("user3")
            val passSecret3 = createOrganizationSecret("pass3")

            val services = listOf(
                service.createForId(
                    OrganizationId(organization.id),
                    "$SERVICE_NAME-1",
                    SERVICE_URL,
                    SERVICE_DESC,
                    userSecret1.name,
                    passSecret1.name,
                    EnumSet.of(CredentialsType.NETRC_FILE)
                ),
                service.createForId(
                    OrganizationId(organization.id),
                    "$SERVICE_NAME-2",
                    SERVICE_URL,
                    SERVICE_DESC,
                    userSecret2.name,
                    passSecret2.name,
                    EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
                ),
                service.createForId(
                    OrganizationId(organization.id),
                    "$SERVICE_NAME-3",
                    SERVICE_URL,
                    SERVICE_DESC,
                    userSecret3.name,
                    passSecret3.name,
                    EnumSet.noneOf(CredentialsType::class.java)
                )
            )

            val parameters = ListQueryParameters(limit = 7, offset = 0)

            val result = service.listForId(OrganizationId(organization.id), parameters)

            result shouldBe ListQueryResult(services, parameters, services.size.toLong())
        }

        "apply list query parameters" {
            val expectedServices = (1..8).map { idx ->
                InfrastructureService(
                    name = "$SERVICE_NAME$idx",
                    url = SERVICE_URL,
                    description = SERVICE_DESC,
                    usernameSecret = orgUserSecret.name,
                    passwordSecret = orgPassSecret.name,
                    organization = organization,
                    product = null,
                    repository = null,
                    credentialsTypes = emptySet()
                )
            }

            expectedServices.shuffled().forEach {
                service.createForId(
                    id = OrganizationId(organization.id),
                    name = it.name,
                    url = it.url,
                    description = it.description,
                    usernameSecretRef = it.usernameSecret,
                    passwordSecretRef = it.passwordSecret,
                    credentialsTypes = emptySet()
                )
            }

            val parameters =
                ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)), limit = 4)
            val services = service.listForId(
                OrganizationId(organization.id),
                parameters
            )

            services.data shouldContainExactly expectedServices.take(4)
        }
    }

    "listForHierarchy" should {
        "return all services for the provided IDs" {
            val repositoryUrl = "https://repo.example.org/test/repo/"
            val otherOrg = fixtures.createOrganization("anotherOrganization")
            val otherProduct = fixtures.createProduct("anotherProduct")
            val otherRepository = fixtures.createRepository(url = "https://example.com/another-repo.git")

            val match1 = createInfrastructureService(
                "matching1",
                "${repositoryUrl}repo1",
                organization = fixtures.organization
            )
            val match2 = createInfrastructureService(
                "matching2",
                "${repositoryUrl}repo2",
                product = fixtures.product
            )
            val match3 = createInfrastructureService(
                "matching3",
                "${repositoryUrl}repo3",
                organization = fixtures.organization
            )
            val match4 = createInfrastructureService(
                "matching4",
                "${repositoryUrl}repo4",
                repository = fixtures.repository
            )

            val noMatch1 = createInfrastructureService(
                "non-matching1",
                organization = otherOrg,
                usernameSecret = createOrganizationSecret("user", otherOrg.id).name,
                passwordSecret = createOrganizationSecret("pass", otherOrg.id).name
            )
            val noMatch2 = createInfrastructureService(
                "non-matching2",
                repositoryUrl,
                product = otherProduct,
                usernameSecret = createProductSecret("user", otherProduct.id).name,
                passwordSecret = createProductSecret("pass", otherProduct.id).name
            )
            val noMatch3 = createInfrastructureService(
                "non-matching3",
                repositoryUrl,
                repository = otherRepository,
                usernameSecret = createRepositorySecret("user", otherRepository.id).name,
                passwordSecret = createRepositorySecret("pass", otherRepository.id).name
            )

            listOf(match1, match2, match3, match4, noMatch1, noMatch2, noMatch3).forEach {
                createInfrastructureService(it)
            }

            val services = service.listForHierarchy(
                Hierarchy(fixtures.repository, fixtures.product, fixtures.organization)
            )

            services shouldContainExactlyInAnyOrder listOf(match1, match2, match3, match4)
        }

        "throw exception for duplicated name for same organization" {
            val productService = createInfrastructureService(product = fixtures.product)
            val orgService = createInfrastructureService(organization = fixtures.organization)
            val orgService2 = createInfrastructureService(
                url = "${SERVICE_URL}/other",
                organization = fixtures.organization
            )

            shouldThrow<UniqueConstraintException> {
                listOf(productService, orgService, orgService2).forEach {
                    createInfrastructureService(it)
                }
            }
        }

        "handle infrastructure services with duplicate URL correctly (repository beats organization)" {
            val repositoryService = createInfrastructureService(
                "MyRepositoryLevelService", repository = fixtures.repository
            )
            val orgService = createInfrastructureService(
                "MyOrganizationLevelService", organization = fixtures.organization
            )
            val orgService2 = createInfrastructureService(
                "MyOrganizationLevelService2", "${SERVICE_URL}/other", organization = fixtures.organization
            )

            listOf(repositoryService, orgService, orgService2).forEach {
                createInfrastructureService(it)
            }

            val services = service.listForHierarchy(
                Hierarchy(fixtures.repository, fixtures.product, fixtures.organization)
            )

            services shouldContainExactlyInAnyOrder listOf(repositoryService, orgService2)
        }

        "handle infrastructure services with duplicate URL correctly (repository beats product)" {
            val repositoryService = createInfrastructureService(
                "MyRepositoryLevelService", repository = fixtures.repository
            )
            val productService = createInfrastructureService(
                "MyProductLevelService", product = fixtures.product
            )
            val productService2 = createInfrastructureService(
                "MyProductLevelService2", "${SERVICE_URL}/other", product = fixtures.product
            )

            listOf(repositoryService, productService, productService2).forEach {
                createInfrastructureService(it)
            }

            val services = service.listForHierarchy(
                Hierarchy(fixtures.repository, fixtures.product, fixtures.organization)
            )

            services shouldContainExactlyInAnyOrder listOf(repositoryService, productService2)
        }
    }

    "getOrCreateDeclarationForRun" should {
        "create a new entity in the database" {
            val expectedService = createInfrastructureServiceDeclaration()
            val infrastructureService = service.getOrCreateDeclarationForRun(expectedService, fixtures.ortRun.id)

            infrastructureService shouldBe expectedService

            val runServices = service.listDeclarationsForRun(fixtures.ortRun.id)
            runServices shouldContainOnly listOf(infrastructureService)
        }

        "reuse an already existing entity" {
            val otherRun = fixtures.createOrtRun()
            val expectedService = createInfrastructureServiceDeclaration()
            val serviceForOtherRun = service.getOrCreateDeclarationForRun(expectedService, otherRun.id)
            val serviceForRun = service.getOrCreateDeclarationForRun(expectedService, fixtures.ortRun.id)

            serviceForRun shouldBe expectedService
            serviceForRun shouldBe serviceForOtherRun

            val runServices = service.listDeclarationsForRun(fixtures.ortRun.id)
            runServices shouldContainOnly listOf(serviceForRun)
        }

        "not reuse a service assigned to an organization" {
            val orgService = createInfrastructureService(organization = fixtures.organization)
            createInfrastructureService(orgService)

            val runService = createInfrastructureServiceDeclaration()
            val dbRunService = service.getOrCreateDeclarationForRun(
                runService,
                fixtures.ortRun.id
            )

            dbRunService shouldNotBe orgService
        }

        "not reuse a service assigned to a product" {
            val prodService = createInfrastructureService(product = fixtures.product)
            createInfrastructureService(prodService)

            val runService = createInfrastructureServiceDeclaration()
            val dbRunService = service.getOrCreateDeclarationForRun(runService, fixtures.ortRun.id)

            dbRunService shouldNotBe prodService
        }

        "throw exception if the entity name is invalid" {
            val serviceName = " #servicename! "
            val newService = createInfrastructureServiceDeclaration(name = serviceName)

            val exception = shouldThrow<ValidationException> {
                service.getOrCreateDeclarationForRun(newService, fixtures.ortRun.id)
            }

            exception.message shouldInclude serviceName

            service.listDeclarationsForRun(fixtures.ortRun.id) shouldBe emptyList()
        }
    }

    "listDeclarationsForRun" should {
        "return all services assigned to a run" {
            val runService1 = createInfrastructureServiceDeclaration(name = "run1")
            val runService2 = createInfrastructureServiceDeclaration(name = "run2")
            val orgService = createInfrastructureService(name = "org", organization = fixtures.organization)
            val prodService = createInfrastructureService(name = "prod", product = fixtures.product)

            createInfrastructureService(orgService)
            createInfrastructureService(prodService)
            service.getOrCreateDeclarationForRun(runService1, fixtures.ortRun.id)
            service.getOrCreateDeclarationForRun(runService2, fixtures.ortRun.id)

            val runServices = service.listDeclarationsForRun(fixtures.ortRun.id)

            runServices shouldContainExactlyInAnyOrder listOf(runService1, runService2)
        }
    }
})
