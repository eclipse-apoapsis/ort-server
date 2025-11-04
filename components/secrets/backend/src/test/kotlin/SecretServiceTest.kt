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

package org.eclipse.apoapsis.ortserver.components.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.secrets.SecretValue
import org.eclipse.apoapsis.ortserver.secrets.SecretsProviderFactoryForTesting

import org.jetbrains.exposed.sql.Database

class SecretServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var fixtures: Fixtures
    lateinit var secretService: SecretService

    beforeEach {
        db = dbExtension.db
        fixtures = dbExtension.fixtures
        secretService = SecretService(
            db,
            fixtures.secretRepository,
            SecretStorage(SecretsProviderFactoryForTesting().createProvider())
        )
    }

    "getSecretValue" should {
        "return the value of a secret" {
            val secret = secretService.createSecret(
                "secret",
                "secret value",
                "description",
                OrganizationId(fixtures.organization.id)
            )

            secretService.getSecretValue(secret) shouldBe SecretValue("secret value")
        }

        "return null if the value is not found" {
            val secret = fixtures.secretRepository.create(
                "path",
                "name",
                "description",
                OrganizationId(fixtures.organization.id)
            )

            secretService.getSecretValue(secret) should beNull()
        }
    }

    "listForHierarchy" should {
        "return an empty list if there are no secrets" {
            val hierarchy = Hierarchy(
                repository = fixtures.repository,
                product = fixtures.product,
                organization = fixtures.organization
            )

            secretService.listForHierarchy(hierarchy) should beEmpty()
        }

        "return secrets from all levels of the hierarchy" {
            val hierarchy = Hierarchy(
                repository = fixtures.repository,
                product = fixtures.product,
                organization = fixtures.organization
            )

            secretService.createSecret(
                "organizationSecret",
                "value",
                "description",
                OrganizationId(hierarchy.organization.id)
            )
            secretService.createSecret(
                "productSecret",
                "value",
                "description",
                ProductId(fixtures.product.id)
            )
            secretService.createSecret(
                "repositorySecret",
                "value",
                "description",
                RepositoryId(fixtures.repository.id)
            )

            secretService.listForHierarchy(hierarchy).map { it.name } should
                    containExactlyInAnyOrder("organizationSecret", "productSecret", "repositorySecret")
        }

        "resolve conflicts for secrets with the same name correctly" {
            val hierarchy = Hierarchy(
                repository = fixtures.repository,
                product = fixtures.product,
                organization = fixtures.organization
            )

            // Create a secret on all levels.
            secretService.createSecret(
                "secret1",
                "value",
                "description",
                OrganizationId(hierarchy.organization.id)
            )
            secretService.createSecret(
                "secret1",
                "value",
                "description",
                ProductId(hierarchy.organization.id)
            )
            secretService.createSecret(
                "secret1",
                "value",
                "description",
                RepositoryId(hierarchy.organization.id)
            )

            // Create a secret on organization and product levels.
            secretService.createSecret(
                "secret2",
                "value",
                "description",
                OrganizationId(hierarchy.organization.id)
            )
            secretService.createSecret(
                "secret2",
                "value",
                "description",
                ProductId(hierarchy.product.id)
            )

            // Create a secret on organization and repository levels.
            secretService.createSecret(
                "secret3",
                "value",
                "description",
                OrganizationId(hierarchy.organization.id)
            )
            secretService.createSecret(
                "secret3",
                "value",
                "description",
                RepositoryId(hierarchy.repository.id)
            )

            // Create a secret on product and repository levels.
            secretService.createSecret(
                "secret4",
                "value",
                "description",
                ProductId(hierarchy.product.id)
            )
            secretService.createSecret(
                "secret4",
                "value",
                "description",
                RepositoryId(hierarchy.repository.id)
            )

            val secrets = secretService.listForHierarchy(hierarchy)

            secrets.find { it.name == "secret1" }.shouldNotBeNull {
                organizationId should beNull()
                productId should beNull()
                repositoryId shouldBe hierarchy.repository.id
            }

            secrets.find { it.name == "secret2" }.shouldNotBeNull {
                organizationId should beNull()
                productId shouldBe hierarchy.product.id
                repositoryId should beNull()
            }

            secrets.find { it.name == "secret3" }.shouldNotBeNull {
                organizationId should beNull()
                productId should beNull()
                repositoryId shouldBe hierarchy.repository.id
            }

            secrets.find { it.name == "secret4" }.shouldNotBeNull {
                organizationId should beNull()
                productId should beNull()
                repositoryId shouldBe hierarchy.repository.id
            }
        }
    }
})
