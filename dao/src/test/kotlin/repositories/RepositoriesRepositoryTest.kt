/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.test.repositories

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.dao.repositories.ProductsRepository
import org.ossreviewtoolkit.server.dao.repositories.RepositoriesRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.CreateProduct
import org.ossreviewtoolkit.server.shared.models.api.CreateRepository
import org.ossreviewtoolkit.server.shared.models.api.Repository
import org.ossreviewtoolkit.server.shared.models.api.RepositoryType
import org.ossreviewtoolkit.server.shared.models.api.UpdateRepository
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class RepositoriesRepositoryTest : DatabaseTest() {
    private var orgId = -1L
    private var productId = -1L

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()

        orgId = OrganizationsRepository.createOrganization(
            CreateOrganization(name = "org", description = "org description")
        ).id

        productId = ProductsRepository.createProduct(
            orgId,
            CreateProduct(name = "product", description = "product description")
        ).id
    }

    init {
        test("createRepository should create an entry in the database") {
            val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")

            val createdRepository = RepositoriesRepository.createRepository(productId, repository)

            val dbEntry = RepositoriesRepository.getRepository(createdRepository.id)

            dbEntry.shouldNotBeNull()
            dbEntry.mapToApiModel() shouldBe Repository(createdRepository.id, repository.type, repository.url)
        }

        test("createRepository should throw an exception if a repository with the same url exists") {
            val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")

            RepositoriesRepository.createRepository(productId, repository)

            shouldThrow<UniqueConstraintException> {
                RepositoriesRepository.createRepository(productId, repository)
            }
        }

        test("listRepositoriesForProduct should return all repositories for a product") {
            val repository1 = CreateRepository(RepositoryType.GIT, "https://example.com/repo1.git")
            val repository2 = CreateRepository(RepositoryType.GIT, "https://example.com/repo2.git")

            val createdRepository1 = RepositoriesRepository.createRepository(productId, repository1)
            val createdRepository2 = RepositoriesRepository.createRepository(productId, repository2)

            RepositoriesRepository.listRepositoriesForProduct(productId).map { it.mapToApiModel() } shouldBe listOf(
                Repository(createdRepository1.id, repository1.type, repository1.url),
                Repository(createdRepository2.id, repository2.type, repository2.url)
            )
        }

        test("updateRepository should update an entry in the database") {
            val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")

            val createdRepository = RepositoriesRepository.createRepository(productId, repository)

            val updateRepository = UpdateRepository(
                OptionalValue.Present(RepositoryType.SUBVERSION),
                OptionalValue.Present("https://svn.example.com/repos/org/repo/trunk")
            )

            val updateRepositoryResult = RepositoriesRepository.updateRepository(createdRepository.id, updateRepository)

            updateRepositoryResult.mapToApiModel() shouldBe Repository(
                createdRepository.id,
                (updateRepository.type as OptionalValue.Present).value,
                (updateRepository.url as OptionalValue.Present).value
            )
        }

        test("updateRepository should throw an exception if a repository with the same url exists") {
            val repository1 = CreateRepository(RepositoryType.GIT, "https://example.com/repo1.git")
            val repository2 = CreateRepository(RepositoryType.GIT, "https://example.com/repo2.git")

            RepositoriesRepository.createRepository(productId, repository1)
            val createdRepository2 = RepositoriesRepository.createRepository(productId, repository2)

            val updateRepository = UpdateRepository(
                OptionalValue.Present(RepositoryType.GIT),
                OptionalValue.Present(repository1.url)
            )

            shouldThrow<UniqueConstraintException> {
                RepositoriesRepository.updateRepository(createdRepository2.id, updateRepository)
            }
        }

        test("deleteRepository should delete the database entry") {
            val repository = CreateRepository(RepositoryType.GIT, "https://example.com/repo.git")

            val createdRepository = RepositoriesRepository.createRepository(productId, repository)

            RepositoriesRepository.deleteRepository(createdRepository.id)

            RepositoriesRepository.listRepositoriesForProduct(productId) shouldBe emptyList()
        }
    }
}
