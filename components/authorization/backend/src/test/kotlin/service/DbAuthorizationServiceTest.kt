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

package org.eclipse.apoapsis.ortserver.components.authorization.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.HierarchyPermissions
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryPermission
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.dao.dbQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.jetbrains.exposed.sql.insert

@Suppress("LargeClass")
class DbAuthorizationServiceTest : WordSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    /**
     * Create a test service instance.
     */
    private fun createService() = DbAuthorizationService(dbExtension.db)

    init {
        "getEffectiveRoles" should {
            "resolve a repository ID correctly" {
                val service = createService()

                val effectiveRole = service.getEffectiveRole(
                    USER_ID,
                    RepositoryId(dbExtension.fixtures.repository.id)
                )

                with(effectiveRole.elementId) {
                    organizationId?.value shouldBe dbExtension.fixtures.organization.id
                    productId?.value shouldBe dbExtension.fixtures.product.id
                    repositoryId?.value shouldBe dbExtension.fixtures.repository.id
                }
            }

            "resolve a product ID correctly" {
                val service = createService()

                val effectiveRole = service.getEffectiveRole(
                    USER_ID,
                    ProductId(dbExtension.fixtures.product.id)
                )

                with(effectiveRole.elementId) {
                    organizationId?.value shouldBe dbExtension.fixtures.organization.id
                    productId?.value shouldBe dbExtension.fixtures.product.id
                    repositoryId shouldBe null
                }
            }

            "resolve an organization ID correctly" {
                val service = createService()

                val effectiveRole = service.getEffectiveRole(
                    USER_ID,
                    OrganizationId(dbExtension.fixtures.organization.id)
                )

                with(effectiveRole.elementId) {
                    organizationId?.value shouldBe dbExtension.fixtures.organization.id
                    productId shouldBe null
                    repositoryId shouldBe null
                }
            }

            "throw an InvalidHierarchyIdException if resolving the product ID fails" {
                val service = createService()
                val missingProductId = ProductId(-1L)

                val exception = shouldThrow<InvalidHierarchyIdException> {
                    service.getEffectiveRole(
                        USER_ID,
                        missingProductId
                    )
                }

                exception.hierarchyId shouldBe missingProductId
            }

            "throw an IllegalHierarchyIdException if resolving the repository ID fails" {
                val service = createService()

                val missingRepositoryId = RepositoryId(-1L)

                val exception = shouldThrow<InvalidHierarchyIdException> {
                    service.getEffectiveRole(
                        USER_ID,
                        missingRepositoryId
                    )
                }

                exception.hierarchyId shouldBe missingRepositoryId
            }

            "return an object with no permissions for a user without role assignments" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = DbAuthorizationService(dbExtension.db)

                val effectiveRole = service.getEffectiveRole(
                    USER_ID,
                    repositoryCompoundId
                )

                effectiveRole.elementId shouldBe repositoryCompoundId
                checkPermissions(effectiveRole)
            }

            "correctly resolve permissions on repository level" {
                val service = createService()

                RepositoryRole.entries.forAll { role ->
                    val repository = dbExtension.fixtures.createRepository(
                        url = "https://example.com/testRepo_$role.git"
                    )

                    createAssignment(
                        organizationId = dbExtension.fixtures.organization.id,
                        productId = dbExtension.fixtures.product.id,
                        repositoryId = repository.id,
                        repositoryRole = role
                    )

                    val effectiveRole = service.getEffectiveRole(USER_ID, RepositoryId(repository.id))

                    checkPermissions(effectiveRole, role)
                }
            }

            "correctly resolve permissions on product level" {
                val service = createService()

                ProductRole.entries.forAll { role ->
                    val product = dbExtension.fixtures.createProduct("testProduct_$role")

                    createAssignment(
                        organizationId = dbExtension.fixtures.organization.id,
                        productId = product.id,
                        productRole = role
                    )

                    val effectiveRole = service.getEffectiveRole(USER_ID, ProductId(product.id))

                    checkPermissions(effectiveRole, role)
                }
            }

            "correctly resolve permissions on organization level" {
                val service = createService()

                OrganizationRole.entries.forAll { role ->
                    val org = dbExtension.fixtures.createOrganization("testOrg_$role")
                    createAssignment(
                        organizationId = org.id,
                        organizationRole = role
                    )

                    val effectiveRole = service.getEffectiveRole(
                        USER_ID,
                        OrganizationId(org.id)
                    )

                    checkPermissions(effectiveRole, role)
                }
            }

            "consider all roles in the hierarchy" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    organizationRole = OrganizationRole.ADMIN
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole, RepositoryRole.ADMIN)
            }

            "filter correctly by user ID" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    userId = "other-user",
                    organizationId = dbExtension.fixtures.organization.id,
                    organizationRole = OrganizationRole.ADMIN
                )
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.READER
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole, RepositoryRole.READER)
            }

            "filter correctly in the hierarchy" {
                val repositoryCompoundId = repositoryCompoundId()
                val organization2 = dbExtension.fixtures.createOrganization("otherOrg")
                val product2 = dbExtension.fixtures.createProduct("otherProduct")
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.READER
                )
                createAssignment(
                    organizationId = organization2.id,
                    organizationRole = OrganizationRole.ADMIN
                )
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = product2.id,
                    productRole = ProductRole.ADMIN
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole, RepositoryRole.READER)
            }

            "allow overriding assignments from higher levels" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.READER
                )
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    organizationRole = OrganizationRole.ADMIN
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole, RepositoryRole.ADMIN)
            }

            "support role assignments on super user level (without an organization ID)" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationRole = OrganizationRole.ADMIN
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole, RepositoryRole.ADMIN, expectedSuperuser = true)
            }

            "allow querying super users only" {
                val normalUser = "normal-user"
                createAssignment(
                    organizationRole = OrganizationRole.ADMIN
                )
                createAssignment(
                    userId = normalUser,
                    organizationId = dbExtension.fixtures.organization.id,
                    organizationRole = OrganizationRole.READER
                )
                val service = createService()

                val effectiveRoleNormal = service.getEffectiveRole(normalUser, CompoundHierarchyId.WILDCARD)
                val effectiveRoleSuper = service.getEffectiveRole(USER_ID, CompoundHierarchyId.WILDCARD)

                checkPermissions(effectiveRoleNormal)
                checkPermissions(effectiveRoleSuper, OrganizationRole.ADMIN, expectedSuperuser = true)
            }

            "not fail for invalid role names" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignmentForRoleNames(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRoleName = "INVALID_ROLE_NAME"
                )
                val service = createService()

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)

                checkPermissions(effectiveRole)
            }
        }

        "checkPermissions" should {
            "return null for missing permissions" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                val effectiveRole = service.checkPermissions(
                    USER_ID,
                    repositoryCompoundId,
                    HierarchyPermissions.permissions(RepositoryPermission.TRIGGER_ORT_RUN)
                )

                effectiveRole should beNull()
            }

            "return an EffectiveRole for permissions explicitly granted" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.WRITER
                )
                val service = createService()

                val effectiveRole = service.checkPermissions(
                    USER_ID,
                    repositoryCompoundId,
                    HierarchyPermissions.permissions(RepositoryPermission.READ)
                )

                effectiveRole shouldNotBeNull {
                    checkPermissions(this, expectedRepositoryPermissions = setOf(RepositoryPermission.READ))
                    elementId shouldBe repositoryCompoundId
                }
            }

            "return an EffectiveRole for permissions granted via a higher level" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    organizationRole = OrganizationRole.READER
                )
                val service = createService()

                val effectiveRole = service.checkPermissions(
                    USER_ID,
                    repositoryCompoundId,
                    HierarchyPermissions.permissions(RepositoryPermission.READ)
                )

                effectiveRole shouldNotBeNull {
                    checkPermissions(this, expectedRepositoryPermissions = setOf(RepositoryPermission.READ))
                    elementId shouldBe repositoryCompoundId
                }
            }

            "return an EffectiveRole for implicit permissions derived from a lower level" {
                val orgId = OrganizationId(dbExtension.fixtures.organization.id)
                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.WRITER
                )
                val service = createService()

                val effectiveRole = service.checkPermissions(
                    USER_ID,
                    orgId,
                    HierarchyPermissions.permissions(OrganizationPermission.READ)
                )

                effectiveRole shouldNotBeNull {
                    checkPermissions(this, expectedOrganizationPermissions = setOf(OrganizationPermission.READ))
                    elementId shouldBe CompoundHierarchyId.forOrganization(orgId)
                }
            }

            "return an EffectiveRole for superuser permissions" {
                val repositoryCompoundId = repositoryCompoundId()
                createAssignment(
                    organizationRole = OrganizationRole.ADMIN
                )
                val service = createService()

                val effectiveRole = service.checkPermissions(
                    USER_ID,
                    repositoryCompoundId,
                    HierarchyPermissions.permissions(RepositoryPermission.DELETE)
                )

                effectiveRole shouldNotBeNull {
                    checkPermissions(
                        this,
                        expectedRepositoryPermissions = setOf(RepositoryPermission.DELETE),
                        expectedSuperuser = true
                    )
                    elementId shouldBe repositoryCompoundId
                }
            }

            "throw an InvalidHierarchyIdException for an invalid hierarchy ID" {
                val service = createService()
                val invalidId = ProductId(-1L)

                val exception = shouldThrow<InvalidHierarchyIdException> {
                    service.checkPermissions(
                        USER_ID,
                        invalidId,
                        HierarchyPermissions.permissions(ProductPermission.READ)
                    )
                }

                exception.hierarchyId shouldBe invalidId
                exception.message shouldContain "Could not resolve hierarchy ID"
            }
        }

        "assignRole" should {
            "create a new role assignment on repository level" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)
                checkPermissions(effectiveRole, RepositoryRole.READER)

                val effectiveRoleProduct = service.getEffectiveRole(USER_ID, repositoryCompoundId.parent!!)
                checkPermissions(effectiveRoleProduct, ProductRole.READER)
            }

            "create a new role assignment on product level" {
                val productCompoundId = CompoundHierarchyId.forProduct(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id)
                )
                val service = createService()

                service.assignRole(
                    USER_ID,
                    ProductRole.WRITER,
                    productCompoundId
                )

                val effectiveRole = service.getEffectiveRole(USER_ID, productCompoundId)
                checkPermissions(effectiveRole, ProductRole.WRITER)

                val effectiveRoleOrg = service.getEffectiveRole(USER_ID, productCompoundId.parent!!)
                checkPermissions(effectiveRoleOrg, OrganizationRole.READER)
            }

            "create a new role assignment on organization level" {
                val organizationCompoundId = CompoundHierarchyId.forOrganization(
                    OrganizationId(dbExtension.fixtures.organization.id)
                )
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.WRITER,
                    organizationCompoundId
                )

                val effectiveRole = service.getEffectiveRole(USER_ID, organizationCompoundId)
                checkPermissions(effectiveRole, OrganizationRole.WRITER)

                val effectiveRoleRepo = service.getEffectiveRole(USER_ID, repositoryCompoundId())
                checkPermissions(effectiveRoleRepo, RepositoryRole.WRITER)
            }

            "create a new superuser role assignment" {
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.WILDCARD
                )

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId())
                checkPermissions(effectiveRole, RepositoryRole.ADMIN, expectedSuperuser = true)
            }

            "replace an already exiting assignment" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()
                service.assignRole(
                    USER_ID,
                    RepositoryRole.WRITER,
                    repositoryCompoundId
                )

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )

                val effectiveRole = service.getEffectiveRole(USER_ID, repositoryCompoundId)
                checkPermissions(effectiveRole, RepositoryRole.READER)
            }
        }

        "removeAssignment" should {
            "return false for a non-existing assignment" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                val result = service.removeAssignment(
                    USER_ID,
                    repositoryCompoundId
                )

                result shouldBe false
            }

            "remove existing assignments" {
                val repositoryCompoundId = repositoryCompoundId()
                val otherRepo = dbExtension.fixtures.createRepository(url = "https://example.com/other.git")

                createAssignment(
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = otherRepo.id,
                    repositoryRole = RepositoryRole.WRITER
                )
                createAssignment(
                    userId = "other-user",
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRole = RepositoryRole.WRITER
                )

                val compoundIds = listOfNotNull(
                    repositoryCompoundId,
                    repositoryCompoundId.parent,
                    repositoryCompoundId.parent?.parent,
                    CompoundHierarchyId.WILDCARD
                )
                val service = createService()

                compoundIds.forAll { id ->
                    service.assignRole(
                        USER_ID,
                        OrganizationRole.ADMIN,
                        id
                    )

                    service.removeAssignment(USER_ID, id) shouldBe true

                    dbExtension.db.dbQuery {
                        RoleAssignmentsTable.select(RoleAssignmentsTable.id).count()
                    } shouldBe 2
                }
            }
        }

        "listUsersWithRole" should {
            "list users with a role on repository level" {
                val repositoryCompoundId = repositoryCompoundId()

                checkListUsersWithRole(repositoryCompoundId, RepositoryRole.READER)
            }

            "list users with a role on product level" {
                val productCompoundId = CompoundHierarchyId.forProduct(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id)
                )

                checkListUsersWithRole(productCompoundId, ProductRole.WRITER)
            }

            "list users with a role on organization level" {
                val organizationCompoundId = CompoundHierarchyId.forOrganization(
                    OrganizationId(dbExtension.fixtures.organization.id)
                )

                checkListUsersWithRole(organizationCompoundId, OrganizationRole.READER)
            }
        }

        "listUsers" should {
            "list users with assignments on repository level" {
                val repositoryCompoundId = repositoryCompoundId()
                val repository2 = dbExtension.fixtures.createRepository(url = "https://example.com/other.git")
                val product2 = dbExtension.fixtures.createProduct("otherProduct")
                val organization2 = dbExtension.fixtures.createOrganization("otherOrg")
                val writerUser = "writer-user"
                val productAdminUser = "product-admin-user"
                val organizationAdminUser = "organization-admin-user"
                val service = createService()

                service.assignRole(USER_ID, RepositoryRole.READER, repositoryCompoundId)
                service.assignRole(
                    "other-repo-user",
                    RepositoryRole.READER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(dbExtension.fixtures.organization.id),
                        ProductId(dbExtension.fixtures.product.id),
                        RepositoryId(repository2.id)
                    )
                )
                service.assignRole(
                    "other-product-user",
                    ProductRole.WRITER,
                    CompoundHierarchyId.forProduct(
                        OrganizationId(dbExtension.fixtures.organization.id),
                        ProductId(product2.id)
                    )
                )
                service.assignRole(
                    "other-organization-user",
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.forOrganization(OrganizationId(organization2.id))
                )
                service.assignRole(writerUser, RepositoryRole.WRITER, repositoryCompoundId)
                service.assignRole(
                    productAdminUser,
                    ProductRole.ADMIN,
                    repositoryCompoundId.parent!!
                )
                service.assignRole(
                    organizationAdminUser,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.forOrganization(OrganizationId(dbExtension.fixtures.organization.id))
                )

                val users = service.listUsers(repositoryCompoundId)
                users.keys shouldContainExactlyInAnyOrder listOf(
                    USER_ID,
                    writerUser,
                    productAdminUser,
                    organizationAdminUser
                )

                users[USER_ID] shouldBe RepositoryRole.READER
                users[writerUser] shouldBe RepositoryRole.WRITER
                users[productAdminUser] shouldBe RepositoryRole.ADMIN
                users[organizationAdminUser] shouldBe RepositoryRole.ADMIN
            }

            "list users with assignments on organization level" {
                val repositoryCompoundId = repositoryCompoundId()
                val organizationCompoundId = CompoundHierarchyId.forOrganization(
                    OrganizationId(dbExtension.fixtures.organization.id)
                )
                val writerUser = "writer-user"
                val adminUser = "admin-user"
                val repoReaderUser = "repo-reader-user"
                val service = createService()

                service.assignRole(USER_ID, OrganizationRole.READER, organizationCompoundId)
                service.assignRole(writerUser, OrganizationRole.WRITER, organizationCompoundId)
                service.assignRole(adminUser, OrganizationRole.ADMIN, organizationCompoundId)
                service.assignRole(repoReaderUser, RepositoryRole.READER, repositoryCompoundId)

                val users = service.listUsers(organizationCompoundId)
                users.keys shouldContainExactlyInAnyOrder listOf(
                    USER_ID,
                    writerUser,
                    adminUser,
                    repoReaderUser
                )

                users[USER_ID] shouldBe OrganizationRole.READER
                users[writerUser] shouldBe OrganizationRole.WRITER
                users[adminUser] shouldBe OrganizationRole.ADMIN
                users[repoReaderUser] shouldBe OrganizationRole.READER
            }

            "list users with implicit rights from lower levels" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(USER_ID, RepositoryRole.READER, repositoryCompoundId)

                val users = service.listUsers(repositoryCompoundId.parent!!)
                users shouldHaveSize 1

                users[USER_ID] shouldBe ProductRole.READER
            }

            "inherit roles from higher levels" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.WRITER,
                    CompoundHierarchyId.forOrganization(OrganizationId(dbExtension.fixtures.organization.id))
                )
                service.assignRole(USER_ID, RepositoryRole.READER, repositoryCompoundId)

                val users = service.listUsers(repositoryCompoundId)
                users shouldHaveSize 1

                users[USER_ID] shouldBe RepositoryRole.WRITER
            }

            "not include super users" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(USER_ID, RepositoryRole.READER, repositoryCompoundId)
                service.assignRole(
                    "super-user",
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.WILDCARD
                )

                val users = service.listUsers(repositoryCompoundId)
                users.keys shouldContainExactlyInAnyOrder listOf(USER_ID)
            }

            "not fail for invalid role names" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                createAssignmentForRoleNames(
                    userId = "invalid-role-user",
                    organizationId = dbExtension.fixtures.organization.id,
                    productId = dbExtension.fixtures.product.id,
                    repositoryId = dbExtension.fixtures.repository.id,
                    repositoryRoleName = "INVALID_ROLE_NAME"
                )
                service.assignRole(USER_ID, RepositoryRole.READER, repositoryCompoundId)

                val users = service.listUsers(repositoryCompoundId)

                users.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe USER_ID
                    value shouldBe RepositoryRole.READER
                }
            }
        }

        "filterHierarchyIds" should {
            "return a filter that includes the IDs of repositories a user has access to" {
                val repositoryCompoundId = repositoryCompoundId()
                val otherRepo = dbExtension.fixtures.createRepository(url = "https://example.com/other.git")
                val otherRepoId = CompoundHierarchyId.forRepository(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id),
                    RepositoryId(otherRepo.id)
                )
                val otherOrg = dbExtension.fixtures.createOrganization("otherOrg")
                val otherProduct = dbExtension.fixtures.createProduct("otherProduct", organizationId = otherOrg.id)
                val repoInOtherStructure = dbExtension.fixtures.createRepository(
                    url = "https://example.com/other-structure.git",
                    productId = otherProduct.id
                )
                val repoInOtherStructureId = CompoundHierarchyId.forRepository(
                    OrganizationId(otherOrg.id),
                    ProductId(otherProduct.id),
                    RepositoryId(repoInOtherStructure.id)
                )
                dbExtension.fixtures.createRepository(url = "https://example.com/forbidden.git")
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )
                service.assignRole(
                    USER_ID,
                    RepositoryRole.WRITER,
                    otherRepoId
                )
                service.assignRole(
                    USER_ID,
                    RepositoryRole.ADMIN,
                    repoInOtherStructureId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ)
                )

                filter.transitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.REPOSITORY_LEVEL
                    value shouldBe setOf(
                        repositoryCompoundId,
                        otherRepoId,
                        repoInOtherStructureId
                    )
                }
                filter.isWildcard shouldBe false
            }

            "return a filter that includes the IDs of organizations the user has access to" {
                val organizationId = CompoundHierarchyId.forOrganization(
                    OrganizationId(dbExtension.fixtures.organization.id)
                )
                val otherOrg = dbExtension.fixtures.createOrganization("otherOrg")
                val otherOrgId = CompoundHierarchyId.forOrganization(OrganizationId(otherOrg.id))
                dbExtension.fixtures.createOrganization("forbiddenOrg")
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.READER,
                    organizationId
                )
                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    otherOrgId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    requiredRole = OrganizationRole.READER
                )

                filter.transitiveIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactlyInAnyOrder setOf(
                    organizationId,
                    otherOrgId
                )
            }

            "only list IDs for which all required permissions are available" {
                val productId = CompoundHierarchyId.forProduct(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id)
                )
                val otherProduct = dbExtension.fixtures.createProduct("otherProduct")
                val otherProductId = CompoundHierarchyId.forProduct(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(otherProduct.id)
                )
                dbExtension.fixtures.createProduct("forbiddenProduct")
                val service = createService()

                service.assignRole(
                    USER_ID,
                    ProductRole.READER,
                    productId
                )
                service.assignRole(
                    USER_ID,
                    ProductRole.WRITER,
                    otherProductId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    productPermissions = setOf(ProductPermission.WRITE)
                )

                filter.transitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.PRODUCT_LEVEL
                    value shouldBe setOf(otherProductId)
                }
            }

            "handle role assignments on higher levels correctly" {
                val repositoryCompoundId = repositoryCompoundId()
                val productId = repositoryCompoundId.parent!!
                val otherOrg = dbExtension.fixtures.createOrganization("otherOrg")
                val otherOrgId = CompoundHierarchyId.forOrganization(OrganizationId(otherOrg.id))
                val otherProduct = dbExtension.fixtures.createProduct("otherProduct", organizationId = otherOrg.id)
                dbExtension.fixtures.createRepository(
                    url = "https://example.com/other.git",
                    productId = otherProduct.id
                )
                val thirdOrg = dbExtension.fixtures.createOrganization("thirdOrg")
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    otherOrgId
                )
                service.assignRole(
                    USER_ID,
                    ProductRole.ADMIN,
                    productId
                )
                service.assignRole(
                    USER_ID,
                    OrganizationRole.READER,
                    CompoundHierarchyId.forOrganization(OrganizationId(thirdOrg.id))
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.WRITE)
                )

                filter.transitiveIncludes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactlyInAnyOrder setOf(
                    productId
                )
                filter.transitiveIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactlyInAnyOrder setOf(
                    otherOrgId
                )
            }

            "drop IDs contained in others" {
                val repositoryCompoundId = repositoryCompoundId()
                val productId = repositoryCompoundId.parent!!
                val organizationId = productId.parent!!
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )
                service.assignRole(
                    USER_ID,
                    ProductRole.ADMIN,
                    productId
                )
                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    organizationId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ)
                )

                filter.transitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.ORGANIZATION_LEVEL
                    value shouldBe setOf(organizationId)
                }
            }

            "handle superuser assignments correctly" {
                val repositoryCompoundId = repositoryCompoundId()
                val productId = repositoryCompoundId.parent!!
                val organizationId = productId.parent!!
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.WILDCARD
                )
                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    organizationId
                )
                service.assignRole(
                    USER_ID,
                    ProductRole.ADMIN,
                    productId
                )
                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ)
                )

                filter.isWildcard shouldBe true
            }

            "correctly filter for the user ID" {
                val repositoryCompoundId = repositoryCompoundId()
                val otherRepo = dbExtension.fixtures.createRepository(url = "https://example.com/other.git")
                val otherRepoId = CompoundHierarchyId.forRepository(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id),
                    RepositoryId(otherRepo.id)
                )
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )
                service.assignRole(
                    "other-user",
                    RepositoryRole.READER,
                    otherRepoId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ)
                )

                filter.transitiveIncludes[CompoundHierarchyId.REPOSITORY_LEVEL] shouldContainExactly setOf(
                    repositoryCompoundId
                )
            }

            "apply a containedIn filter on product level" {
                val repositoryCompoundId = repositoryCompoundId()
                val otherProduct = dbExtension.fixtures.createProduct("otherProduct")
                val otherRepo = dbExtension.fixtures.createRepository(
                    url = "https://example.com/other.git",
                    productId = otherProduct.id
                )
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )
                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(dbExtension.fixtures.organization.id),
                        ProductId(otherProduct.id),
                        RepositoryId(otherRepo.id)
                    )
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ),
                    containedIn = repositoryCompoundId.productId
                )

                filter.transitiveIncludes[CompoundHierarchyId.REPOSITORY_LEVEL] shouldContainExactly setOf(
                    repositoryCompoundId
                )
            }

            "apply a containedIn filter on organization level" {
                val productId = CompoundHierarchyId.forProduct(
                    OrganizationId(dbExtension.fixtures.organization.id),
                    ProductId(dbExtension.fixtures.product.id)
                )
                val otherOrg = dbExtension.fixtures.createOrganization("otherOrg")
                val otherProduct = dbExtension.fixtures.createProduct("otherProduct", organizationId = otherOrg.id)
                val service = createService()

                service.assignRole(
                    USER_ID,
                    ProductRole.WRITER,
                    productId
                )
                service.assignRole(
                    USER_ID,
                    ProductRole.WRITER,
                    CompoundHierarchyId.forProduct(
                        OrganizationId(otherOrg.id),
                        ProductId(otherProduct.id)
                    )
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    productPermissions = setOf(ProductPermission.WRITE),
                    containedIn = productId.organizationId
                )

                filter.transitiveIncludes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactly setOf(productId)
            }

            "handle a containedIn filter together with a superuser assignment" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.WILDCARD
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ),
                    containedIn = repositoryCompoundId.productId
                )

                filter.isWildcard shouldBe false
                filter.transitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.PRODUCT_LEVEL
                    value shouldBe setOf(repositoryCompoundId.parent)
                }
            }

            "handle a containedIn filter together with permissive rights on a higher level" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(
                    USER_ID,
                    OrganizationRole.ADMIN,
                    CompoundHierarchyId.forOrganization(
                        OrganizationId(dbExtension.fixtures.organization.id)
                    )
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ),
                    containedIn = repositoryCompoundId.productId
                )

                filter.transitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.PRODUCT_LEVEL
                    value shouldBe setOf(repositoryCompoundId.parent)
                }
            }

            "find non-transitive includes" {
                val repositoryCompoundId = repositoryCompoundId()
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ)
                )

                filter.nonTransitiveIncludes[CompoundHierarchyId.PRODUCT_LEVEL] shouldContainExactly setOf(
                    repositoryCompoundId.parent
                )
                filter.nonTransitiveIncludes[CompoundHierarchyId.ORGANIZATION_LEVEL] shouldContainExactly setOf(
                    repositoryCompoundId.parent?.parent
                )
                filter.nonTransitiveIncludes shouldHaveSize 2
            }

            "apply a containedIn filter to non-transitive includes" {
                val repositoryCompoundId = repositoryCompoundId()
                val product2 = dbExtension.fixtures.createProduct("otherProduct")
                val repo2 = dbExtension.fixtures.createRepository(
                    url = "https://example.com/other.git",
                    productId = product2.id
                )
                val service = createService()

                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    repositoryCompoundId
                )
                service.assignRole(
                    USER_ID,
                    RepositoryRole.READER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(dbExtension.fixtures.organization.id),
                        ProductId(product2.id),
                        RepositoryId(repo2.id)
                    )
                )

                val filter = service.filterHierarchyIds(
                    USER_ID,
                    repositoryPermissions = setOf(RepositoryPermission.READ),
                    containedIn = repositoryCompoundId.productId
                )

                filter.nonTransitiveIncludes.entries.shouldBeSingleton { (key, value) ->
                    key shouldBe CompoundHierarchyId.PRODUCT_LEVEL
                    value shouldBe setOf(repositoryCompoundId.parent)
                }
            }
        }
    }

    /**
     * Create a [CompoundHierarchyId] for the test repository.
     */
    private fun repositoryCompoundId(): CompoundHierarchyId =
        CompoundHierarchyId.forRepository(
            organizationId = OrganizationId(dbExtension.fixtures.organization.id),
            productId = ProductId(dbExtension.fixtures.product.id),
            repositoryId = RepositoryId(dbExtension.fixtures.repository.id)
        )

    /**
     * Create a role assignment entity in the database for the given parameters.
     */
    private suspend fun createAssignment(
        userId: String = USER_ID,
        organizationId: Long? = null,
        productId: Long? = null,
        repositoryId: Long? = null,
        organizationRole: OrganizationRole? = null,
        productRole: ProductRole? = null,
        repositoryRole: RepositoryRole? = null
    ) {
        createAssignmentForRoleNames(
            userId,
            organizationId,
            productId,
            repositoryId,
            organizationRole?.name,
            productRole?.name,
            repositoryRole?.name
        )
    }

    /**
     * Create role assignment entity in the database for the given parameters using role names directly. This can be
     * used to test unexpected or invalid role names.
     */
    private suspend fun createAssignmentForRoleNames(
        userId: String = USER_ID,
        organizationId: Long? = null,
        productId: Long? = null,
        repositoryId: Long? = null,
        organizationRoleName: String? = null,
        productRoleName: String? = null,
        repositoryRoleName: String? = null
    ) {
        dbExtension.db.dbQuery {
            RoleAssignmentsTable.insert {
                it[RoleAssignmentsTable.userId] = userId
                it[RoleAssignmentsTable.organizationId] = organizationId
                it[RoleAssignmentsTable.productId] = productId
                it[RoleAssignmentsTable.repositoryId] = repositoryId
                it[RoleAssignmentsTable.organizationRole] = organizationRoleName
                it[RoleAssignmentsTable.productRole] = productRoleName
                it[RoleAssignmentsTable.repositoryRole] = repositoryRoleName
            }
        }
    }

    /**
     * Execute a test for listing the users with the given [role] on the specified [hierarchyId].
     */
    private suspend fun checkListUsersWithRole(hierarchyId: CompoundHierarchyId, role: Role) {
        val user2 = "test-user-2"
        val service = createService()

        service.assignRole(USER_ID, role, hierarchyId)
        service.assignRole(user2, role, hierarchyId)
        service.assignRole("other-role-user", OrganizationRole.ADMIN, hierarchyId)

        val otherId = hierarchyId.parent ?: CompoundHierarchyId.forOrganization(
            OrganizationId(dbExtension.fixtures.createOrganization("otherOrg").id)
        )
        service.assignRole("other-hierarchy-user", role, otherId)

        val users = service.listUsersWithRole(role, hierarchyId)
        users shouldContainExactlyInAnyOrder listOf(USER_ID, user2)
    }
}

/** The ID of a test user. */
private const val USER_ID = "test-user"

/**
 * Check that the given [effectiveRole] contains exactly the specified [expectedOrganizationPermissions],
 * [expectedProductPermissions], and [expectedRepositoryPermissions] on the different hierarchy levels. Also check the
 * [superuser][expectedSuperuser] flag.
 */
private fun checkPermissions(
    effectiveRole: EffectiveRole,
    expectedOrganizationPermissions: Set<OrganizationPermission> = emptySet(),
    expectedProductPermissions: Set<ProductPermission> = emptySet(),
    expectedRepositoryPermissions: Set<RepositoryPermission> = emptySet(),
    expectedSuperuser: Boolean = false
) {
    OrganizationPermission.entries.forAll {
        effectiveRole.hasOrganizationPermission(it) shouldBe (it in expectedOrganizationPermissions)
    }
    ProductPermission.entries.forAll {
        effectiveRole.hasProductPermission(it) shouldBe (it in expectedProductPermissions)
    }
    RepositoryPermission.entries.forAll {
        effectiveRole.hasRepositoryPermission(it) shouldBe (it in expectedRepositoryPermissions)
    }

    effectiveRole.isSuperuser shouldBe expectedSuperuser
}

/**
 * Check that the given [effectiveRole] contains exactly the permissions as defined by the given [expectedRole]. Also
 * check the [superuser][expectedSuperuser] flag.
 */
private fun checkPermissions(effectiveRole: EffectiveRole, expectedRole: Role, expectedSuperuser: Boolean = false) =
    checkPermissions(
        effectiveRole,
        expectedRole.organizationPermissions,
        expectedRole.productPermissions,
        expectedRole.repositoryPermissions,
        expectedSuperuser
    )
