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

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.repositories.OrganizationsRepository
import org.ossreviewtoolkit.server.shared.models.api.Organization
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

class OrganizationsRepositoryTest : DatabaseTest() {
    init {
        test("createOrganization should create an entry in the database") {
            dataSource.connect()

            val org = Organization(name = "MyOrg", description = "Description of MyOrg")

            val createdOrg = OrganizationsRepository.createOrganization(org.name, org.description)

            createdOrg.shouldNotBeNull()
            val dbEntry = OrganizationsRepository.getOrganization(createdOrg.id)

            dbEntry.shouldNotBeNull()
            dbEntry.mapToApiModel() shouldBe org.copy(id = createdOrg.id)
        }

        test("listOrganizations should retrieve all entities from the database") {
            dataSource.connect()

            val org1 = Organization(name = "org1", description = "description")
            val org2 = Organization(name = "org2", description = "description")

            val createdOrg1 = OrganizationsRepository.createOrganization(org1.name, org1.description)
            val createdOrg2 = OrganizationsRepository.createOrganization(org2.name, org2.description)
            createdOrg1.shouldNotBeNull()
            createdOrg2.shouldNotBeNull()

            OrganizationsRepository.listOrganizations().map { it.mapToApiModel() } shouldBe listOf(
                org1.copy(id = createdOrg1.id),
                org2.copy(id = createdOrg2.id)
            )
        }

        test("updateOrganization should update an entity in the database") {
            dataSource.connect()

            val org = Organization(name = "org", description = "description")
            val createdOrg = OrganizationsRepository.createOrganization(org.name, org.description)
            createdOrg.shouldNotBeNull()

            val updatedOrg = Organization(name = "updatedOrg", description = "updated description")

            OrganizationsRepository.updateOrganization(createdOrg.id, updatedOrg.name, updatedOrg.description)

            OrganizationsRepository.getOrganization(createdOrg.id)?.mapToApiModel()
                .shouldBe(updatedOrg.copy(createdOrg.id))
        }

        test("deleteOrganization should delete an entity in the database") {
            dataSource.connect()

            val org = Organization(name = "org", description = "description")
            val createdOrg = OrganizationsRepository.createOrganization(org.name, org.description)
            createdOrg.shouldNotBeNull()

            OrganizationsRepository.deleteOrganization(createdOrg.id)

            OrganizationsRepository.listOrganizations() shouldBe emptyList()
        }
    }
}
