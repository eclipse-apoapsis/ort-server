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

package org.ossreviewtoolkit.server.clients.keycloak

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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.clients.keycloak.internal.Client
import org.ossreviewtoolkit.server.clients.keycloak.internal.GroupRequest
import org.ossreviewtoolkit.server.clients.keycloak.internal.RoleRequest
import org.ossreviewtoolkit.server.clients.keycloak.internal.TokenInfo
import org.ossreviewtoolkit.server.clients.keycloak.internal.UserRequest
import org.ossreviewtoolkit.server.clients.keycloak.internal.findByName
import org.ossreviewtoolkit.server.clients.keycloak.internal.generateAccessToken
import org.ossreviewtoolkit.server.clients.keycloak.internal.refreshToken

import org.slf4j.LoggerFactory

/**
 * A client implementing interactions with Keycloak, based on the documentation from
 * https://www.keycloak.org/docs-api/19.0/rest-api/index.html.
 */
@Suppress("TooManyFunctions")
class KeycloakClient(
    /** The pre-configured [HttpClient] for the interaction with the Keycloak REST API. */
    private val httpClient: HttpClient,

    /** The base URL to access the Keycloak Admin REST API. */
    private val apiUrl: String,

    /** The clientId of the client inside the Keycloak realm, which will be accessed/modified by this client. */
    private val clientId: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(KeycloakClient::class.java)

        fun create(config: KeycloakClientConfiguration, json: Json): KeycloakClient {
            val httpClient = createHttpClient(config, json)

            return KeycloakClient(httpClient, config.apiUrl, config.clientId)
        }

        private fun createHttpClient(config: KeycloakClientConfiguration, json: Json): HttpClient =
            createDefaultHttpClient(json) {
                expectSuccess = true

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

    /**
     * Return a set of all [groups][Group], which currently exist in the Keycloak realm.
     */
    suspend fun getGroups(): Set<Group> =
        runCatching {
            httpClient.get("$apiUrl/groups")
        }.getOrElse {
            throw KeycloakClientException("Failed to load groups.", it)
        }.body()

    /**
     * Return exactly the [group][Group] with the given [id].
     */
    suspend fun getGroup(id: GroupId): Group =
        runCatching {
            httpClient.get("$apiUrl/groups/${id.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find group '${id.value}'.", it)
        }.body()

    /**
     * Return the [group][Group] with the given [name].
     */
    suspend fun getGroup(name: GroupName): Group =
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
        }.body<List<Group>>().findByName(name).takeIf { it != null }
            ?: throw KeycloakClientException("Could not find group with name '${name.value}'.")

    /**
     * Add a new [group][Group] to the Keycloak realm with the given [name].
     */
    suspend fun createGroup(name: GroupName): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/groups") {
                setBody(GroupRequest(name))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create group '${name.value}'.", it) }

    /**
     * Add a new [subgroup][Group] with the given [name] to the [group][Group] with the given [id].
     */
    suspend fun createSubGroup(id: GroupId, name: GroupName): Group =
        runCatching {
            httpClient.post("$apiUrl/groups/${id.value}/children") {
                setBody(GroupRequest(name))
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to add subgroup '${name.value}' to the group '${id.value}'.", it)
        }.body()

    /**
     * Update the [group][Group] with the given [id], with the new [name] in the Keycloak realm.
     */
    suspend fun updateGroup(id: GroupId, name: GroupName): HttpResponse =
        runCatching {
            httpClient.put("$apiUrl/groups/${id.value}") {
                setBody(GroupRequest(name))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update group '${id.value}'.", it) }

    /**
     * Delete the [group][Group] within the Keycloak realm with the given [id].
     */
    suspend fun deleteGroup(id: GroupId): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/groups/${id.value}")
        }.getOrElse { throw KeycloakClientException("Failed to delete group '${id.value}'.", it) }

    /**
     * Get all client [roles][Role] for the [group][Group] with the given [id].
     */
    suspend fun getGroupClientRoles(id: GroupId): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}/composite")
        }.getOrElse {
            throw KeycloakClientException("Failed to load client roles for group '${id.value}'.", it)
        }.body()

    /**
     * Add a [role][roleId] to the [group][Group] with the given [id].
     */
    suspend fun addGroupClientRole(id: GroupId, role: Role): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}") {
                setBody(listOf(role))
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to add role '${role.name.value}' to group '${id.value}'.", it)
        }.body()

    /**
     * Remove a [role][roleId] from the [group][Group] with the given [id].
     */
    suspend fun removeGroupClientRole(id: GroupId, role: Role): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/groups/${id.value}/role-mappings/clients/${getClientId()}") {
                setBody(listOf(role))
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to remove role '${role.name.value}' from group '${id.value}'.", it)
        }.body()

    /**
     * Return a set of all [roles][Role] that are currently defined for the configured [client][clientId].
     */
    suspend fun getRoles(): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles")
        }.getOrElse {
            throw KeycloakClientException("Failed to load roles.", it)
        }.body()

    /**
     * Return exactly the client [role][Role] with the given [name].
     */
    suspend fun getRole(name: RoleName): Role =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/${name.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find role '${name.value}'.", it)
        }.body()

    /**
     * Add a new [role][Role] to the configured [client][clientId] with the given [name] and [description].
     */
    suspend fun createRole(name: RoleName, description: String? = null): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles") {
                setBody(RoleRequest(name, description))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create role '${name.value}'.", it) }

    /**
     * Update the [role][Role] within the configured [client][clientId] with the new [updatedName] and
     * [updatedDescription].
     */
    suspend fun updateRole(name: RoleName, updatedName: RoleName, updatedDescription: String?): HttpResponse {
        val oldRole = getRole(name)

        val roleDescription = getUpdatedValue(oldRole.description, updatedDescription)

        return runCatching {
            httpClient.put("$apiUrl/clients/${getClientId()}/roles/${name.value}") {
                setBody(RoleRequest(updatedName, roleDescription))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update role '${name.value}'.", it) }
    }

    /**
     * Delete the [role][Role] within the configured [client][clientId] with the given [name].
     */
    suspend fun deleteRole(name: RoleName): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/clients/${getClientId()}/roles/${name.value}")
        }.getOrElse { throw KeycloakClientException("Failed to delete role '${name.value}'.", it) }

    /**
     * Add the role identified by [compositeRoleId] to the composites of the role identified by [name].
     */
    suspend fun addCompositeRole(name: RoleName, compositeRoleId: RoleId): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites") {
                setBody(listOf(RoleIdHolder(compositeRoleId)))
            }
        }.getOrElse {
            throw KeycloakClientException(
                "Failed to add composite role with id '${compositeRoleId.value}' to role '${name.value}'.",
                it
            )
        }

    /**
     * Get all composite roles of the [role][Role] with the given [name].
     */
    suspend fun getCompositeRoles(name: RoleName): List<Role> =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites")
        }.getOrElse {
            throw KeycloakClientException("Failed to find composites for role '${name.value}'.", it)
        }.body()

    suspend fun removeCompositeRole(name: RoleName, compositeRoleId: RoleId): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/clients/${getClientId()}/roles/${name.value}/composites") {
                setBody(listOf(RoleIdHolder(compositeRoleId)))
            }
        }.getOrElse {
            throw KeycloakClientException(
                "Failed to remove composite role with id '${compositeRoleId.value}' from role '${name.value}'.",
                it
            )
        }.body()

    /**
     * Return a set of all [users][User], which currently exist in the Keycloak realm.
     */
    suspend fun getUsers(): Set<User> =
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

    /**
     * Return exactly the [user][User] with the given [id].
     */
    suspend fun getUser(id: UserId): User =
        runCatching {
            httpClient.get("$apiUrl/users/${id.value}")
        }.getOrElse {
            throw KeycloakClientException("Could not find user '${id.value}'.", it)
        }.body()

    /**
     * Return the [user][User] with the given [username].
     */
    suspend fun getUser(username: UserName): User =
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

    /**
     * Create a new [user][User] in the Keycloak realm with the given [username], [firstName], [lastName] and [email].
     */
    suspend fun createUser(
        username: UserName,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null
    ): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/users") {
                setBody(UserRequest(username, firstName, lastName, email))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create user '${username.value}'.", it) }

    /**
     * Update the [user][User] with the given [id] within the Keycloak realm with the new [username], [firstName],
     * [lastName] and [email].
     */
    suspend fun updateUser(
        id: UserId,
        username: UserName? = null,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null
    ): HttpResponse {
        val oldUser = getUser(id)

        val correctUsername = getUpdatedValue(oldUser.username.value, username?.value)?.let { UserName(it) }
        val correctFirstName = getUpdatedValue(oldUser.firstName, firstName)
        val correctLastName = getUpdatedValue(oldUser.lastName, lastName)
        val correctEmail = getUpdatedValue(oldUser.email, email)

        return runCatching {
            httpClient.put("$apiUrl/users/${id.value}") {
                setBody(UserRequest(correctUsername, correctFirstName, correctLastName, correctEmail))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update user '${id.value}'.", it) }
    }

    /**
     * Delete the [user][User] with the given [id] from the Keycloak realm.
     */
    suspend fun deleteUser(id: UserId): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/users/${id.value}")
        }.getOrElse { throw KeycloakClientException("Failed to delete user '${id.value}'.", it) }

    /**
     * Get all client [roles][Role] for the [user][User] with the given [id].
     */
    suspend fun getUserClientRoles(id: UserId): Set<Role> =
        runCatching {
            httpClient.get("$apiUrl/users/${id.value}/role-mappings/clients/${getClientId()}/composite")
        }.getOrElse {
            throw KeycloakClientException("Failed to load client roles for user '${id.value}'.", it)
        }.body()
}

/**
 * Return the correct value for an update request body based on the [oldValue] and [newValue]. If the [newValue] is
 * given and not empty it will be returned, otherwise the [oldValue] will be returned even it is null.
 */
private fun getUpdatedValue(oldValue: String?, newValue: String?): String? =
    newValue?.takeIf { newValue.isNotEmpty() } ?: oldValue
