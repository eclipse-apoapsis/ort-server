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

import kotlin.time.Clock
import kotlin.time.Instant

import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClientException
import org.eclipse.apoapsis.ortserver.clients.keycloak.User as KeycloakUser
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserId
import org.eclipse.apoapsis.ortserver.clients.keycloak.UserName
import org.eclipse.apoapsis.ortserver.components.authorization.db.RoleAssignmentsTable
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UserDao
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UserSyncStatusTable
import org.eclipse.apoapsis.ortserver.dao.repositories.user.UsersTable
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.dao.utils.toDatabasePrecision
import org.eclipse.apoapsis.ortserver.model.User

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(KeycloakUserSyncService::class.java)

/**
 * A service that brings the stored users and the role assignments in line with the users in Keycloak.
 *
 * A sync stores every Keycloak user, marks stored users that no longer exist in Keycloak as deleted, and removes the
 * role assignments of usernames that no longer exist in Keycloak. Before it removes anything, it checks that the
 * answer from Keycloak is believable, and it asks Keycloak once more about every single user that it is about to
 * mark deleted or remove role assignments of. If any of this fails, it changes nothing.
 */
class KeycloakUserSyncService(
    /** The client to load the users from Keycloak. */
    private val keycloakClient: KeycloakClient,

    /** The database with the stored users and role assignments. */
    private val db: Database,

    /** The number of users that a single sync may always remove, regardless of [maxDeletedShare]. */
    private val maxDeletedUsers: Int = DEFAULT_MAX_DELETED_USERS,

    /** The share of users that a single sync may remove, if this is more than [maxDeletedUsers]. */
    private val maxDeletedShare: Double = DEFAULT_MAX_DELETED_SHARE,

    /** The number of users to request from Keycloak at once. */
    private val pageSize: Int = KeycloakClientConfiguration.DEFAULT_DATA_GET_CHUNK_SIZE,

    /** The clock to read the start and end times of a sync from. */
    private val clock: Clock = Clock.System
) {
    companion object {
        /** The default for [maxDeletedUsers]. */
        const val DEFAULT_MAX_DELETED_USERS = 5

        /** The default for [maxDeletedShare]. */
        const val DEFAULT_MAX_DELETED_SHARE = 0.1
    }

    /**
     * Sync the stored users and the role assignments with Keycloak and return what has changed. Throw a
     * [UserSyncException] if a safety check fails, or a [KeycloakClientException] if a request to Keycloak fails. In
     * both cases, nothing is changed.
     */
    suspend fun sync(): UserSyncResult {
        val startTime = clock.now().toDatabasePrecision()

        val keycloakUsers = keycloakClient.getUsers(pageSize)
        val usersToStore = keycloakUsers.map { it.toStoredUser() }
        val keycloakUserIds = usersToStore.mapTo(mutableSetOf()) { it.userId }
        val keycloakUsernames = usersToStore.mapTo(mutableSetOf()) { it.username }

        val state = db.transaction { loadState(startTime) }

        val userCandidates = state.usersCreatedBeforeStart.filterNot { it.userId in keycloakUserIds }
        val usernameCandidates = state.assignedUsernames - keycloakUsernames

        checkSafety(keycloakUsers.isEmpty(), state, userCandidates.size, usernameCandidates.size)

        // Users can be added, renamed or deleted while the pages are loaded, which can move other users between pages,
        // so that they are missing from the list. Only remove users of which Keycloak says that they do not exist, and
        // store the users that Keycloak still finds.
        val foundUsers = mutableListOf<User>()
        val usersToDelete = userCandidates.filter { candidate ->
            val user = keycloakClient.findUser(UserId(candidate.userId))
            user?.let { foundUsers += it.toStoredUser() }
            user == null
        }

        val usernamesToRemove = usernameCandidates.filter { username ->
            val user = keycloakClient.findUser(UserName(username))
            user?.let { foundUsers += it.toStoredUser() }
            user == null
        }

        // A user found by a lookup is newer than the same user in the list, so it replaces that one.
        val allUsersToStore = (usersToStore + foundUsers).associateBy { it.userId }.values

        // Only remove the role assignments that existed when the sync started. A username can be created again in
        // Keycloak and get new roles while the sync runs, and these new roles must stay.
        val assignmentIdsToRemove = usernamesToRemove.flatMap { state.assignmentIdsByUsername.getValue(it) }

        val removedRoleAssignments = db.transaction {
            UserDao.insertOrUpdateAll(allUsersToStore, startTime)
            UserDao.markDeleted(usersToDelete.map { it.userId }, startTime)
            val removed = removeRoleAssignments(assignmentIdsToRemove)
            UserSyncStatusTable.recordSuccess(clock.now())

            removed
        }

        usersToDelete.forEach { logger.info("Marked user '{}' ({}) deleted.", it.username, it.userId) }

        return UserSyncResult(
            storedUsers = allUsersToStore.size,
            newUsers = allUsersToStore.count { it.userId !in state.storedUserIds },
            deletedUsers = usersToDelete.size,
            removedRoleAssignments = removedRoleAssignments
        ).also { logger.info("Synced the users with Keycloak: {}.", it) }
    }

    /** Load the stored data that the sync compares with Keycloak, using [startTime] to find candidates for deletion. */
    private fun loadState(startTime: Instant) =
        StoredState(
            storedUserIds = UsersTable.select(UsersTable.id).mapTo(mutableSetOf()) { it[UsersTable.id].value },
            notDeletedUserCount = UsersTable.selectAll().where { UsersTable.deletedAt.isNull() }.count(),
            usersCreatedBeforeStart = UserDao.findNotDeletedCreatedBefore(startTime),
            assignmentIdsByUsername = RoleAssignmentsTable.select(RoleAssignmentsTable.id, RoleAssignmentsTable.userId)
                .groupBy({ it[RoleAssignmentsTable.userId] }, { it[RoleAssignmentsTable.id].value }),
            hasSucceededBefore = UserSyncStatusTable.lastSuccess() != null
        )

    /**
     * Throw a [UserSyncException] if the answer from Keycloak is not believable: if it is [empty][keycloakIsEmpty] but
     * there are stored users or role assignments, if more than the allowed number of the [stored users][state] would
     * be marked deleted, or, after an earlier successful sync, if more than the allowed number of usernames with
     * role assignments would lose them.
     */
    private fun checkSafety(keycloakIsEmpty: Boolean, state: StoredState, deletedUsers: Int, removedUsernames: Int) {
        if (keycloakIsEmpty && (state.notDeletedUserCount > 0 || state.assignedUsernames.isNotEmpty())) {
            throw UserSyncException(
                "Keycloak returned no users, but there are ${state.notDeletedUserCount} stored users and " +
                    "${state.assignedUsernames.size} usernames with role assignments. Check the Keycloak realm."
            )
        }

        if (exceedsLimit(deletedUsers, state.notDeletedUserCount.toInt())) {
            throw UserSyncException(
                "$deletedUsers of ${state.notDeletedUserCount} stored users would be marked deleted, which is more " +
                    "than allowed. To delete them anyway, raise the limits 'maxDeletedUsers' ($maxDeletedUsers) " +
                    "or 'maxDeletedShare' ($maxDeletedShare) and run the sync again."
            )
        }

        // The first sync is expected to remove many role assignments left behind by users deleted earlier.
        if (state.hasSucceededBefore && exceedsLimit(removedUsernames, state.assignedUsernames.size)) {
            throw UserSyncException(
                "$removedUsernames of ${state.assignedUsernames.size} usernames with role assignments would lose " +
                    "them, which is more than allowed. To remove them anyway, raise the limits 'maxDeletedUsers' " +
                    "($maxDeletedUsers) or 'maxDeletedShare' ($maxDeletedShare) and run the sync again."
            )
        }
    }

    /** Return whether [count] of [total] is more than both [maxDeletedUsers] and the [maxDeletedShare] of [total]. */
    private fun exceedsLimit(count: Int, total: Int) = count > maxDeletedUsers && count > total * maxDeletedShare

    /** Remove the role assignments with the given [ids], log each of them, and return how many were removed. */
    private fun removeRoleAssignments(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0

        RoleAssignmentsTable.selectAll().where { RoleAssignmentsTable.id inList ids }.forEach {
            logger.info(
                "Removing the role assignment of '{}' on organization {}, product {}, repository {}.",
                it[RoleAssignmentsTable.userId],
                it[RoleAssignmentsTable.organizationId]?.value,
                it[RoleAssignmentsTable.productId]?.value,
                it[RoleAssignmentsTable.repositoryId]?.value
            )
        }

        return RoleAssignmentsTable.deleteWhere { RoleAssignmentsTable.id inList ids }
    }
}

/** An exception thrown if a sync of the users with Keycloak stops because a safety check fails. */
class UserSyncException(message: String) : RuntimeException(message)

/** The stored data that a sync compares with the users in Keycloak. */
private class StoredState(
    /** The IDs of all stored users, including those marked deleted. */
    val storedUserIds: Set<String>,

    /** The number of stored users that are not marked deleted. */
    val notDeletedUserCount: Long,

    /** The stored users that are not marked deleted and were created before the sync started. */
    val usersCreatedBeforeStart: List<User>,

    /** The IDs of the role assignments of each username that has role assignments. */
    val assignmentIdsByUsername: Map<String, List<Long>>,

    /** Whether a sync has succeeded before. */
    val hasSucceededBefore: Boolean
) {
    /** The distinct usernames that have role assignments. */
    val assignedUsernames: Set<String> get() = assignmentIdsByUsername.keys
}

/**
 * Convert this Keycloak user to a stored user. The full name joins the first and last name with a space, as Keycloak
 * does for the `name` claim of its tokens.
 */
private fun KeycloakUser.toStoredUser() =
    User(
        userId = id.value,
        username = username.value,
        fullName = listOfNotNull(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null }
    )
