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

package org.eclipse.apoapsis.ortserver.services

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager

class InfrastructureServiceServiceTest : WordSpec({
    beforeSpec {
        mockkStatic(
            "org.eclipse.apoapsis.ortserver.dao.DatabaseKt",
            "org.jetbrains.exposed.sql.transactions.TransactionApiKt"
        )
    }

    afterSpec {
        unmockkAll()
    }

    "createForOrganization" should {
        "create an infrastructure service for an organization" {
            testWithHelper {
                val userSecret = mockOrganizationSecret(USERNAME_SECRET)
                val passSecret = mockOrganizationSecret(PASSWORD_SECRET)

                val infrastructureService = mockk<InfrastructureService>()
                every {
                    repository.create(
                        SERVICE_NAME,
                        SERVICE_URL,
                        SERVICE_DESC,
                        userSecret,
                        passSecret,
                        EnumSet.of(CredentialsType.NETRC_FILE),
                        ORGANIZATION_ID,
                        null
                    )
                } returns infrastructureService

                val createResult = service.createForOrganization(
                    ORGANIZATION_ID,
                    SERVICE_NAME,
                    SERVICE_URL,
                    SERVICE_DESC,
                    USERNAME_SECRET,
                    PASSWORD_SECRET,
                    EnumSet.of(CredentialsType.NETRC_FILE)
                )

                createResult shouldBe infrastructureService
            }
        }

        "throw an exception if a secret reference cannot be resolved" {
            testWithHelper(verifyTx = false) {
                mockOrganizationSecret(USERNAME_SECRET)
                coEvery {
                    secretService.getSecretByIdAndName(OrganizationId(ORGANIZATION_ID), PASSWORD_SECRET)
                } returns null

                val exception = shouldThrow<InvalidSecretReferenceException> {
                    service.createForOrganization(
                        ORGANIZATION_ID,
                        SERVICE_NAME,
                        SERVICE_URL,
                        SERVICE_DESC,
                        USERNAME_SECRET,
                        PASSWORD_SECRET,
                        emptySet()
                    )
                }

                exception.message shouldContain PASSWORD_SECRET
            }
        }
    }

    "updateForOrganization" should {
        "update the properties of a service" {
            testWithHelper {
                val userSecret = mockOrganizationSecret(USERNAME_SECRET)
                val passSecret = mockOrganizationSecret(PASSWORD_SECRET)

                val infrastructureService = mockk<InfrastructureService>()
                every {
                    repository.updateForOrganizationAndName(
                        ORGANIZATION_ID,
                        SERVICE_NAME,
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns infrastructureService

                val updateResult = service.updateForOrganization(
                    ORGANIZATION_ID,
                    SERVICE_NAME,
                    SERVICE_URL.asPresent(),
                    SERVICE_DESC.asPresent(),
                    USERNAME_SECRET.asPresent(),
                    PASSWORD_SECRET.asPresent(),
                    EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE).asPresent()
                )

                updateResult shouldBe infrastructureService

                val slotUrl = slot<OptionalValue<String>>()
                val slotDescription = slot<OptionalValue<String?>>()
                val slotUsernameSecret = slot<OptionalValue<Secret>>()
                val slotPasswordSecret = slot<OptionalValue<Secret>>()
                val slotCredentialsType = slot<OptionalValue<Set<CredentialsType>>>()
                verify {
                    repository.updateForOrganizationAndName(
                        ORGANIZATION_ID,
                        SERVICE_NAME,
                        capture(slotUrl),
                        capture(slotDescription),
                        capture(slotUsernameSecret),
                        capture(slotPasswordSecret),
                        capture(slotCredentialsType)
                    )
                }

                equalsOptionalValues(SERVICE_URL.asPresent(), slotUrl.captured) shouldBe true
                equalsOptionalValues(SERVICE_DESC.asPresent(), slotDescription.captured) shouldBe true
                equalsOptionalValues(userSecret.asPresent(), slotUsernameSecret.captured) shouldBe true
                equalsOptionalValues(passSecret.asPresent(), slotPasswordSecret.captured) shouldBe true
                equalsOptionalValues(
                    EnumSet.of(CredentialsType.GIT_CREDENTIALS_FILE).asPresent(),
                    slotCredentialsType.captured
                ) shouldBe true
            }
        }

        "throw an exception if a secret reference cannot be resolved" {
            testWithHelper(verifyTx = false) {
                coEvery { secretService.getSecretByIdAndName(any(), any()) } returns null

                shouldThrow<InvalidSecretReferenceException> {
                    service.updateForOrganization(
                        ORGANIZATION_ID,
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
    }

    "deleteForOrganization" should {
        "delete an infrastructure service" {
            testWithHelper {
                every { repository.deleteForOrganizationAndName(ORGANIZATION_ID, SERVICE_NAME) } just runs

                service.deleteForOrganization(ORGANIZATION_ID, SERVICE_NAME)

                verify {
                    repository.deleteForOrganizationAndName(ORGANIZATION_ID, SERVICE_NAME)
                }
            }
        }
    }

    "listForOrganization" should {
        "return a list with the infrastructure services of the organization" {
            val services = listOf<InfrastructureService>(mockk(), mockk(), mockk())
            val parameters = ListQueryParameters(limit = 7, offset = 11)
            val expectedResult = ListQueryResult(services, parameters, services.size.toLong())

            testWithHelper {
                every { repository.listForOrganization(ORGANIZATION_ID, parameters) } returns expectedResult

                val result = service.listForOrganization(ORGANIZATION_ID, parameters)

                result shouldBe expectedResult
            }
        }
    }
})

/**
 * Run a test defined by the given [block] which makes use of a [TestHelper] instance. [Optionally][verifyTx] verify
 * that a database transaction was used.
 */
private suspend fun testWithHelper(verifyTx: Boolean = true, block: suspend TestHelper.() -> Unit) {
    val helper = TestHelper()
    helper.block()
    if (verifyTx) {
        helper.verifyTransaction()
    }
}

/**
 * A test helper class managing an instance of the service under test and its dependencies.
 */
private class TestHelper(
    /** Mock for the database. */
    val db: Database = createDatabaseMock(),

    /** Mock for the repository for infrastructure services. */
    val repository: InfrastructureServiceRepository = mockk(),

    /** Mock for the secret service. */
    val secretService: SecretService = mockk(),

    /** The service under test. */
    val service: InfrastructureServiceService = InfrastructureServiceService(db, repository, secretService)
) {
    /**
     * Create a mock [Secret] and prepare the mock [SecretService] to return it when asked for a secret for the
     * test organization with the given [name].
     */
    fun mockOrganizationSecret(name: String): Secret {
        val secret = mockk<Secret>()
        coEvery {
            secretService.getSecretByIdAndName(OrganizationId(ORGANIZATION_ID), name)
        } returns secret

        return secret
    }

    /**
     * Verify that a database transaction was active.
     */
    fun verifyTransaction() {
        coVerify {
            db.dbQuery(any(), any(), any())
        }
    }
}

/**
 * Create a mock [Database]. The mock is prepared to expect invocations of the [Database.dbQuery] function. The
 * blocks passed to this function are directly executed.
 */
private fun createDatabaseMock(): Database =
    mockk {
        coEvery { dbQuery(any(), any(), any<Transaction.() -> Any>()) } answers {
            val block = arg<Transaction.() -> Any>(3)
            mockkTransaction { transaction { block() } }
        }
        every { transactionManager } returns mockk(relaxed = true)
    }

/**
 * Check whether the given optional values [v1] and [v2] are equal. This function is needed, since [OptionalValue]
 * does not implement equals().
 */
private fun <T> equalsOptionalValues(v1: OptionalValue<T>, v2: OptionalValue<T>): Boolean =
    when {
        v1 is OptionalValue.Present && v2 is OptionalValue.Present -> v1.value == v2.value
        v1 == OptionalValue.Absent && v2 == OptionalValue.Absent -> true
        else -> false
    }

private const val ORGANIZATION_ID = 1000L
private const val SERVICE_NAME = "TestInfrastructureService"
private const val SERVICE_URL = "https://repo.example.org/infra/test"
private const val SERVICE_DESC = "This is a test infrastructure service"
private const val USERNAME_SECRET = "myUserSecret"
private const val PASSWORD_SECRET = "myPassSecret"
