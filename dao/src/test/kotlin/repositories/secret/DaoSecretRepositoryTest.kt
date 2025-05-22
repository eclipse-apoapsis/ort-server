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

package org.eclipse.apoapsis.ortserver.dao.repositories.secret

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoSecretRepositoryTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    private lateinit var secretRepository: DaoSecretRepository
    private lateinit var fixtures: Fixtures

    private var organizationId = OrganizationId(-1L)
    private var productId = ProductId(-1L)
    private var repositoryId = RepositoryId(-1L)

    private val path = "https://secret-storage.com/ssh_host_rsa_key"
    private val name = "rsa certificate"
    private val description = "ssh rsa certificate"

    init {
        beforeEach {
            secretRepository = dbExtension.fixtures.secretRepository
            fixtures = dbExtension.fixtures

            organizationId = OrganizationId(fixtures.organization.id)
            productId = ProductId(fixtures.product.id)
            repositoryId = RepositoryId(fixtures.repository.id)
        }

        "create should create an entry in the database" {
            val name = "secret1"
            val secret = createSecret(name, organizationId)

            val dbEntry = secretRepository.getByIdAndName(organizationId, name)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe secret
        }

        "update should update an organization secret in the database" {
            val name = "secret2"
            val secret = createSecret(name, organizationId)

            secretRepository.updateForIdAndName(
                organizationId,
                name,
                description.asPresent()
            )

            val dbEntry = secretRepository.getByIdAndName(organizationId, name)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Secret(
                secret.id,
                path + name,
                name,
                description,
                fixtures.organization,
                null,
                null
            )
        }

        "update should update a product secret in the database" {
            val name = "secret3"
            val secret = createSecret(name, productId)

            secretRepository.updateForIdAndName(
                productId,
                name,
                description.asPresent()
            )

            val dbEntry = secretRepository.getByIdAndName(productId, name)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Secret(
                secret.id,
                path + name,
                name,
                description,
                null,
                fixtures.product,
                null
            )
        }

        "update should update a repository secret in the database" {
            val name = "secret2"
            val secret = createSecret(name, repositoryId)

            secretRepository.updateForIdAndName(
                repositoryId,
                name,
                description.asPresent()
            )

            val dbEntry = secretRepository.getByIdAndName(repositoryId, name)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Secret(
                secret.id,
                path + name,
                name,
                description,
                null,
                null,
                fixtures.repository
            )
        }

        "delete should delete the database entry" {
            val name = "secret3"
            createSecret(name, repositoryId)

            secretRepository.deleteForIdAndName(repositoryId, name)

            secretRepository.listForId(repositoryId).data shouldBe emptyList()
        }

        "Reading all secrets of specific type" should {
            "return all stored results for a specific organization" {
                val organizationSecret1 = createSecret("secret4", organizationId)
                val organizationSecret2 = createSecret("secret5", organizationId)
                createSecret(
                    "secret6",
                    OrganizationId(fixtures.createOrganization("extra organization", "org description").id)
                )
                createSecret("productSecret1", productId)
                createSecret("repositorySecret1", repositoryId)

                secretRepository.listForId(organizationId).data should containExactlyInAnyOrder(
                    organizationSecret1,
                    organizationSecret2
                )
            }

            "return all stored results for a specific product" {
                val productSecret1 = createSecret("secret7", productId)
                val productSecret2 = createSecret("secret8", productId)
                createSecret(
                    "secret9",
                    ProductId(fixtures.createProduct("extra product", "prod description", fixtures.organization.id).id)
                )
                createSecret("organizationSecret1", organizationId)
                createSecret("repositorySecret2", repositoryId)

                secretRepository.listForId(productId).data should containExactlyInAnyOrder(
                    productSecret1,
                    productSecret2
                )
            }

            "return all stored results for a specific repository" {
                val repositorySecret1 = createSecret("secret10", repositoryId)
                val repositorySecret2 = createSecret("secret11", repositoryId)
                createSecret(
                    "secret12",
                    RepositoryId(
                        fixtures.createRepository(RepositoryType.GIT, "repo description", fixtures.product.id).id
                    )
                )
                createSecret("organizationSecret2", organizationId)
                createSecret("productSecret2", organizationId)

                secretRepository.listForId(repositoryId).data should containExactlyInAnyOrder(
                    repositorySecret1,
                    repositorySecret2
                )
            }
        }
    }

    private fun createSecret(name: String, id: HierarchyId) =
        secretRepository.create("$path$name", name, description, id)
}
