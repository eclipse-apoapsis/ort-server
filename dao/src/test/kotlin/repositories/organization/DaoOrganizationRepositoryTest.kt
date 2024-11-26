/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.organization

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.model.util.asPresent

class DaoOrganizationRepositoryTest : StringSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var organizationRepository: DaoOrganizationRepository

    beforeEach {
        organizationRepository = dbExtension.fixtures.organizationRepository
    }

    "create should create an entry in the database" {
        val name = "name"
        val description = "description"

        val createdOrg = organizationRepository.create(name, description)

        val dbEntry = organizationRepository.get(createdOrg.id)

        dbEntry.shouldNotBeNull()
        dbEntry shouldBe Organization(dbEntry.id, name, description)
    }

    "list should retrieve all entities from the database" {
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

    "list should apply parameters" {
        val name1 = "name1"
        val description1 = "description1"

        val name2 = "name2"
        val description2 = "description2"

        organizationRepository.create(name1, description1)
        val createdOrg2 = organizationRepository.create(name2, description2)

        val parameters = ListQueryParameters(
            sortFields = listOf(OrderField("name", OrderDirection.DESCENDING)),
            limit = 1
        )

        organizationRepository.list(parameters) shouldBe listOf(
            Organization(createdOrg2.id, name2, description2)
        )
    }

    "update should update an entity in the database" {
        val createdOrg = organizationRepository.create("name", "description")

        val updateName = "updatedName".asPresent()
        val updateDescription = "updatedDescription".asPresent()

        organizationRepository.update(createdOrg.id, updateName, updateDescription)

        organizationRepository.get(createdOrg.id) shouldBe Organization(
            id = createdOrg.id,
            name = updateName.value,
            description = updateDescription.value
        )
    }

    "update should update null values and ignore absent values" {
        val name = "name"

        val createdOrg = organizationRepository.create("name", "description")

        organizationRepository.update(
            id = createdOrg.id,
            name = OptionalValue.Absent,
            description = null.asPresent()
        )

        organizationRepository.get(createdOrg.id) shouldBe Organization(
            id = createdOrg.id,
            name = name,
            description = null
        )
    }

    "delete should delete an entity in the database" {
        val createdOrg = organizationRepository.create("name", "description")

        organizationRepository.delete(createdOrg.id)

        organizationRepository.list() shouldBe emptyList()
    }

    "get should return null" {
        organizationRepository.get(1L).shouldBeNull()
    }

    "get should return the organization" {
        val organization = organizationRepository.create("name", "description")

        organizationRepository.get(organization.id) shouldBe organization
    }
})
