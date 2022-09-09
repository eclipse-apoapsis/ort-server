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
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.shared.models.api.CreateOrganization
import org.ossreviewtoolkit.server.shared.models.api.Organization
import org.ossreviewtoolkit.server.shared.models.api.UpdateOrganization
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class OrganizationsRepositoryTest : DatabaseTest() {
    override suspend fun beforeTest(testCase: TestCase) = dataSource.connect()

    init {
        test("createOrganization should create an entry in the database") {
            val org = CreateOrganization("MyOrg", "Description of MyOrg")

            val createdOrg = OrganizationsRepository.createOrganization(org)

            val dbEntry = OrganizationsRepository.getOrganization(createdOrg.id)

            dbEntry.shouldNotBeNull()
            dbEntry.mapToApiModel() shouldBe Organization(dbEntry.id, org.name, org.description)
        }

        test("listOrganizations should retrieve all entities from the database") {
            val org1 = CreateOrganization("org1", "description")
            val org2 = CreateOrganization("org2", "description")

            val createdOrg1 = OrganizationsRepository.createOrganization(org1)
            val createdOrg2 = OrganizationsRepository.createOrganization(org2)

            OrganizationsRepository.listOrganizations().map { it.mapToApiModel() } shouldBe listOf(
                Organization(createdOrg1.id, org1.name, org1.description),
                Organization(createdOrg2.id, org2.name, org2.description)
            )
        }

        test("updateOrganization should update an entity in the database") {
            val org = CreateOrganization(name = "org", description = "description")
            val createdOrg = OrganizationsRepository.createOrganization(org)

            val updatedOrg = UpdateOrganization(
                OptionalValue.Present("updatedOrg"),
                OptionalValue.Present("updated description")
            )

            OrganizationsRepository.updateOrganization(createdOrg.id, updatedOrg)

            OrganizationsRepository.getOrganization(createdOrg.id)?.mapToApiModel() shouldBe Organization(
                id = createdOrg.id,
                name = "updatedOrg",
                description = "updated description"
            )
        }

        test("updateOrganization should update null values and ignore absent values") {
            val org = CreateOrganization("org", "description")
            val createdOrg = OrganizationsRepository.createOrganization(org)

            val updatedOrg = UpdateOrganization(
                name = OptionalValue.Absent,
                description = OptionalValue.Present(null)
            )

            OrganizationsRepository.updateOrganization(createdOrg.id, updatedOrg)

            OrganizationsRepository.getOrganization(createdOrg.id)?.mapToApiModel() shouldBe Organization(
                id = createdOrg.id,
                name = org.name,
                description = null
            )
        }

        test("deleteOrganization should delete an entity in the database") {
            val org = CreateOrganization("org", "description")
            val createdOrg = OrganizationsRepository.createOrganization(org)

            OrganizationsRepository.deleteOrganization(createdOrg.id)

            OrganizationsRepository.listOrganizations() shouldBe emptyList()
        }
    }
}
