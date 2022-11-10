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

import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class DaoOrganizationRepositoryTest : DatabaseTest() {
    private val organizationRepository = DaoOrganizationRepository()

    override suspend fun beforeTest(testCase: TestCase) {
        dataSource.connect()
    }

    init {
        test("create should create an entry in the database") {
            val name = "name"
            val description = "description"

            val createdOrg = organizationRepository.create(name, description)

            val dbEntry = organizationRepository.get(createdOrg.id)

            dbEntry.shouldNotBeNull()
            dbEntry shouldBe Organization(dbEntry.id, name, description)
        }

        test("list should retrieve all entities from the database") {
            val name1 = "name1"
            val description1 = "description1"

            val name2 = "name2"
            val description2 = "description2"

            val createdOrg1 = organizationRepository.create(name1, description1)
            val createdOrg2 = organizationRepository.create(name2, description2)

            organizationRepository.list() shouldBe listOf(
                Organization(createdOrg1.id, name1, description1),
                Organization(createdOrg2.id, name2, description2)
            )
        }

        test("update should update an entity in the database") {
            val createdOrg = organizationRepository.create("name", "description")

            val updateName = OptionalValue.Present("updatedName")
            val updateDescription = OptionalValue.Present("updatedDescription")

            organizationRepository.update(createdOrg.id, updateName, updateDescription)

            organizationRepository.get(createdOrg.id) shouldBe Organization(
                id = createdOrg.id,
                name = updateName.value,
                description = updateDescription.value
            )
        }

        test("update should update null values and ignore absent values") {
            val name = "name"

            val createdOrg = organizationRepository.create("name", "description")

            organizationRepository.update(
                id = createdOrg.id,
                name = OptionalValue.Absent,
                description = OptionalValue.Present(null)
            )

            organizationRepository.get(createdOrg.id) shouldBe Organization(
                id = createdOrg.id,
                name = name,
                description = null
            )
        }

        test("delete should delete an entity in the database") {
            val createdOrg = organizationRepository.create("name", "description")

            organizationRepository.delete(createdOrg.id)

            organizationRepository.list() shouldBe emptyList()
        }
    }
}
