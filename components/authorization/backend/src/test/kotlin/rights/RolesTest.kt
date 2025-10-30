/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.authorization.rights

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId

class RolesTest : WordSpec({
    "getRoleByNameAndLevel" should {
        "return the correct role" {
            val allRoles = OrganizationRole.entries.toList() +
                ProductRole.entries.toList() +
                RepositoryRole.entries.toList()

            allRoles.forAll { role ->
                Role.getRoleByNameAndLevel(role.level, role.name) shouldBe role
            }
        }

        "return null for an invalid role name" {
            Role.getRoleByNameAndLevel(CompoundHierarchyId.ORGANIZATION_LEVEL, "GARDENER") shouldBe null
        }

        "return null for an invalid level" {
            Role.getRoleByNameAndLevel(CompoundHierarchyId.WILDCARD_LEVEL, "READER") shouldBe null
            Role.getRoleByNameAndLevel(1000, "READER") shouldBe null
        }
    }

    "level" should {
        "be correct for OrganizationRole" {
            OrganizationRole.entries.forAll { role ->
                role.level shouldBe CompoundHierarchyId.ORGANIZATION_LEVEL
            }
        }

        "be correct for ProductRole" {
            ProductRole.entries.forAll { role ->
                role.level shouldBe CompoundHierarchyId.PRODUCT_LEVEL
            }
        }

        "be correct for RepositoryRole" {
            RepositoryRole.entries.forAll { role ->
                role.level shouldBe CompoundHierarchyId.REPOSITORY_LEVEL
            }
        }
    }
})
