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

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(KeycloakUserSyncService::class.java)

/**
 * A service that brings the stored users and the role assignments in line with the users in Keycloak.
 *
 * A sync stores every Keycloak user, marks stored users that no longer exist in Keycloak as deleted, and removes the
 * role assignments of usernames that no longer exist in Keycloak. It also fills in the Keycloak ID of the user in
 * role assignments that have none, and removes role assignments stored with the ID of another user than the one who
 * has the username now. Before it removes anything, it checks that the answer from Keycloak is believable, and it asks
 * Keycloak once more about every single user that it is about to mark deleted, and about every username whose role
 * assignments it is about to change. If any of this fails, it changes nothing.
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
        val keycloakUserIdsByUsername = usersToStore.groupBy({ it.username }, { it.userId })

        val state = db.transaction { loadState(startTime) }

        val userCandidates = state.usersCreatedBeforeStart.filterNot { it.userId in keycloakUserIds }

        // A username is looked up directly if the list does not show exactly one user for it whose ID all its role
        // assignments already have. Users renamed while the pages are loaded can be missing from the list or appear
        // in it twice, so the ID of a user is only ever taken from a direct lookup.
        val usernameCandidates = state.assignmentsByUsername.filter { (username, assignments) ->
            val listedIds = keycloakUserIdsByUsername[username].orEmpty()
            listedIds.size != 1 || assignments.any { it.userSub != listedIds.single() }
        }.keys

        val usernamesLosingRolesByList = state.assignmentsByUsername.count { (username, assignments) ->
            val listedIds = keycloakUserIdsByUsername[username].orEmpty()
            listedIds.isEmpty() || assignments.any { it.userSub != null && it.userSub !in listedIds }
        }

        checkSafety(keycloakUsers.isEmpty(), state, userCandidates.size)
        checkRemovedUsernames(state, usernamesLosingRolesByList)

        // Users can be added, renamed or deleted while the pages are loaded, which can move other users between pages,
        // so that they are missing from the list. Only remove users of which Keycloak says that they do not exist, and
        // store the users that Keycloak still finds.
        val foundUsers = mutableListOf<User>()
        val usersToDelete = userCandidates.filter { candidate ->
            val user = keycloakClient.findUser(UserId(candidate.userId))
            user?.let { foundUsers += it.toStoredUser() }
            user == null
        }

        val assignmentChanges = lookUpAssignedUsernames(usernameCandidates, state)
        foundUsers += assignmentChanges.foundUsers

        // A lookup can find that a username in the list does not exist anymore, so check the limit again.
        checkRemovedUsernames(state, assignmentChanges.usernamesLosingRoles)

        // A user found by a lookup is newer than the same user in the list, so it replaces that one.
        val allUsersToStore = (usersToStore + foundUsers).associateBy { it.userId }.values

        val (filledRoleAssignments, removedRoleAssignments) = db.transaction {
            UserDao.insertOrUpdateAll(allUsersToStore, startTime)
            UserDao.markDeleted(usersToDelete.map { it.userId }, startTime)
            val filled = fillUserSubs(assignmentChanges.assignmentIdsToFillByUserSub)
            val removed = removeRoleAssignments(assignmentChanges.assignmentIdsToRemove)
            UserSyncStatusTable.recordSuccess(clock.now())

            filled to removed
        }

        usersToDelete.forEach { logger.info("Marked user '{}' ({}) deleted.", it.username, it.userId) }

        return UserSyncResult(
            storedUsers = allUsersToStore.size,
            newUsers = allUsersToStore.count { it.userId !in state.storedUserIds },
            deletedUsers = usersToDelete.size,
            removedRoleAssignments = removedRoleAssignments,
            filledRoleAssignments = filledRoleAssignments
        ).also { logger.info("Synced the users with Keycloak: {}.", it) }
    }

    /**
     * Ask Keycloak about each of the given [usernames] and decide from its answer how to change their role assignments
     * in the [state]. If there is no user with the username, all its role assignments are removed. Otherwise, role
     * assignments without an ID get the ID of the user, and those with the ID of another user are removed. Only the
     * role assignments that existed when the sync started are changed, because a username can be created again in
     * Keycloak and get new roles while the sync runs, and these new roles must stay.
     */
    private suspend fun lookUpAssignedUsernames(usernames: Set<String>, state: StoredState): AssignmentChanges {
        val foundUsers = mutableListOf<User>()
        val assignmentIdsToFillByUserSub = mutableMapOf<String, MutableList<Long>>()
        val assignmentIdsToRemove = mutableListOf<Long>()
        var usernamesLosingRoles = 0

        usernames.forEach { username ->
            val assignments = state.assignmentsByUsername.getValue(username)
            val user = keycloakClient.findUser(UserName(username))
            user?.let { foundUsers += it.toStoredUser() }
            val userSub = user?.id?.value

            val assignmentsToRemove = assignments.filter {
                userSub == null || (it.userSub != null && it.userSub != userSub)
            }

            if (assignmentsToRemove.isNotEmpty()) usernamesLosingRoles++
            assignmentIdsToRemove += assignmentsToRemove.map { it.id }

            val assignmentIdsWithoutSub = assignments.filter { it.userSub == null }.map { it.id }
            // A user renamed between two lookups can be found under two usernames, so keep the IDs of both.
            if (userSub != null && assignmentIdsWithoutSub.isNotEmpty()) {
                assignmentIdsToFillByUserSub.getOrPut(userSub) { mutableListOf() } += assignmentIdsWithoutSub
            }
        }

        return AssignmentChanges(foundUsers, assignmentIdsToFillByUserSub, assignmentIdsToRemove, usernamesLosingRoles)
    }

    /** Load the stored data that the sync compares with Keycloak, using [startTime] to find candidates for deletion. */
    private fun loadState(startTime: Instant) =
        StoredState(
            storedUserIds = UsersTable.select(UsersTable.id).mapTo(mutableSetOf()) { it[UsersTable.id].value },
            notDeletedUserCount = UsersTable.selectAll().where { UsersTable.deletedAt.isNull() }.count(),
            usersCreatedBeforeStart = UserDao.findNotDeletedCreatedBefore(startTime),
            assignmentsByUsername = RoleAssignmentsTable
                .select(RoleAssignmentsTable.id, RoleAssignmentsTable.userId, RoleAssignmentsTable.userSub)
                .groupBy(
                    { it[RoleAssignmentsTable.userId] },
                    { StoredAssignment(it[RoleAssignmentsTable.id].value, it[RoleAssignmentsTable.userSub]) }
                ),
            hasSucceededBefore = UserSyncStatusTable.lastSuccess() != null
        )

    /**
     * Throw a [UserSyncException] if the answer from Keycloak is not believable: if it is [empty][keycloakIsEmpty] but
     * there are stored users or role assignments, or if more than the allowed number of the [stored users][state]
     * would be marked deleted.
     */
    private fun checkSafety(keycloakIsEmpty: Boolean, state: StoredState, deletedUsers: Int) {
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
    }

    /**
     * Throw a [UserSyncException] if, after an earlier successful sync, more than the allowed number of the usernames
     * with role assignments in the [state] would lose any of them. [removedUsernames] counts each username once, no
     * matter how many role assignments it loses.
     */
    private fun checkRemovedUsernames(state: StoredState, removedUsernames: Int) {
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

    /**
     * Fill in the Keycloak ID of the user in the role assignments given for each ID in [assignmentIdsByUserSub], but
     * only where the ID is still missing. Return how many role assignments got an ID.
     */
    private fun fillUserSubs(assignmentIdsByUserSub: Map<String, List<Long>>): Int =
        assignmentIdsByUserSub.entries.sumOf { (userSub, ids) ->
            RoleAssignmentsTable.update({
                (RoleAssignmentsTable.id inList ids) and RoleAssignmentsTable.userSub.isNull()
            }) {
                it[RoleAssignmentsTable.userSub] = userSub
            }
        }

    /** Remove the role assignments with the given [ids], log each of them, and return how many were removed. */
    private fun removeRoleAssignments(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0

        RoleAssignmentsTable.selectAll().where { RoleAssignmentsTable.id inList ids }.forEach {
            logger.info(
                "Removing the role assignment of '{}' ({}) on organization {}, product {}, repository {}.",
                it[RoleAssignmentsTable.userId],
                it[RoleAssignmentsTable.userSub],
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

    /** The role assignments of each username that has role assignments. */
    val assignmentsByUsername: Map<String, List<StoredAssignment>>,

    /** Whether a sync has succeeded before. */
    val hasSucceededBefore: Boolean
) {
    /** The distinct usernames that have role assignments. */
    val assignedUsernames: Set<String> get() = assignmentsByUsername.keys
}

/** The changes to role assignments that a sync decided on after asking Keycloak about usernames. */
private class AssignmentChanges(
    /** The users that Keycloak found by their username. */
    val foundUsers: List<User>,

    /** The IDs of the role assignments without a user ID to fill in, for each Keycloak ID of a user. */
    val assignmentIdsToFillByUserSub: Map<String, List<Long>>,

    /** The IDs of the role assignments to remove. */
    val assignmentIdsToRemove: List<Long>,

    /** The number of usernames that lose any role assignment. */
    val usernamesLosingRoles: Int
)

/** A role assignment as the sync read it when it started. */
private class StoredAssignment(
    /** The ID of the role assignment. */
    val id: Long,

    /** The Keycloak ID of the user stored with the role assignment, or *null* if there is none. */
    val userSub: String?
)

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
