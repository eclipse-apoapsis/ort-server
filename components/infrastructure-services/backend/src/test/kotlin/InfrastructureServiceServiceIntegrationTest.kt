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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

private const val SERVICE_NAME = "TestInfrastructureService"
private const val SERVICE_URL = "https://repo.example.org/infra/test"
private const val SERVICE_DESC = "This is a test infrastructure service"

class InfrastructureServiceServiceIntegrationTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var organization: Organization
    lateinit var secretService: SecretService
    lateinit var service: InfrastructureServiceService

    beforeEach {
        secretService = SecretService(
            dbExtension.db,
            dbExtension.fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )

        service = InfrastructureServiceService(dbExtension.db, secretService)

        organization = dbExtension.fixtures.organization
    }

    suspend fun createOrganizationSecret(name: String) =
        secretService.createSecret(name, "value", null, OrganizationId(organization.id))

    "createForId" should {
        "create an infrastructure service for an organization" {
            val userSecret = createOrganizationSecret("user")
            val passSecret = createOrganizationSecret("pass")

            val createResult = service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                userSecret.name,
                passSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            createResult shouldBe InfrastructureService(
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                userSecret,
                passSecret,
                organization,
                null,
                null,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )
        }

        "throw an exception if a secret reference cannot be resolved" {
            val userSecret = createOrganizationSecret("user")

            val exception = shouldThrow<InvalidSecretReferenceException> {
                service.createForId(
                    OrganizationId(organization.id),
                    SERVICE_NAME,
                    SERVICE_URL,
                    SERVICE_DESC,
                    userSecret.name,
                    "pass",
                    emptySet()
                )
            }

            exception.message shouldContain "pass"
        }
    }

    "updateForId" should {
        "update the properties of a service" {
            val userSecret = createOrganizationSecret("user")
            val passSecret = createOrganizationSecret("pass")
            val newUserSecret = createOrganizationSecret("newUser")
            val newpassSecret = createOrganizationSecret("newPass")

            service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                userSecret.name,
                passSecret.name,
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
                newUserSecret,
                newpassSecret,
                organization,
                null,
                null,
                EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE)
            )
        }

        "throw an exception if a secret reference cannot be resolved" {
            shouldThrow<InvalidSecretReferenceException> {
                service.updateForId(
                    OrganizationId(organization.id),
                    SERVICE_NAME,
                    OptionalValue.Absent,
                    SERVICE_DESC.asPresent(),
                    "someNonExistingSecret".asPresent(),
                    OptionalValue.Absent,
                    OptionalValue.Absent
                )
            }
        }
    }

    "deleteForId" should {
        "delete an infrastructure service" {
            val userSecret = createOrganizationSecret("user")
            val passSecret = createOrganizationSecret("pass")

            service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                userSecret.name,
                passSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            service.deleteForId(OrganizationId(organization.id), SERVICE_NAME)

            service.getForId(OrganizationId(organization.id), SERVICE_NAME) should beNull()
        }
    }

    "getForId" should {
        "return an infrastructure service" {
            val userSecret = createOrganizationSecret("user")
            val passSecret = createOrganizationSecret("pass")

            val infrastructureService = service.createForId(
                OrganizationId(organization.id),
                SERVICE_NAME,
                SERVICE_URL,
                SERVICE_DESC,
                userSecret.name,
                passSecret.name,
                EnumSet.of(CredentialsType.NETRC_FILE)
            )

            service.getForId(OrganizationId(organization.id), SERVICE_NAME) shouldBe infrastructureService
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
    }
})
