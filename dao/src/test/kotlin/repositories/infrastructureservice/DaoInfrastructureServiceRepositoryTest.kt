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

package org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.DaoSecretRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException

class DaoInfrastructureServiceRepositoryTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var infrastructureServicesRepository: DaoInfrastructureServiceRepository
    private lateinit var secretRepository: DaoSecretRepository
    private lateinit var ortRunRepository: DaoOrtRunRepository
    private lateinit var fixtures: Fixtures
    private lateinit var usernameSecret: Secret
    private lateinit var passwordSecret: Secret

    init {
        beforeEach {
            infrastructureServicesRepository = dbExtension.fixtures.infrastructureServiceRepository
            secretRepository = dbExtension.fixtures.secretRepository
            ortRunRepository = dbExtension.fixtures.ortRunRepository
            fixtures = dbExtension.fixtures

            usernameSecret = secretRepository.create("p1", "user", null, OrganizationId(fixtures.organization.id))
            passwordSecret = secretRepository.create("p2", "pass", null, OrganizationId(fixtures.organization.id))
        }

        "create" should {
            "create an infrastructure service for an organization" {
                val expectedService = createInfrastructureService(organization = fixtures.organization)

                val service = infrastructureServicesRepository.create(expectedService)

                service shouldBe expectedService

                val organizationServices =
                    infrastructureServicesRepository.listForOrganization(fixtures.organization.id)
                organizationServices.data shouldContainOnly listOf(service)
            }

            "create an infrastructure service for a product" {
                val expectedService =
                    createInfrastructureService(product = fixtures.product, credentialsTypes = emptySet())

                val service = infrastructureServicesRepository.create(expectedService)

                service shouldBe expectedService

                val productServices = infrastructureServicesRepository.listForProduct(fixtures.product.id)
                productServices shouldContainOnly listOf(service)
            }
        }

        "listForOrganization" should {
            "return all services assigned to an organization" {
                val orgService1 = createInfrastructureService(organization = fixtures.organization)
                val orgService2 = createInfrastructureService(organization = fixtures.organization, name = "other")
                val prodService = createInfrastructureService(product = fixtures.product, name = "productService")

                listOf(orgService1, prodService, orgService2).forEach { infrastructureServicesRepository.create(it) }

                val services = infrastructureServicesRepository.listForOrganization(fixtures.organization.id)

                services.data shouldContainExactlyInAnyOrder listOf(orgService1, orgService2)
            }

            "apply list query parameters" {
                val expectedServices = (1..8).map { idx ->
                    createInfrastructureService(name = "$SERVICE_NAME$idx", organization = fixtures.organization)
                }

                expectedServices.shuffled().forEach { infrastructureServicesRepository.create(it) }

                val parameters =
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)), limit = 4)
                val services =
                    infrastructureServicesRepository.listForOrganization(fixtures.organization.id, parameters)

                services.data shouldContainExactly expectedServices.take(4)
            }
        }

        "listForProduct" should {
            "return all services assigned to a product" {
                val prodService1 = createInfrastructureService(product = fixtures.product)
                val prodService2 = createInfrastructureService(product = fixtures.product, name = "other")
                val orgService =
                    createInfrastructureService(organization = fixtures.organization, name = "productService")

                listOf(prodService1, orgService, prodService2).forEach { infrastructureServicesRepository.create(it) }

                val services = infrastructureServicesRepository.listForProduct(fixtures.product.id)

                services shouldContainExactlyInAnyOrder listOf(prodService1, prodService2)
            }

            "apply list query parameters" {
                val expectedServices = (1..8).map { idx ->
                    createInfrastructureService(name = "$SERVICE_NAME$idx", product = fixtures.product)
                }

                expectedServices.shuffled().forEach { infrastructureServicesRepository.create(it) }

                val parameters =
                    ListQueryParameters(sortFields = listOf(OrderField("name", OrderDirection.ASCENDING)), limit = 4)
                val services = infrastructureServicesRepository.listForProduct(fixtures.product.id, parameters)

                services shouldContainExactly expectedServices.take(4)
            }
        }

        "getForOrganizationAndName" should {
            "return an existing service" {
                val expectedService = createInfrastructureService(organization = fixtures.organization)
                infrastructureServicesRepository.create(expectedService)

                val service =
                    infrastructureServicesRepository.getByOrganizationAndName(fixtures.organization.id, SERVICE_NAME)

                service shouldBe expectedService
            }

            "return null for a non-existing service" {
                infrastructureServicesRepository.create(
                    createInfrastructureService(organization = fixtures.organization)
                )

                val service = infrastructureServicesRepository.getByOrganizationAndName(
                    fixtures.organization.id,
                    "onExisting"
                )

                service should beNull()
            }
        }

        "getForProductAndName" should {
            "return an existing service" {
                val expectedService = createInfrastructureService(product = fixtures.product)
                infrastructureServicesRepository.create(expectedService)

                val service = infrastructureServicesRepository.getByProductAndName(fixtures.product.id, SERVICE_NAME)

                service shouldBe expectedService
            }

            "return null for a non-existing service" {
                infrastructureServicesRepository.create(createInfrastructureService(product = fixtures.product))

                val service = infrastructureServicesRepository.getByProductAndName(fixtures.product.id, "onExisting")

                service should beNull()
            }
        }

        "updateForOrganizationAndName" should {
            "update the properties of a service" {
                val newUser = secretRepository.create("p3", "newUser", null, OrganizationId(fixtures.organization.id))
                val newPassword = secretRepository.create(
                    "p4", "newPass", null, OrganizationId(fixtures.organization.id)
                )
                val service = createInfrastructureService(organization = fixtures.organization)
                val updatedService = createInfrastructureService(
                    url = "https://repo.example.org/newRepo",
                    description = null,
                    usernameSecret = newUser,
                    passwordSecret = newPassword,
                    organization = fixtures.organization,
                    credentialsTypes = EnumSet.of(CredentialsType.NETRC_FILE, CredentialsType.GIT_CREDENTIALS_FILE),
                )

                infrastructureServicesRepository.create(service)

                val result = infrastructureServicesRepository.updateForOrganizationAndName(
                    fixtures.organization.id,
                    SERVICE_NAME,
                    updatedService.url.asPresent(),
                    updatedService.description.asPresent(),
                    updatedService.usernameSecret.asPresent(),
                    updatedService.passwordSecret.asPresent(),
                    credentialsTypes = updatedService.credentialsTypes.asPresent()
                )

                result shouldBe updatedService

                val dbService =
                    infrastructureServicesRepository.getByOrganizationAndName(fixtures.organization.id, SERVICE_NAME)
                dbService shouldBe updatedService
            }

            "fail for a non-existing service" {
                shouldThrow<EntityNotFoundException> {
                    infrastructureServicesRepository.updateForOrganizationAndName(
                        42L,
                        SERVICE_NAME,
                        OptionalValue.Absent,
                        OptionalValue.Absent,
                        OptionalValue.Absent,
                        OptionalValue.Absent,
                        OptionalValue.Absent
                    )
                }
            }
        }

        "updateForProductAndName" should {
            "update the properties of a service" {
                val newUser = secretRepository.create("p3", "newUser", null, OrganizationId(fixtures.organization.id))
                val newPassword = secretRepository.create(
                    "p4", "newPass", null, OrganizationId(fixtures.organization.id)
                )
                val service = createInfrastructureService(product = fixtures.product)
                val updatedService = createInfrastructureService(
                    url = "https://repo.example.org/newRepo",
                    description = null,
                    usernameSecret = newUser,
                    passwordSecret = newPassword,
                    product = fixtures.product,
                    credentialsTypes = emptySet(),
                )

                infrastructureServicesRepository.create(service)

                val result = infrastructureServicesRepository.updateForProductAndName(
                    fixtures.product.id,
                    SERVICE_NAME,
                    updatedService.url.asPresent(),
                    updatedService.description.asPresent(),
                    updatedService.usernameSecret.asPresent(),
                    updatedService.passwordSecret.asPresent(),
                    updatedService.credentialsTypes.asPresent()
                )

                result shouldBe updatedService

                val dbService = infrastructureServicesRepository.getByProductAndName(fixtures.product.id, SERVICE_NAME)
                dbService shouldBe updatedService
            }

            "fail for a non-existing service" {
                shouldThrow<EntityNotFoundException> {
                    infrastructureServicesRepository.updateForProductAndName(
                        42L,
                        SERVICE_NAME,
                        OptionalValue.Absent,
                        OptionalValue.Absent,
                        OptionalValue.Absent,
                        OptionalValue.Absent
                    )
                }
            }
        }

        "deleteForOrganizationAndName" should {
            "delete an existing entity" {
                val service1 = createInfrastructureService(organization = fixtures.organization)
                val service2 =
                    createInfrastructureService(name = "I_will_survive", organization = fixtures.organization)

                infrastructureServicesRepository.create(service1)
                infrastructureServicesRepository.create(service2)

                infrastructureServicesRepository.deleteForOrganizationAndName(fixtures.organization.id, SERVICE_NAME)

                val orgServices = infrastructureServicesRepository.listForOrganization(fixtures.organization.id)
                orgServices.data shouldContainOnly listOf(service2)
            }

            "fail for a non-existing service" {
                infrastructureServicesRepository.create(
                    createInfrastructureService(organization = fixtures.organization)
                )

                shouldThrow<EntityNotFoundException> {
                    infrastructureServicesRepository.deleteForOrganizationAndName(
                        fixtures.organization.id,
                        "nonExisting"
                    )
                }
            }
        }

        "deleteForProductAndName" should {
            "delete an existing entity" {
                val service1 = createInfrastructureService(product = fixtures.product)
                val service2 = createInfrastructureService(name = "I_will_survive", product = fixtures.product)

                infrastructureServicesRepository.create(service1)
                infrastructureServicesRepository.create(service2)

                infrastructureServicesRepository.deleteForProductAndName(fixtures.product.id, SERVICE_NAME)

                val prodServices = infrastructureServicesRepository.listForProduct(fixtures.product.id)
                prodServices shouldContainOnly listOf(service2)
            }

            "fail for a non-existing service" {
                infrastructureServicesRepository.create(createInfrastructureService(product = fixtures.product))

                shouldThrow<EntityNotFoundException> {
                    infrastructureServicesRepository.deleteForProductAndName(fixtures.product.id, "nonExisting")
                }
            }
        }

        "listForHierarchy" should {
            "return all services for the provided IDs" {
                val repositoryUrl = "https://repo.example.org/test/repo/"
                val otherOrg = fixtures.createOrganization("anotherOrganization")
                val otherProduct = fixtures.createProduct("anotherProduct")

                val match1 = createInfrastructureService(
                    "matching1",
                    url = "${repositoryUrl}repo1",
                    organization = fixtures.organization
                )
                val match2 = createInfrastructureService(
                    "matching2",
                    url = "${repositoryUrl}repo2",
                    product = fixtures.product
                )
                val match3 = createInfrastructureService(
                    "matching3",
                    url = "${repositoryUrl}repo3",
                    organization = fixtures.organization
                )

                val noMatch1 = createInfrastructureService(
                    "non-matching1",
                    organization = otherOrg
                )
                val noMatch2 = createInfrastructureService(
                    name = "non-matching2",
                    url = repositoryUrl,
                    product = otherProduct
                )

                listOf(match1, match2, match3, noMatch1, noMatch2).forEach {
                    infrastructureServicesRepository.create(it)
                }

                val services = infrastructureServicesRepository.listForHierarchy(
                    fixtures.organization.id,
                    fixtures.product.id
                )

                services shouldContainExactlyInAnyOrder listOf(match1, match2, match3)
            }

            "handle infrastructure services with duplicate URL correctly" {
                val productService = createInfrastructureService(product = fixtures.product)
                val orgService = createInfrastructureService(organization = fixtures.organization)
                val orgService2 = createInfrastructureService(
                    url = "${SERVICE_URL}/other",
                    organization = fixtures.organization
                )

                listOf(productService, orgService, orgService2).forEach {
                    infrastructureServicesRepository.create(it)
                }

                val services = infrastructureServicesRepository.listForHierarchy(
                    fixtures.organization.id,
                    fixtures.product.id
                )

                services shouldContainExactlyInAnyOrder listOf(productService, orgService2)
            }
        }

        "listForSecret" should {
            "return all services using the secret" {
                val service1 = createInfrastructureService()
                val service2 = createInfrastructureService(name = "OtherRepositoryService")

                val otherSecret = secretRepository.create(
                    path = "p3",
                    name = "otherUser",
                    description = null,
                    id = OrganizationId(fixtures.organization.id)
                )

                val service3 = createInfrastructureService(
                    name = "NotIncludedService",
                    usernameSecret = otherSecret,
                    passwordSecret = otherSecret
                )

                infrastructureServicesRepository.create(service1)
                infrastructureServicesRepository.create(service2)
                infrastructureServicesRepository.create(service3)

                infrastructureServicesRepository.listForSecret(usernameSecret.id) shouldBe listOf(service1, service2)
            }

            "return an empty list when a secret is not used in any service" {
                infrastructureServicesRepository.listForSecret(usernameSecret.id).shouldBeEmpty()
            }
        }
    }

    /**
     * Convenience function to create a test [InfrastructureService] with default properties.
     */
    private fun createInfrastructureService(
        name: String = SERVICE_NAME,
        url: String = SERVICE_URL,
        description: String? = SERVICE_DESCRIPTION,
        usernameSecret: Secret = this.usernameSecret,
        passwordSecret: Secret = this.passwordSecret,
        organization: Organization? = null,
        product: Product? = null,
        credentialsTypes: Set<CredentialsType> = setOf(CredentialsType.NETRC_FILE),
    ): InfrastructureService =
        InfrastructureService(
            name,
            url,
            description,
            usernameSecret,
            passwordSecret,
            organization,
            product,
            credentialsTypes
        )
}

private const val SERVICE_NAME = "MyRepositoryService"
private const val SERVICE_URL = "https://repo.example.org/artifacts"
private const val SERVICE_DESCRIPTION = "This infrastructure service..."

/**
 * Create an infrastructure service in the database based on the given [service].
 */
private fun DaoInfrastructureServiceRepository.create(service: InfrastructureService): InfrastructureService =
    create(
        service.name,
        service.url,
        service.description,
        service.usernameSecret,
        service.passwordSecret,
        service.credentialsTypes,
        service.organization?.id,
        service.product?.id
    )
