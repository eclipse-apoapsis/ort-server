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
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

class HierarchyPermissionsTest : WordSpec({
    "permissions" should {
        "return a checker for organization permissions" {
            val checker = HierarchyPermissions.permissions(
                OrganizationPermission.WRITE, OrganizationPermission.CREATE_PRODUCT
            )

            checker(OrganizationRole.ADMIN) shouldBe true
            checker(OrganizationRole.WRITER) shouldBe true
            checker(OrganizationRole.READER) shouldBe false
        }

        "return a checker for product permissions" {
          val checker = HierarchyPermissions.permissions(
              ProductPermission.WRITE, ProductPermission.CREATE_REPOSITORY, ProductPermission.TRIGGER_ORT_RUN
          )

            checker(ProductRole.ADMIN) shouldBe true
            checker(ProductRole.WRITER) shouldBe true
            checker(ProductRole.READER) shouldBe false
        }

        "return a checker for repository permissions" {
            val checker = HierarchyPermissions.permissions(
                RepositoryPermission.WRITE, RepositoryPermission.TRIGGER_ORT_RUN
            )

            checker(RepositoryRole.ADMIN) shouldBe true
            checker(RepositoryRole.WRITER) shouldBe true
            checker(RepositoryRole.READER) shouldBe false
        }

        "return a checker for the permissions of a role" {
            val checker = HierarchyPermissions.permissions(ProductRole.WRITER)

            checker(ProductRole.ADMIN) shouldBe true
            checker(ProductRole.WRITER) shouldBe true
            checker(ProductRole.READER) shouldBe false
            checker(RepositoryRole.ADMIN) shouldBe false
            checker(OrganizationRole.READER) shouldBe false
        }
    }

    "hasPermission" should {
        "return false if there is no matching role assignment for an element" {
            val id = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val permissions = HierarchyPermissions.create(
                emptyList(),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )

            permissions.hasPermission(id) shouldBe false
        }

        "return true if permissions are granted on a hierarchy element" {
            val repositoryId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            val permissions = HierarchyPermissions.create(
                listOf(repositoryId to RepositoryRole.READER),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )

            permissions.hasPermission(repositoryId) shouldBe true
        }

        "return true if permissions are inherited from a higher level in the hierarchy" {
            val organizationId = OrganizationId(1)
            val product1Id = ProductId(2)
            val product2Id = ProductId(3)
            val repository1Id = CompoundHierarchyId.forRepository(organizationId, product1Id, RepositoryId(4))
            val repository2Id = CompoundHierarchyId.forRepository(organizationId, product1Id, RepositoryId(5))
            val repository3Id = CompoundHierarchyId.forRepository(organizationId, product2Id, RepositoryId(6))

            val permissions = HierarchyPermissions.create(
                listOf(
                    CompoundHierarchyId.forProduct(organizationId, product1Id) to RepositoryRole.WRITER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.WRITE)
            )

            permissions.hasPermission(repository1Id) shouldBe true
            permissions.hasPermission(repository2Id) shouldBe true
            permissions.hasPermission(repository3Id) shouldBe false
        }

        "support widening permissions on lower levels in the hierarchy" {
            val organizationId = OrganizationId(1)
            val product1Id = ProductId(2)
            val repository1Id = CompoundHierarchyId.forRepository(organizationId, product1Id, RepositoryId(4))

            val permissions = HierarchyPermissions.create(
                listOf(
                    CompoundHierarchyId.forOrganization(organizationId) to RepositoryRole.READER,
                    repository1Id to RepositoryRole.WRITER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.WRITE)
            )

            permissions.hasPermission(repository1Id) shouldBe true
        }

        "override restricted roles on lower levels in the hierarchy" {
            val organizationId = OrganizationId(1)
            val product1Id = ProductId(2)
            val repository1Id = CompoundHierarchyId.forRepository(organizationId, product1Id, RepositoryId(4))

            val permissions = HierarchyPermissions.create(
                listOf(
                    CompoundHierarchyId.forOrganization(organizationId) to RepositoryRole.ADMIN,
                    repository1Id to RepositoryRole.READER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.WRITE)
            )

            permissions.hasPermission(repository1Id) shouldBe true
        }

        "take implicitly granted permissions into account" {
            val repoId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val productId = repoId.parent!!
            val orgId = productId.parent!!

            val permissions = HierarchyPermissions.create(
                listOf(
                    repoId to RepositoryRole.READER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )

            permissions.hasPermission(repoId) shouldBe true
            permissions.hasPermission(productId) shouldBe true
            permissions.hasPermission(orgId) shouldBe true
        }
    }

    "includes" should {
        "return the IDs for which a sufficient role assignment exists" {
            val id1 = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val id2 = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(4))
            val id3 = CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(5))
            val id4 = CompoundHierarchyId.forOrganization(OrganizationId(6))

            val permissions = HierarchyPermissions.create(
                listOf(
                    id1 to RepositoryRole.READER,
                    id2 to RepositoryRole.WRITER,
                    id3 to RepositoryRole.WRITER,
                    id4 to RepositoryRole.ADMIN
                ),
                HierarchyPermissions.permissions(RepositoryPermission.WRITE)
            )
            val includes = permissions.includes()

            includes shouldHaveSize 3
            includes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactlyInAnyOrder listOf(id4)
            includes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactlyInAnyOrder listOf(id3)
            includes[CompoundHierarchyId.REPOSITORY_LEVEL] shouldContainExactlyInAnyOrder listOf(id2)
        }

        "not contain elements that are already dominated by higher level assignments" {
            val orgId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val repo1Id = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val repo2Id = CompoundHierarchyId.forRepository(OrganizationId(4), ProductId(5), RepositoryId(6))

            val permissions = HierarchyPermissions.create(
                listOf(
                    orgId to RepositoryRole.READER,
                    repo1Id to RepositoryRole.READER,
                    repo2Id to RepositoryRole.WRITER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )
            val includes = permissions.includes()

            includes[CompoundHierarchyId.REPOSITORY_LEVEL] shouldContainExactlyInAnyOrder listOf(repo2Id)
        }
    }

    "implicitIncludes" should {
        "return the IDs of elements that are implicitly included by elements on lower levels" {
            val repoId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            val permissions = HierarchyPermissions.create(
                listOf(
                    repoId to RepositoryRole.READER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )
            val implicitIncludes = permissions.implicitIncludes()

            implicitIncludes shouldHaveSize 2
            implicitIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactlyInAnyOrder listOf(
                CompoundHierarchyId.forOrganization(OrganizationId(1))
            )
            implicitIncludes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactlyInAnyOrder listOf(repoId.parent)
        }

        "not return the IDs of elements dominated by higher level assignments" {
            val orgId = CompoundHierarchyId.forOrganization(OrganizationId(1))
            val repoId = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))

            val permissions = HierarchyPermissions.create(
                listOf(
                    orgId to RepositoryRole.READER,
                    repoId to RepositoryRole.READER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )
            val implicitIncludes = permissions.implicitIncludes()

            implicitIncludes shouldBe emptyMap()
        }

        "not return duplicate IDs" {
            val repo1Id = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            val repo2Id = CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(4))

            val permissions = HierarchyPermissions.create(
                listOf(
                    repo1Id to RepositoryRole.READER,
                    repo2Id to RepositoryRole.READER
                ),
                HierarchyPermissions.permissions(RepositoryPermission.READ)
            )
            val implicitIncludes = permissions.implicitIncludes()

            implicitIncludes shouldHaveSize 2
            implicitIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactlyInAnyOrder listOf(
                CompoundHierarchyId.forOrganization(OrganizationId(1))
            )
            implicitIncludes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactlyInAnyOrder listOf(repo1Id.parent)
        }
    }

    "create" should {
        "return only a superuser instance if a correct ADMIN role is available" {
            val permissions = HierarchyPermissions.create(
                listOf(
                    CompoundHierarchyId.WILDCARD to OrganizationRole.WRITER
                ),
                HierarchyPermissions.permissions(ProductPermission.READ)
            )

            permissions.isSuperuser() shouldBe false
        }
    }

    "the superuser instance" should {
        val superuserPermissions = HierarchyPermissions.create(
            listOf(
                CompoundHierarchyId.WILDCARD to OrganizationRole.ADMIN,
                CompoundHierarchyId.forOrganization(OrganizationId(1)) to OrganizationRole.READER
            ),
            HierarchyPermissions.permissions(ProductPermission.WRITE)
        )

        "always return true for hasPermission" {
            val ids = listOf(
                CompoundHierarchyId.forOrganization(OrganizationId(1)),
                CompoundHierarchyId.forProduct(OrganizationId(1), ProductId(2)),
                CompoundHierarchyId.forRepository(OrganizationId(1), ProductId(2), RepositoryId(3))
            )
            ids.forAll { superuserPermissions.hasPermission(it) shouldBe true }
        }

        "return a map with includes containing only the wildcard ID" {
            val includes = superuserPermissions.includes()

            includes.entries.shouldBeSingleton { (key, value) ->
                key shouldBe CompoundHierarchyId.WILDCARD_LEVEL
                value shouldBe listOf(CompoundHierarchyId.WILDCARD)
            }
        }

        "return an empty map for implicit includes" {
            superuserPermissions.implicitIncludes() should beEmpty()
        }

        "declare itself as superuser instance" {
            superuserPermissions.isSuperuser() shouldBe true
        }
    }
})
