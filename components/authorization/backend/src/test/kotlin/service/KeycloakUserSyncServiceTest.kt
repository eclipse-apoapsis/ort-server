/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientException
import org.eclipse.apoapsis.ortserver.clients.keycloak.User as KeycloakUser
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.RepositoryRole
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UserDao
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UserSyncStatusTable
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UsersTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.User

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class KeycloakUserSyncServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var keycloakClient: KeycloakClient
    lateinit var authorizationService: DbAuthorizationService

    /** Let Keycloak return the given [users] in the list of all users and find them by ID and username. */
    fun keycloakHas(vararg users: KeycloakUser) {
        coEvery { keycloakClient.getUsers(any()) } returns users.toSet()
        users.forEach { user ->
            coEvery { keycloakClient.findUser(user.id) } returns user
            coEvery { keycloakClient.findUser(user.username) } returns user
        }
    }

    /** Store a user that was created one hour ago, before any sync in these tests starts. */
    suspend fun storeUser(userId: String, username: String, fullName: String? = null) {
        dbExtension.db.transaction {
            UserDao.insertOrUpdate(User(userId, username, fullName))
            UsersTable.update({ UsersTable.id eq userId }) { it[createdAt] = Clock.System.now() - 1.hours }
        }
    }

    suspend fun assignOrganizationRole(username: String, userSub: String? = null) =
        authorizationService.assignRole(
            username,
            OrganizationRole.READER,
            CompoundHierarchyId.forOrganization(OrganizationId(dbExtension.fixtures.organization.id)),
            userSub
        )

    suspend fun assignProductRole(username: String, userSub: String? = null) =
        authorizationService.assignRole(
            username,
            ProductRole.READER,
            CompoundHierarchyId.forProduct(
                OrganizationId(dbExtension.fixtures.organization.id),
                ProductId(dbExtension.fixtures.product.id)
            ),
            userSub
        )

    suspend fun assignSuperuser(username: String, userSub: String? = null) =
        authorizationService.assignRole(username, OrganizationRole.ADMIN, CompoundHierarchyId.WILDCARD, userSub)

    suspend fun recordEarlierSuccess() =
        dbExtension.db.transaction { UserSyncStatusTable.recordSuccess(Clock.System.now() - 1.hours) }

    suspend fun storedUser(userId: String) = dbExtension.db.transaction { UserDao.findById(userId)?.mapToModel() }

    suspend fun assignedUsernames() = dbExtension.db.transaction {
        RoleAssignmentsTable.selectAll().map { it[RoleAssignmentsTable.userId] }
    }

    /** Return the username and the user ID of each role assignment. */
    suspend fun assignedUsers() = dbExtension.db.transaction {
        RoleAssignmentsTable.selectAll().map { it[RoleAssignmentsTable.userId] to it[RoleAssignmentsTable.userSub] }
    }

    suspend fun lastSuccess() = dbExtension.db.transaction { UserSyncStatusTable.lastSuccess() }

    fun createService(clock: Clock = Clock.System) =
        KeycloakUserSyncService(keycloakClient, dbExtension.db, clock = clock)

    beforeEach {
        keycloakClient = mockk {
            coEvery { findUser(any<UserId>()) } returns null
            coEvery { findUser(any<UserName>()) } returns null
        }

        authorizationService = DbAuthorizationService(dbExtension.db)
    }

    "sync" should {
        "store a new Keycloak user with username, full name and sync time" {
            val startTime = Instant.parse("2026-11-10T08:00:00Z")
            keycloakHas(keycloakUser("id-1", "john.doe", "John", "Doe"))

            val result = createService(clockOf(startTime, startTime + 1.minutes)).sync()

            storedUser("id-1") shouldBe User("id-1", "john.doe", "John Doe", lastSyncedAt = startTime)
            result shouldBe UserSyncResult(
                storedUsers = 1,
                newUsers = 1,
                deletedUsers = 0,
                removedRoleAssignments = 0,
                filledRoleAssignments = 0
            )
        }

        "mark a user deleted in Keycloak deleted and remove all their role assignments" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            storeUser("id-1", "john.doe")
            storeUser("id-2", "jane.doe", "Jane Doe")
            assignOrganizationRole("jane.doe")
            assignSuperuser("jane.doe")
            assignOrganizationRole("john.doe")

            val result = createService().sync()

            storedUser("id-2").shouldNotBeNull {
                username shouldBe "jane.doe"
                fullName shouldBe "Jane Doe"
                deletedAt shouldNot beNull()
            }

            assignedUsernames() should containExactlyInAnyOrder("john.doe")
            result.deletedUsers shouldBe 1
            result.removedRoleAssignments shouldBe 2
        }

        "store a renamed user under the new name and remove the role assignments under the old name" {
            keycloakHas(keycloakUser("id-1", "john.smith"))
            storeUser("id-1", "john.doe")
            assignOrganizationRole("john.doe")

            createService().sync()

            storedUser("id-1").shouldNotBeNull {
                username shouldBe "john.smith"
                deletedAt should beNull()
            }

            assignedUsernames() should beEmpty()
        }

        "keep the role assignments of a user who was deleted and created again with the same username" {
            keycloakHas(keycloakUser("new-id", "john.doe"))
            storeUser("old-id", "john.doe")
            assignOrganizationRole("john.doe")

            createService().sync()

            storedUser("old-id")?.deletedAt shouldNot beNull()
            storedUser("new-id")?.deletedAt should beNull()
            assignedUsernames() should containExactlyInAnyOrder("john.doe")
        }

        "clear the deletion mark of a user who exists in Keycloak again" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            storeUser("id-1", "john.doe")
            dbExtension.db.transaction { UserDao.markDeleted(listOf("id-1"), Clock.System.now()) }

            createService().sync()

            storedUser("id-1")?.deletedAt should beNull()
        }

        "not mark a user deleted who was stored after the sync started" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            dbExtension.db.transaction { UserDao.insertOrUpdate(User("id-2", "jane.doe")) }
            val startTime = Clock.System.now() - 1.hours

            createService(clockOf(startTime, startTime + 1.minutes)).sync()

            storedUser("id-2")?.deletedAt should beNull()
        }

        "keep and store a user missing from the list whom the direct lookup still finds" {
            val startTime = Instant.parse("2026-11-10T08:00:00Z")
            val missingUser = keycloakUser("id-2", "jane.doe", "Jane", "Doe")
            keycloakHas(keycloakUser("id-1", "john.doe"))
            coEvery { keycloakClient.findUser(missingUser.id) } returns missingUser
            coEvery { keycloakClient.findUser(missingUser.username) } returns missingUser
            storeUser("id-2", "jane.doe")
            assignOrganizationRole("jane.doe")

            val result = createService(clockOf(startTime, startTime + 1.minutes)).sync()

            storedUser("id-2") shouldBe User("id-2", "jane.doe", "Jane Doe", lastSyncedAt = startTime)
            assignedUsernames() should containExactlyInAnyOrder("jane.doe")
            result.storedUsers shouldBe 2
        }

        "store a user with role assignments but no stored row whom only the username lookup finds" {
            val missingUser = keycloakUser("id-2", "jane.doe")
            keycloakHas(keycloakUser("id-1", "john.doe"))
            coEvery { keycloakClient.findUser(missingUser.username) } returns missingUser
            assignOrganizationRole("jane.doe")

            val result = createService().sync()

            storedUser("id-2")?.username shouldBe "jane.doe"
            assignedUsernames() should containExactlyInAnyOrder("jane.doe")
            result.newUsers shouldBe 2
        }

        "keep a role that is assigned to a removed username while the sync runs" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            assignOrganizationRole("jane.doe")
            coEvery { keycloakClient.findUser(UserName("jane.doe")) } coAnswers {
                // The username is created again in Keycloak and gets a new role after the sync loaded the stored data.
                assignOrganizationRole("jane.doe")
                null
            }

            val result = createService().sync()

            assignedUsernames() should containExactlyInAnyOrder("jane.doe")
            result.removedRoleAssignments shouldBe 0
        }

        "record the end time as the last success and the start time as the sync time" {
            val startTime = Instant.parse("2026-11-10T08:00:00Z")
            val endTime = Instant.parse("2026-11-10T08:05:00Z")
            keycloakHas(keycloakUser("id-1", "john.doe"))

            createService(clockOf(startTime, endTime)).sync()

            lastSuccess() shouldBe endTime
            storedUser("id-1")?.lastSyncedAt shouldBe startTime
        }
    }

    "the user IDs of role assignments" should {
        "be filled in for every existing username" {
            keycloakHas(keycloakUser("id-1", "john.doe"), keycloakUser("id-2", "jane.doe"))
            assignOrganizationRole("john.doe")
            assignSuperuser("john.doe")
            assignOrganizationRole("jane.doe")

            val result = createService().sync()

            assignedUsers() should containExactlyInAnyOrder(
                "john.doe" to "id-1",
                "john.doe" to "id-1",
                "jane.doe" to "id-2"
            )
            result.filledRoleAssignments shouldBe 3
        }

        "be taken from the direct lookup, not from the list" {
            keycloakHas(keycloakUser("listed-id", "john.doe"))
            coEvery { keycloakClient.findUser(UserName("john.doe")) } returns keycloakUser("found-id", "john.doe")
            assignOrganizationRole("john.doe")

            createService().sync()

            assignedUsers() should containExactlyInAnyOrder("john.doe" to "found-id")
        }

        "be filled in for a username that only the direct lookup finds" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            coEvery { keycloakClient.findUser(UserName("jane.doe")) } returns keycloakUser("id-2", "jane.doe")
            assignOrganizationRole("jane.doe")

            createService().sync()

            assignedUsers() should containExactlyInAnyOrder("jane.doe" to "id-2")
        }

        "be filled in for two usernames whose direct lookups find the same user" {
            keycloakHas(keycloakUser("id-1", "john.doe"), keycloakUser("id-2", "jane.doe"))
            // The user with the first username is renamed to the second one between the two lookups.
            coEvery { keycloakClient.findUser(UserName("john.doe")) } returns keycloakUser("id-1", "john.doe")
            coEvery { keycloakClient.findUser(UserName("jane.doe")) } returns keycloakUser("id-1", "jane.doe")
            assignOrganizationRole("john.doe")
            assignOrganizationRole("jane.doe")

            val result = createService().sync()

            assignedUsers() should containExactlyInAnyOrder("john.doe" to "id-1", "jane.doe" to "id-1")
            result.filledRoleAssignments shouldBe 2
        }

        "remove role assignments with the ID of another user and keep those with the ID of the user" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            assignOrganizationRole("john.doe", "other-id")
            assignSuperuser("john.doe", "id-1")

            val result = createService().sync()

            assignedUsers() should containExactlyInAnyOrder("john.doe" to "id-1")
            result.removedRoleAssignments shouldBe 1
        }

        "remove the role assignments of a renamed user whose username another user has now" {
            keycloakHas(keycloakUser("id-1", "john.smith"), keycloakUser("id-2", "john.doe"))
            assignOrganizationRole("john.doe", "id-1")

            createService().sync()

            assignedUsers() should beEmpty()
        }

        "keep role assignments whose ID the direct lookup confirms although the list has another one" {
            keycloakHas(keycloakUser("listed-id", "john.doe"))
            coEvery { keycloakClient.findUser(UserName("john.doe")) } returns keycloakUser("id-1", "john.doe")
            assignOrganizationRole("john.doe", "id-1")

            createService().sync()

            assignedUsers() should containExactlyInAnyOrder("john.doe" to "id-1")
        }

        "remove all role assignments of a username in the list twice that the direct lookup does not find" {
            keycloakHas(keycloakUser("id-1", "john.doe"), keycloakUser("id-2", "john.doe"))
            coEvery { keycloakClient.findUser(UserName("john.doe")) } returns null
            assignOrganizationRole("john.doe")
            assignSuperuser("john.doe", "id-1")

            createService().sync()

            assignedUsers() should beEmpty()
        }

        "not require a direct lookup if all role assignments of a username have the ID in the list" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            assignOrganizationRole("john.doe", "id-1")

            createService().sync()

            coVerify(exactly = 0) { keycloakClient.findUser(UserName("john.doe")) }
        }

        "not change role assignments made while the sync runs" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            assignSuperuser("john.doe")
            coEvery { keycloakClient.findUser(UserName("john.doe")) } coAnswers {
                // Roles are assigned to the username after the sync loaded the stored data.
                assignOrganizationRole("john.doe")
                assignProductRole("john.doe", "other-id")
                keycloakUser("id-1", "john.doe")
            }

            val result = createService().sync()

            assignedUsers() should containExactlyInAnyOrder(
                "john.doe" to "id-1",
                "john.doe" to null,
                "john.doe" to "other-id"
            )
            result.filledRoleAssignments shouldBe 1
            result.removedRoleAssignments shouldBe 0
        }
    }

    "a failed sync" should {
        "change nothing and record no success if loading the users fails" {
            coEvery { keycloakClient.getUsers(any()) } throws KeycloakClientException("Failed to load users.")
            storeUser("id-1", "john.doe")
            assignOrganizationRole("john.doe")

            shouldThrow<KeycloakClientException> { createService().sync() }

            storedUser("id-1")?.deletedAt should beNull()
            assignedUsernames() should containExactlyInAnyOrder("john.doe")
            lastSuccess() should beNull()
        }

        "change nothing and record no success if a lookup by ID fails" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            coEvery { keycloakClient.findUser(UserId("id-2")) } throws KeycloakClientException("Failed.")
            storeUser("id-2", "jane.doe")

            shouldThrow<KeycloakClientException> { createService().sync() }

            storedUser("id-1") should beNull()
            storedUser("id-2")?.deletedAt should beNull()
            lastSuccess() should beNull()
        }

        "change nothing and record no success if a username search fails" {
            keycloakHas(keycloakUser("id-1", "john.doe"))
            coEvery { keycloakClient.findUser(UserName("jane.doe")) } throws KeycloakClientException("Failed.")
            assignOrganizationRole("jane.doe")

            shouldThrow<KeycloakClientException> { createService().sync() }

            storedUser("id-1") should beNull()
            assignedUsernames() should containExactlyInAnyOrder("jane.doe")
            lastSuccess() should beNull()
        }

        "change nothing if Keycloak returns no users while users are stored" {
            keycloakHas()
            storeUser("id-1", "john.doe")

            shouldThrow<UserSyncException> { createService().sync() }

            storedUser("id-1")?.deletedAt should beNull()
            lastSuccess() should beNull()
        }

        "change nothing if Keycloak returns no users while only role assignments exist" {
            keycloakHas()
            assignOrganizationRole("john.doe")

            shouldThrow<UserSyncException> { createService().sync() }

            assignedUsernames() should containExactlyInAnyOrder("john.doe")
            lastSuccess() should beNull()
        }
    }

    "the safety limits" should {
        "allow marking 5 of 20 users deleted, but not 6" {
            val users = (1..20).map { keycloakUser("id-$it", "user-$it") }
            users.forEach { storeUser(it.id.value, it.username.value) }

            keycloakHas(*users.drop(6).toTypedArray())
            shouldThrow<UserSyncException> { createService().sync() }
            storedUser("id-1")?.deletedAt should beNull()

            keycloakHas(*users.drop(5).toTypedArray())
            createService().sync().deletedUsers shouldBe 5
        }

        "allow marking 10 of 100 users deleted, but not 11" {
            val users = (1..100).map { keycloakUser("id-$it", "user-$it") }
            users.forEach { storeUser(it.id.value, it.username.value) }

            keycloakHas(*users.drop(11).toTypedArray())
            shouldThrow<UserSyncException> { createService().sync() }
            storedUser("id-1")?.deletedAt should beNull()

            keycloakHas(*users.drop(10).toTypedArray())
            createService().sync().deletedUsers shouldBe 10
        }

        "stop removing the role assignments of 6 of 20 usernames after an earlier successful sync" {
            val users = (1..20).map { keycloakUser("id-$it", "user-$it") }
            users.forEach { assignOrganizationRole(it.username.value) }
            recordEarlierSuccess()
            keycloakHas(*users.drop(6).toTypedArray())

            shouldThrow<UserSyncException> { createService().sync() }

            assignedUsernames().distinct().size shouldBe 20
        }

        "remove the role assignments of 6 of 20 usernames in the first sync" {
            val users = (1..20).map { keycloakUser("id-$it", "user-$it") }
            users.forEach { assignOrganizationRole(it.username.value) }
            keycloakHas(*users.drop(6).toTypedArray())

            val result = createService().sync()

            assignedUsernames().distinct().size shouldBe 14
            result.removedRoleAssignments shouldBe 6
        }

        "count a username with many role assignments once" {
            val users = (1..20).map { keycloakUser("id-$it", "user-$it") }
            users.forEach { assignOrganizationRole(it.username.value) }
            repeat(9) { index ->
                val repository = dbExtension.fixtures.createRepository(url = "https://example.com/repo-$index.git")
                authorizationService.assignRole(
                    "user-1",
                    RepositoryRole.READER,
                    CompoundHierarchyId.forRepository(
                        OrganizationId(dbExtension.fixtures.organization.id),
                        ProductId(dbExtension.fixtures.product.id),
                        RepositoryId(repository.id)
                    )
                )
            }

            recordEarlierSuccess()
            keycloakHas(*users.drop(1).toTypedArray())

            createService().sync().removedRoleAssignments shouldBe 10
        }

        "stop removing role assignments with the ID of another user of 6 of 20 usernames and fill in no ID" {
            val users = (1..20).map { keycloakUser("id-$it", "user-$it") }
            users.forEachIndexed { index, user ->
                assignOrganizationRole(user.username.value, if (index < 6) "other-id-$index" else null)
            }

            recordEarlierSuccess()
            keycloakHas(*users.toTypedArray())

            shouldThrow<UserSyncException> { createService().sync() }

            assignedUsers().count { it.second == null } shouldBe 14
            assignedUsers().size shouldBe 20
        }
    }
})

private fun keycloakUser(id: String, username: String, firstName: String? = null, lastName: String? = null) =
    KeycloakUser(UserId(id), UserName(username), firstName, lastName)

/** Return a [Clock] that returns the given [times] one after another, and the last one after that. */
private fun clockOf(vararg times: Instant): Clock {
    val remaining = ArrayDeque(times.map { it.toDatabasePrecision() })

    return object : Clock {
        override fun now(): Instant = if (remaining.size > 1) remaining.removeFirst() else remaining.first()
    }
}
