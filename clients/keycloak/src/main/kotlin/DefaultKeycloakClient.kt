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

package org.eclipse.apoapsis.ortserver.clients.keycloak

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.Client
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.Credential
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.GroupRequest
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.RoleRequest
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.TokenInfo
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.UserRequest
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.generateAccessToken
import org.eclipse.apoapsis.ortserver.clients.keycloak.internal.refreshToken

import org.slf4j.LoggerFactory

/**
 * The default implementation of [KeycloakClient] which uses the provided [httpClient] to access the Keycloak instance
 * at [apiUrl] and manages roles in the provided [client][clientId].
 */
@Suppress("TooManyFunctions")
class DefaultKeycloakClient(
    /** The pre-configured [HttpClient] for the interaction with the Keycloak REST API. */
    private val httpClient: HttpClient,

    /** The base URL to access the Keycloak Admin REST API. */
    private val apiUrl: String,

    /** The clientId of the client inside the Keycloak realm, which will be accessed/modified by this client. */
    private val clientId: String,

    /** Number of records to retrieve from Keycloak API in one chunk. */
    private val dataGetChunkSize: Int

) : KeycloakClient {
    companion object {
        private val logger = LoggerFactory.getLogger(KeycloakClient::class.java)

        fun create(config: KeycloakClientConfiguration, json: Json): KeycloakClient {
            val httpClient = createHttpClient(config, json)

            return DefaultKeycloakClient(httpClient, config.apiUrl, config.subjectClientId, config.dataGetChunkSize)
        }

        private fun createHttpClient(config: KeycloakClientConfiguration, json: Json): HttpClient =
            createDefaultHttpClient(json) {
                expectSuccess = true
            }.configureAuthentication(config, json)

        fun HttpClient.configureAuthentication(
            config: KeycloakClientConfiguration,
            json: Json
        ): HttpClient =
            config {
                defaultRequest {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                }

                install(Auth) {
                    val accessTokenUrl = config.accessTokenUrl
                    val clientId = config.clientId
                    val apiUser = config.apiUser
                    val apiSecret = config.apiSecret

                    val tokenClient = createDefaultHttpClient(json) { expectSuccess = true }

                    bearer {
                        loadTokens {
                            val tokenInfo: TokenInfo = runCatching {
                                tokenClient.generateAccessToken(accessTokenUrl, clientId, apiUser, apiSecret)
                            }.getOrElse {
                                throw KeycloakClientException("Failed to generate access token.", it)
                            }.body()

                            BearerTokens(tokenInfo.accessToken, tokenInfo.refreshToken)
                        }

                        refreshTokens {
                            val tokenInfo: TokenInfo = runCatching {
                                tokenClient.refreshToken(accessTokenUrl, clientId, oldTokens?.refreshToken.orEmpty())
                            }.getOrElse {
                                logger.debug("Failed to refresh the access token.", it)
                                tokenClient.generateAccessToken(accessTokenUrl, clientId, apiUser, apiSecret)
                            }.body()

                            BearerTokens(tokenInfo.accessToken, tokenInfo.refreshToken)
                        }
                    }
                }
            }
    }

    /** A mutex allowing safe access to the client ID, which is initialized on first access. */
    private val mutexClientId = Mutex()

    /** The internal ID of the client whose roles are to be accessed by this client. */
    private var internalClientId: String? = null

    /**
     * Return a set of all [clients][Client], which currently exist in the Keycloak realm.
     */
    private suspend fun getClients(): Set<Client> =
        runCatching {
            httpClient.get("$apiUrl/clients")
        }.getOrElse {
            throw KeycloakClientException("Failed to loads clients.", it)
        }.body()

    /**
     * Return the internal client ID (not name) of the given [clientId]. If the client cannot be found, an exception
     * will be thrown.
     */
    private suspend fun findClientId(clientId: String): String =
        getClients().find { it.clientId == clientId }?.id
            ?: throw KeycloakClientException("Could not find client with ID '$clientId'.")

    /**
     * Return the [(internal) ID of the client][internalClientId] to be manipulated by this client. Retrieve it on
     * the first access.
     */
    private suspend fun getClientId(): String = mutexClientId.withLock {
        internalClientId ?: findClientId(clientId).also { internalClientId = it }
    }

    override suspend fun getGroups(groupNameFilter: String?): Set<Group> =
        runCatching {
            val groupCount = httpClient.get("$apiUrl/groups/count") {
                url {
                    groupNameFilter?.also { filter ->
                        parameters.append("search", filter)
                    }
                }
            }.body<GroupCount>().count

            buildSet(groupCount) {
                var first = 0

                while (first < groupCount) {
                    val chunk = httpClient.get("$apiUrl/groups") {
                        url {
                            parameters.append("first", first.toString())
                            parameters.append("max", dataGetChunkSize.toString())
                            groupNameFilter?.also { filter ->
                                parameters.append("search", filter)
                            }
                        }
                    }.body<List<Group>>()
                    addAll(chunk)
                    first += chunk.size
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to load groups.", it)
        }

    override suspend fun getGroup(id: GroupId): Group =
        runCatching {
            httpClient.get("$apiUrl/groups/${id.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find group '${id.value}'.", it)
        }.body()

    override suspend fun getGroup(name: GroupName): Group =
        runCatching {
            // Keycloak does not provide an API to get a group by name, so use a search query and filter the result.
            httpClient.get("$apiUrl/groups") {
                url {
                    parameters.append("search", name.value)
                    parameters.append("exact", "true")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Could not find group with name '${name.value}'.", it)
        }.body<List<Group>>().find { it.name == name }
            ?: throw KeycloakClientException("Could not find group with name '${name.value}'.")

    override suspend fun searchGroups(name: GroupName): Set<Group> =
        runCatching {
            httpClient.get("$apiUrl/groups") {
                url {
                    parameters.append("search", name.value)
                    parameters.append("exact", "false")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to loads groups.", it)
        }.body<Set<Group>>()

    override suspend fun createGroup(name: GroupName) {
        runCatching {
            httpClient.post("$apiUrl/groups") {
                setBody(GroupRequest(name))
            }
        }.onFailure { throw KeycloakClientException("Failed to create group '${name.value}'.", it) }
    }

    override suspend fun updateGroup(id: GroupId, name: GroupName) {
        runCatching {
            httpClient.put("$apiUrl/groups/${id.value}") {
                setBody(GroupRequest(name))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update group '${id.value}'.", it) }
    }

    override suspend fun deleteGroup(id: GroupId) {
        runCatching {
            httpClient.delete("$apiUrl/groups/${id.value}")
        }.getOrElse { throw KeycloakClientException("Failed to delete group '${id.value}'.", it) }
    }

    override suspend fun getGroupClientRoles(id: GroupId): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}/composite")
        }.getOrElse {
            throw KeycloakClientException("Failed to load client roles for group '${id.value}'.", it)
        }.body()

    override suspend fun addGroupClientRole(id: GroupId, role: Role) {
        runCatching {
            httpClient.post("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}") {
                setBody(listOf(role))
            }
        }.onFailure {
            throw KeycloakClientException("Failed to add role '${role.name.value}' to group '${id.value}'.", it)
        }
    }

    override suspend fun removeGroupClientRole(id: GroupId, role: Role) {
        runCatching {
            httpClient.delete("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}") {
                setBody(listOf(role))
            }
        }.onFailure {
            throw KeycloakClientException("Failed to remove role '${role.name.value}' from group '${id.value}'.", it)
        }
    }

    override suspend fun getRoles(): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles")
        }.getOrElse {
            throw KeycloakClientException("Failed to load roles.", it)
        }.body()

    override suspend fun getRole(name: RoleName): Role =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/${name.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find role '${name.value}'.", it)
        }.body()

    override suspend fun createRole(name: RoleName, description: String?) {
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles") {
                setBody(RoleRequest(name, description))
            }
        }.onFailure { throw KeycloakClientException("Failed to create role '${name.value}'.", it) }
    }

    override suspend fun updateRole(name: RoleName, updatedName: RoleName, updatedDescription: String?) {
        val oldRole = getRole(name)

        val roleDescription = getUpdatedValue(oldRole.description, updatedDescription)

        runCatching {
            httpClient.put("$apiUrl/clients/${getClientId()}/roles/${name.value}") {
                setBody(RoleRequest(updatedName, roleDescription))
            }
        }.onFailure { throw KeycloakClientException("Failed to update role '${name.value}'.", it) }
    }

    override suspend fun deleteRole(name: RoleName) {
        runCatching {
            httpClient.delete("$apiUrl/clients/${getClientId()}/roles/${name.value}")
        }.onFailure { throw KeycloakClientException("Failed to delete role '${name.value}'.", it) }
    }

    override suspend fun addCompositeRole(name: RoleName, compositeRoleId: RoleId) {
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites") {
                setBody(listOf(RoleIdHolder(compositeRoleId)))
            }
        }.onFailure {
            throw KeycloakClientException(
                "Failed to add composite role with id '${compositeRoleId.value}' to role '${name.value}'.",
                it
            )
        }
    }

    override suspend fun getCompositeRoles(name: RoleName): List<Role> =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites")
        }.getOrElse {
            throw KeycloakClientException("Failed to find composites for role '${name.value}'.", it)
        }.body()

    override suspend fun removeCompositeRole(name: RoleName, compositeRoleId: RoleId) {
        runCatching {
            httpClient.delete("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites") {
                setBody(listOf(RoleIdHolder(compositeRoleId)))
            }
        }.onFailure {
            throw KeycloakClientException(
                "Failed to remove composite role with id '${compositeRoleId.value}' from role '${name.value}'.",
                it
            )
        }
    }

    override suspend fun getUsers(): Set<User> =
        runCatching {
            httpClient.get("$apiUrl/users") {
                url {
                    // The Keycloak API returns by default only 100 users. By using the query parameter max with the
                    // value -1, the API returns directly all users.
                    parameters.append("max", "-1")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to load users.", it)
        }.body()

    override suspend fun getUser(id: UserId): User =
        runCatching {
            httpClient.get("$apiUrl/users/${id.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find user '${id.value}'.", it)
        }.body()

    override suspend fun getUser(username: UserName): User =
        runCatching {
            // Keycloak does not provide an API to get a single user by name, so use a search query and filter the
            // result.
            httpClient.get("$apiUrl/users") {
                url {
                    parameters.append("username", username.value)
                    parameters.append("exact", "true")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Could not find user with name '${username.value}'.", it)
        }.body<List<User>>().find { it.username == username }
            ?: throw KeycloakClientException("Could not find user with name '${username.value}'.")

    override suspend fun createUser(
        username: UserName,
        firstName: String?,
        lastName: String?,
        email: String?,
        password: String?,
        temporary: Boolean
    ) {
        runCatching {
            // If a password is provided, create a Credential object; otherwise, leave credentials empty.
            val credentials = password?.let {
                listOf(Credential(value = it, temporary = temporary))
            }.orEmpty()

            // Create the UserRequest object which optionally includes the credentials.
            val userRequest = UserRequest(
                username = username,
                firstName = firstName,
                lastName = lastName,
                email = email,
                credentials = credentials
            )

            httpClient.post("$apiUrl/users") {
                setBody(userRequest)
            }
        }.onFailure { throw KeycloakClientException("Failed to create user '${username.value}'.", it) }
    }

    override suspend fun getUserHasCredentials(username: UserName): Boolean =
        runCatching {
            val user = getUser(username)
            httpClient.get("$apiUrl/users/${user.id.value}/credentials") {
            }
        }.getOrElse {
            throw KeycloakClientException("Could not find user with name '${username.value}'.", it)
        }.body<List<UserCredentials>>().isNotEmpty()

    override suspend fun updateUser(
        id: UserId,
        username: UserName?,
        firstName: String?,
        lastName: String?,
        email: String?
    ) {
        val oldUser = getUser(id)

        val correctUsername = getUpdatedValue(oldUser.username.value, username?.value)?.let { UserName(it) }
        val correctFirstName = getUpdatedValue(oldUser.firstName, firstName)
        val correctLastName = getUpdatedValue(oldUser.lastName, lastName)
        val correctEmail = getUpdatedValue(oldUser.email, email)

        runCatching {
            httpClient.put("$apiUrl/users/${id.value}") {
                setBody(UserRequest(correctUsername, correctFirstName, correctLastName, correctEmail))
            }
        }.onFailure { throw KeycloakClientException("Failed to update user '${id.value}'.", it) }
    }

    override suspend fun deleteUser(id: UserId) {
        runCatching {
            httpClient.delete("$apiUrl/users/${id.value}")
        }.onFailure { throw KeycloakClientException("Failed to delete user '${id.value}'.", it) }
    }

    override suspend fun getUserClientRoles(id: UserId): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/users/${id.value}/role-mappings/clients/${getClientId()}/composite")
        }.getOrElse {
            throw KeycloakClientException("Failed to load client roles for user '${id.value}'.", it)
        }.body()

    override suspend fun addUserToGroup(username: UserName, groupName: GroupName) {
        val user = getUser(username)
        val group = getGroup(groupName)

        runCatching {
            httpClient.put("$apiUrl/users/${user.id.value}/groups/${group.id.value}")
        }.onFailure {
            throw KeycloakClientException(
                "Failed to add user '{$username.value}' to group '{$groupName}'.",
                it
            )
        }
    }

    override suspend fun removeUserFromGroup(username: UserName, groupName: GroupName) {
        val user = getUser(username)
        val group = getGroup(groupName)

        runCatching {
            httpClient.delete("$apiUrl/users/${user.id.value}/groups/${group.id.value}")
        }.onFailure {
            throw KeycloakClientException(
                "Failed to remove user '${username.value}' from group {'$groupName.value}'.",
                it
            )
        }
    }

    override suspend fun getGroupMembers(groupName: GroupName): Set<User> {
        val group = getGroup(groupName)
        return getGroupMembers(group.id)
    }

    override suspend fun getGroupMembers(groupId: GroupId): Set<User> {
        return runCatching {
            httpClient.get("$apiUrl/groups/${groupId.value}/members")
        }.getOrElse {
            throw KeycloakClientException("Failed to get members of group '${groupId.value}'.", it)
        }.body()
    }
}

/**
 * Return the correct value for an update request body based on the [oldValue] and [newValue]. If the [newValue] is
 * given and not empty it will be returned, otherwise the [oldValue] will be returned even it is null.
 */
private fun getUpdatedValue(oldValue: String?, newValue: String?): String? =
    newValue?.takeIf { newValue.isNotEmpty() } ?: oldValue
