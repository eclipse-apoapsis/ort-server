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
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    suspend fun getGroup(id: String): Group =
        runCatching {
            httpClient.get("$apiUrl/groups/$id")
        }.getOrElse {
            throw KeycloakClientException("Could not find group '$id'.", it)
        }.body()

    /**
     * Return the [group][Group] with the given [name].
     */
    suspend fun getGroupByName(name: String): Group =
        runCatching {
            // Keycloak does not provide an API to get a group by name, so use a search query and filter the result.
            httpClient.get("$apiUrl/groups") {
                url {
                    parameters.append("search", name)
                    parameters.append("exact", "true")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Could not find group with name '$name'.", it)
        }.body<List<Group>>().findByName(name).takeIf { it != null }
            ?: throw KeycloakClientException("Could not find group with name '$name'.")

    /**
     * Add a new [group][Group] to the Keycloak realm with the given [name].
     */
    suspend fun createGroup(name: String): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/groups") {
                setBody(GroupRequest(name))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create group '$name'.", it) }

    /**
     * Add a new [subgroup][Group] with the given [name] to the [group][Group] with the given [id].
     */
    suspend fun createSubGroup(id: String, name: String): Group =
        runCatching {
            httpClient.post("$apiUrl/groups/$id/children") {
                setBody(GroupRequest(name))
            }
        }.getOrElse {
            throw KeycloakClientException("Failed to add subgroup '$name' to the group '$id'.", it)
        }.body()

    /**
     * Update the [group][Group] with the given [id], with the new [name] in the Keycloak realm.
     */
    suspend fun updateGroup(id: String, name: String): HttpResponse =
        runCatching {
            httpClient.put("$apiUrl/groups/$id") {
                setBody(GroupRequest(name))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update group '$id'.", it) }

    /**
     * Delete the [group][Group] within the Keycloak realm with the given [id].
     */
    suspend fun deleteGroup(id: String): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/groups/$id")
        }.getOrElse { throw KeycloakClientException("Failed to delete group '$id'.", it) }

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
    suspend fun getRole(name: String): Role =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/$name")
        }.getOrElse {
            throw KeycloakClientException("Could not find role '$name'.", it)
        }.body()

    /**
     * Add a new [role][Role] to the configured [client][clientId] with the given [name] and [description].
     */
    suspend fun createRole(name: String, description: String? = null): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles") {
                setBody(RoleRequest(name, description))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create role '$name'.", it) }

    /**
     * Update the [role][Role] within the configured [client][clientId] with the new [updatedName] and
     * [updatedDescription].
     */
    suspend fun updateRole(name: String, updatedName: String, updatedDescription: String?): HttpResponse {
        val oldRole = getRole(name)

        val roleDescription = getUpdatedValue(oldRole.description, updatedDescription)

        return runCatching {
            httpClient.put("$apiUrl/clients/${getClientId()}/roles/$name") {
                setBody(RoleRequest(updatedName, roleDescription))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update role '$name'.", it) }
    }

    /**
     * Delete the [role][Role] within the configured [client][clientId] with the given [name].
     */
    suspend fun deleteRole(name: String): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/clients/${getClientId()}/roles/$name")
        }.getOrElse { throw KeycloakClientException("Failed to delete role '$name'.", it) }

    /**
     * Add the role identified by [compositeRoleId] to the composites of the role identified by [name].
     */
    suspend fun addCompositeRole(name: String, compositeRoleId: String): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/clients/${getClientId()}/roles/$name/composites") {
                setBody(listOf(RoleId(compositeRoleId)))
            }
        }.getOrElse {
            throw KeycloakClientException(
                "Failed to add composite role with id '$compositeRoleId' to role '$name'.",
                it
            )
        }

    /**
     * Get all composite roles of the [role][Role] with the given [name].
     */
    suspend fun getCompositeRoles(name: String): List<Role> =
        runCatching {
            httpClient.get("$apiUrl/clients/${getClientId()}/roles/$name/composites")
        }.getOrElse {
            throw KeycloakClientException("Failed to find composites for role '$name'.", it)
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
    suspend fun getUser(id: String): User =
        runCatching {
            httpClient.get("$apiUrl/users/$id")
        }.getOrElse {
            throw KeycloakClientException("Could not find user '$id'.", it)
        }.body()

    /**
     * Return the [user][User] with the given [username].
     */
    suspend fun getUserByName(username: String): User =
        runCatching {
            // Keycloak does not provide an API to get a single user by name, so use a search query and filter the
            // result.
            httpClient.get("$apiUrl/users") {
                url {
                    parameters.append("username", username)
                    parameters.append("exact", "true")
                }
            }
        }.getOrElse {
            throw KeycloakClientException("Could not find user with name '$username'.", it)
        }.body<List<User>>().find { it.username == username }
            ?: throw KeycloakClientException("Could not find user with name '$username'.")

    /**
     * Create a new [user][User] in the Keycloak realm with the given [username], [firstName], [lastName] and [email].
     */
    suspend fun createUser(
        username: String,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null
    ): HttpResponse =
        runCatching {
            httpClient.post("$apiUrl/users") {
                setBody(UserRequest(username, firstName, lastName, email))
            }
        }.getOrElse { throw KeycloakClientException("Failed to create user '$username'.", it) }

    /**
     * Update the [user][User] with the given [id] within the Keycloak realm with the new [username], [firstName],
     * [lastName] and [email].
     */
    suspend fun updateUser(
        id: String,
        username: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null
    ): HttpResponse {
        val oldUser = getUser(id)

        val correctUsername = getUpdatedValue(oldUser.username, username)
        val correctFirstName = getUpdatedValue(oldUser.firstName, firstName)
        val correctLastName = getUpdatedValue(oldUser.lastName, lastName)
        val correctEmail = getUpdatedValue(oldUser.email, email)

        return runCatching {
            httpClient.put("$apiUrl/users/$id") {
                setBody(UserRequest(correctUsername, correctFirstName, correctLastName, correctEmail))
            }
        }.getOrElse { throw KeycloakClientException("Failed to update user '$id'.", it) }
    }

    /**
     * Delete the [user][User] with the given [id] from the Keycloak realm.
     */
    suspend fun deleteUser(id: String): HttpResponse =
        runCatching {
            httpClient.delete("$apiUrl/users/$id")
        }.getOrElse { throw KeycloakClientException("Failed to delete user '$id'.", it) }
}

/**
 * A data class representing information about the tokens received from the API client.
 */
@Serializable
private data class TokenInfo(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)

/**
 * A data class representing an OAuth client in a Keycloak realm.
 */
@Serializable
private data class Client(
    /** The (Keycloak-internal) unique client ID. */
    val id: String,

    /** The client ID (as part of the client's credentials). */
    val clientId: String
)

/**
 * A data class representing a role managed by Keycloak.
 */
@Serializable
data class Role(
    /** The internal ID of the role. */
    val id: String,

    /** The role name. */
    val name: String,

    /** The description of the role. */
    val description: String? = null
)

@Serializable
data class RoleRequest(
    val name: String,
    val description: String?
)

@Serializable
data class RoleId(
    val id: String
)

/**
 * A data class representing a group managed by Keycloak.
 */
@Serializable
data class Group(
    /** The internal ID of the group. */
    val id: String,

    /** The group name. */
    val name: String,

    /** A set of groups, which represents the subgroup hierarchy. */
    val subGroups: Set<Group>
)

/**
 * Recursively search for a group with the provided [name].
 */
private fun Collection<Group>.findByName(name: String): Group? {
    forEach {
        if (it.name == name) return it
        val group = it.subGroups.findByName(name)
        if (group != null) return group
    }

    return null
}

@Serializable
private data class GroupRequest(
    val name: String,
)

/**
 * A data class representing a user managed by Keycloak.
 */
@Serializable
data class User(
    /** The internal ID of the user. */
    val id: String,

    /** The username of the user. */
    val username: String,

    /** The first name of the user. */
    val firstName: String? = null,

    /** The last name of the user. */
    val lastName: String? = null,

    /** The mail address of the user. */
    val email: String? = null,

    /** Specifies, whether the user can log in or not. */
    val enabled: Boolean = true
)

@Serializable
private data class UserRequest(
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val enabled: Boolean = true
)

class KeycloakClientException : RuntimeException {
    constructor(msg: String, cause: Throwable) : super(msg, cause)
    constructor(msg: String) : super(msg)
}

internal suspend fun HttpClient.generateAccessToken(
    tokenUrl: String,
    clientId: String,
    username: String,
    password: String
) = submitForm(
    url = tokenUrl,
    formParameters = Parameters.build {
        append("client_id", clientId)
        append("grant_type", "password")
        append("username", username)
        append("password", password)
    }
)

internal suspend fun HttpClient.refreshToken(tokenUrl: String, clientId: String, refreshToken: String) =
    submitForm(
        url = tokenUrl,
        formParameters = Parameters.build {
            append("client_id", clientId)
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
        }
    )

/**
 * Return the correct value for an update request body based on the [oldValue] and [newValue]. If the [newValue] is
 * given and not empty it will be returned, otherwise the [oldValue] will be returned even it is null.
 */
private fun getUpdatedValue(oldValue: String?, newValue: String?): String? =
    newValue?.takeIf { newValue.isNotEmpty() } ?: oldValue

data class KeycloakClientConfiguration(
    val apiUrl: String,
    val clientId: String,
    val accessTokenUrl: String,
    val apiUser: String,
    val apiSecret: String
)
