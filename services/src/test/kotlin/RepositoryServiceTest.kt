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

import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.test.DatabaseTestExtension
import org.ossreviewtoolkit.server.dao.test.Fixtures

class RepositoryServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var db: Database
    lateinit var ortRunRepository: DaoOrtRunRepository
    lateinit var repositoryRepository: DaoRepositoryRepository
    lateinit var fixtures: Fixtures

    beforeEach {
        db = dbExtension.db
        ortRunRepository = dbExtension.fixtures.ortRunRepository
        repositoryRepository = dbExtension.fixtures.repositoryRepository
        fixtures = dbExtension.fixtures
    }

    "deleteRepository" should {
        "delete Keycloak permissions" {
            val authorizationService = mockk<AuthorizationService> {
                coEvery { deleteRepositoryPermissions(any()) } just runs
                coEvery { deleteRepositoryRoles(any()) } just runs
            }

            val service = RepositoryService(db, ortRunRepository, repositoryRepository, authorizationService)
            service.deleteRepository(fixtures.repository.id)

            coVerify(exactly = 1) {
                authorizationService.deleteRepositoryPermissions(fixtures.repository.id)
                authorizationService.deleteRepositoryRoles(fixtures.repository.id)
            }
        }
    }
})
