/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.services

import io.kotest.core.spec.style.WordSpec

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures
import org.ossreviewtoolkit.server.model.RepositoryType

class ProductServiceTest : WordSpec({
    lateinit var db: Database
    lateinit var productRepository: DaoProductRepository
    lateinit var repositoryRepository: DaoRepositoryRepository

    lateinit var fixtures: Fixtures

    extension(
        DatabaseTestExtension {
            db = it
            productRepository = DaoProductRepository(db)
            repositoryRepository = DaoRepositoryRepository(db)
            fixtures = Fixtures(db)
        }
    )

    "createRepository" should {
        "create Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { createRepositoryPermissions(any()) } just runs
            }

            val service = ProductService(db, productRepository, repositoryRepository, authorizationService)
            val repository =
                service.createRepository(RepositoryType.GIT, "https://example.com/repo.git", fixtures.product.id)

            coVerify(exactly = 1) {
                authorizationService.createRepositoryPermissions(repository.id)
            }
        }
    }

    "deleteProduct" should {
        "delete Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { deleteProductPermissions(any()) } just runs
            }

            val service = ProductService(db, productRepository, repositoryRepository, authorizationService)
            service.deleteProduct(fixtures.product.id)

            coVerify(exactly = 1) {
                authorizationService.deleteProductPermissions(fixtures.product.id)
            }
        }
    }
})
