/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldInclude

import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.DaoSecretRepository
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.validation.ValidationException

class InfrastructureServiceDeclarationRepositoryTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var infrastructureServicesRepository: DaoInfrastructureServiceRepository
    private lateinit var infrastructureServiceDeclarationRepository: DaoInfrastructureServiceDeclarationRepository
    private lateinit var secretRepository: DaoSecretRepository
    private lateinit var ortRunRepository: DaoOrtRunRepository
    private lateinit var fixtures: Fixtures
    private lateinit var usernameSecret: Secret
    private lateinit var passwordSecret: Secret

    init {
        beforeEach {
            infrastructureServicesRepository = dbExtension.fixtures.infrastructureServiceRepository
            infrastructureServiceDeclarationRepository = dbExtension.fixtures.infrastructureServiceDeclarationRepository
            secretRepository = dbExtension.fixtures.secretRepository
            ortRunRepository = dbExtension.fixtures.ortRunRepository
            fixtures = dbExtension.fixtures

            usernameSecret = secretRepository.create("p1", "user", null, OrganizationId(fixtures.organization.id))
            passwordSecret = secretRepository.create("p2", "pass", null, OrganizationId(fixtures.organization.id))
        }

        "getOrCreateForRun" should {
            "create a new entity in the database" {
                val expectedService = createInfrastructureServiceDeclaration()

                val service = infrastructureServiceDeclarationRepository.getOrCreateForRun(
                    expectedService,
                    fixtures.ortRun.id
                )

                service shouldBe expectedService

                val runServices = infrastructureServiceDeclarationRepository.listForRun(fixtures.ortRun.id)
                runServices shouldContainOnly listOf(service)
            }

            "reuse an already existing entity" {
                val otherRun = fixtures.createOrtRun()
                val expectedService = createInfrastructureServiceDeclaration()
                val serviceForOtherRun =
                    infrastructureServiceDeclarationRepository.getOrCreateForRun(expectedService, otherRun.id)

                val serviceForRun =
                    infrastructureServiceDeclarationRepository.getOrCreateForRun(expectedService, fixtures.ortRun.id)

                serviceForRun shouldBe expectedService
                serviceForRun shouldBe serviceForOtherRun

                val runServices = infrastructureServiceDeclarationRepository.listForRun(fixtures.ortRun.id)
                runServices shouldContainOnly listOf(serviceForRun)
            }

            "not reuse a service assigned to an organization" {
                val orgService = createInfrastructureService(organization = fixtures.organization)
                infrastructureServicesRepository.create(orgService)

                val runService = createInfrastructureServiceDeclaration()
                val dbRunService = infrastructureServiceDeclarationRepository.getOrCreateForRun(
                    runService,
                    fixtures.ortRun.id
                )

                dbRunService shouldNotBe orgService
            }

            "not reuse a service assigned to a product" {
                val prodService = createInfrastructureService(product = fixtures.product)
                infrastructureServicesRepository.create(prodService)

                val runService = createInfrastructureServiceDeclaration()
                val dbRunService = infrastructureServiceDeclarationRepository.getOrCreateForRun(
                    runService,
                    fixtures.ortRun.id
                )

                dbRunService shouldNotBe prodService
            }

            "throw exception if the entity name is invalid" {
                val serviceName = " #servicename! "
                val newService = createInfrastructureServiceDeclaration(name = serviceName)

                val exception = shouldThrow<ValidationException> {
                    infrastructureServiceDeclarationRepository.getOrCreateForRun(newService, fixtures.ortRun.id)
                }

                exception.message shouldInclude serviceName

                infrastructureServiceDeclarationRepository.listForRun(fixtures.ortRun.id) shouldBe emptyList()
            }
        }

        "listForRun" should {
            "return all services assigned to a run" {
                val runService1 = createInfrastructureServiceDeclaration(name = "run1")
                val runService2 = createInfrastructureServiceDeclaration(name = "run2")
                val orgService = createInfrastructureService(name = "org", organization = fixtures.organization)
                val prodService = createInfrastructureService(name = "prod", product = fixtures.product)

                infrastructureServicesRepository.create(orgService)
                infrastructureServicesRepository.create(prodService)
                infrastructureServiceDeclarationRepository.getOrCreateForRun(runService1, fixtures.ortRun.id)
                infrastructureServiceDeclarationRepository.getOrCreateForRun(runService2, fixtures.ortRun.id)

                val runServices = infrastructureServiceDeclarationRepository.listForRun(fixtures.ortRun.id)

                runServices shouldContainExactlyInAnyOrder listOf(runService1, runService2)
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

    private fun createInfrastructureServiceDeclaration(
        name: String = SERVICE_NAME,
        url: String = SERVICE_URL,
        description: String = SERVICE_DESCRIPTION,
        usernameSecret: Secret = this.usernameSecret,
        passwordSecret: Secret = this.passwordSecret,
        credentialsTypes: Set<CredentialsType> = setOf(CredentialsType.NETRC_FILE),
    ): InfrastructureServiceDeclaration =
        InfrastructureServiceDeclaration(
            name,
            url,
            description,
            usernameSecret.name,
            passwordSecret.name,
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
        service.organization?.let { OrganizationId(it.id) }
            ?: service.product?.let { ProductId(it.id) }
            ?: error("At least one of organization or product must be set.")
    )
