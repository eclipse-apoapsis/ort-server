/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.migrations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseMigrationTestExtension

@Suppress("ClassNaming")
class V65__removeUserInfoFromRepositoryUrlTest : StringSpec() {
    val extension = extension(DatabaseMigrationTestExtension("64", "101"))

    init {
        "repository migration should remove user information from the repository URL" {
            val product1 = extension.fixtures.createProduct(name = "product1")
            val product2 = extension.fixtures.createProduct(name = "product2")
            val repository1 = extension.fixtures.createRepository(
                url = "https://username:password@github.com/org/repo.git",
                productId = product1.id,
                description = null
            )
            val repository2 = extension.fixtures.createRepository(
                url = "https://username@github.com/org/repo.git",
                productId = product2.id,
                description = null
            )

            extension.testAppliedMigration {
                val cleanRepository1 = extension.fixtures.repositoryRepository.get(repository1.id).shouldNotBeNull()
                val cleanRepository2 = extension.fixtures.repositoryRepository.get(repository2.id).shouldNotBeNull()

                cleanRepository1.url shouldBe "https://xxx@github.com/org/repo.git"
                cleanRepository2.url shouldBe "https://xxx@github.com/org/repo.git"
            }
        }

        "repository migration should handle duplicate repository URLs" {
            val product1 = extension.fixtures.createProduct(name = "product1")
            val repository1 = extension.fixtures.createRepository(
                url = "https://username:password@github.com/org/repo.git",
                productId = product1.id,
                description = null
            )
            val repository2 = extension.fixtures.createRepository(
                url = "https://otherUsername:password@github.com/org/repo.git",
                productId = product1.id,
                description = null
            )

            extension.testAppliedMigration {
                val cleanRepository1 = extension.fixtures.repositoryRepository.get(repository1.id).shouldNotBeNull()
                val cleanRepository2 = extension.fixtures.repositoryRepository.get(repository2.id).shouldNotBeNull()

                cleanRepository1.url shouldBe "https://xxx@github.com/org/repo.git"
                cleanRepository2.url shouldBe "https://xxx2@github.com/org/repo.git"
            }
        }
    }
}
