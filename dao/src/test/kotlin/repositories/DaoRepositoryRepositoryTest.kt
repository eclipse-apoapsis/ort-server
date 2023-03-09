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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.UniqueConstraintException
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.model.util.asPresent

class DaoRepositoryRepositoryTest : StringSpec() {
    private val repositoryRepository = DaoRepositoryRepository()

    private lateinit var fixtures: Fixtures
    private var productId = -1L

    init {
        extension(
            DatabaseTestExtension {
                fixtures = Fixtures()
                productId = fixtures.product.id
            }
        )

        "create should create an entry in the database" {
            val type = RepositoryType.GIT
            val url = "https://example.com/repo.git"

            val createdRepository = repositoryRepository.create(type, url, productId)

            val dbEntry = repositoryRepository.get(createdRepository.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Repository(createdRepository.id, type, url)
        }

        "create should throw an exception if a repository with the same url exists" {
            val type = RepositoryType.GIT
            val url = "https://example.com/repo.git"

            repositoryRepository.create(type, url, productId)

            shouldThrow<UniqueConstraintException> {
                repositoryRepository.create(type, url, productId)
            }
        }

        "listForProduct should return all repositories for a product" {
            val type = RepositoryType.GIT

            val url1 = "https://example.com/repo1.git"
            val url2 = "https://example.com/repo2.git"

            val createdRepository1 = repositoryRepository.create(type, url1, productId)
            val createdRepository2 = repositoryRepository.create(type, url2, productId)

            repositoryRepository.listForProduct(productId) shouldBe listOf(
                Repository(createdRepository1.id, type, url1),
                Repository(createdRepository2.id, type, url2)
            )
        }

        "update should update an entry in the database" {
            val createdRepository =
                repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

            val updateType = RepositoryType.SUBVERSION.asPresent()
            val updateUrl = "https://svn.example.com/repos/org/repo/trunk".asPresent()

            val updateRepositoryResult = repositoryRepository.update(createdRepository.id, updateType, updateUrl)

            updateRepositoryResult shouldBe Repository(
                id = createdRepository.id,
                type = updateType.value,
                url = updateUrl.value
            )

            repositoryRepository.get(createdRepository.id) shouldBe Repository(
                id = createdRepository.id,
                type = updateType.value,
                url = updateUrl.value,
            )
        }

        "update should throw an exception if a repository with the same url exists" {
            val type = RepositoryType.GIT

            val url1 = "https://example.com/repo1.git"
            val url2 = "https://example.com/repo2.git"

            repositoryRepository.create(type, url1, productId)
            val createdRepository2 = repositoryRepository.create(type, url2, productId)

            val updateType = OptionalValue.Absent
            val updateUrl = url1.asPresent()

            shouldThrow<UniqueConstraintException> {
                repositoryRepository.update(createdRepository2.id, updateType, updateUrl)
            }
        }

        "delete should delete the database entry" {
            val createdRepository =
                repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

            repositoryRepository.delete(createdRepository.id)

            repositoryRepository.listForProduct(productId) shouldBe emptyList()
        }

        "get should return null" {
            repositoryRepository.get(1L).shouldBeNull()
        }

        "get should return the repository" {
            val repository =
                repositoryRepository.create(RepositoryType.GIT, "https://example.com/repo.git", productId)

            repositoryRepository.get(repository.id) shouldBe repository
        }
    }
}
