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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import java.net.Authenticator
import java.net.PasswordAuthentication

import org.eclipse.apoapsis.ortserver.config.Path

import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.ort.UserInfoAuthenticator

import org.slf4j.LoggerFactory

/**
 * A special [Authenticator] implementation for authenticating against infrastructure components whose URLs and
 * credentials are defined in the system environment.
 *
 * This implementation looks for environment variables that have the strings _URL_ or _URI_ in their names and whose
 * values are URLs with a user info component. It can handle authentication requests for these URLs (without the
 * user info component) using the credentials defined in the environment. The credentials are resolved against the
 * [InfraSecretResolverFun] function passed to the [create] function. This mechanism allows for a convenient way of
 * configuring services giving administrators full flexibility how they manage their secrets.
 *
 * In case a secret cannot be resolved, the resulting [PasswordAuthentication] contains its name. That way, this class
 * can be used with real credentials passed in the URL as well like ORT's [UserInfoAuthenticator]. However, this
 * typically indicates a problem in the configuration; therefore, a warning is logged in this case.
 */
internal class UserInfoSecretAuthenticator private constructor(
    /** The object managing known services and their credentials. */
    private val services: AuthenticatedServices<AuthenticationData>
) : Authenticator() {
    companion object {
        private val logger = LoggerFactory.getLogger(UserInfoSecretAuthenticator::class.java)

        /**
         * Create an instance of [UserInfoSecretAuthenticator] that uses the given [secretResolverFun] to resolve
         * secrets.
         */
        fun create(secretResolverFun: InfraSecretResolverFun): UserInfoSecretAuthenticator =
            UserInfoSecretAuthenticator(
                AuthenticatedServices.create(
                    gatherAuthenticationData(secretResolverFun),
                    AuthenticationData::url,
                    AuthenticationData::variableName,
                    enableFuzzyMatching = false
                )
            )

        /**
         * Obtain information about services and their credentials from the environment. Return a [Map] with service
         * URLs and their associated authentication information.
         */
        private fun gatherAuthenticationData(
            secretResolverFun: InfraSecretResolverFun
        ): List<AuthenticationData> {
            val userInfoAuthenticator = UserInfoAuthenticator()

            return System.getenv().entries.filter { (key, _) ->
                val upperKey = key.uppercase()
                "URL" in upperKey || "URI" in upperKey
            }.mapNotNull {
                getAuthenticationData(userInfoAuthenticator, it.key, it.value, secretResolverFun)
            }
        }

        /**
         * Try to obtain an [AuthenticationData] object for an environment variable defined by [varName] and [varValue]
         * that might specify a service URL. Use [userInfoAuthenticator] to extract credentials contained in the URL,
         * and the given [secretResolverFun] to resolve them. If successful, return a populated [AuthenticationData]
         * instance for it; otherwise, return *null*.
         */
        private fun getAuthenticationData(
            userInfoAuthenticator: UserInfoAuthenticator,
            varName: String,
            varValue: String,
            secretResolverFun: InfraSecretResolverFun
        ): AuthenticationData? =
            varValue.toUri { it.toURL() }.mapCatching { url ->
                userInfoAuthenticator.requestPasswordAuthenticationInstance(
                    null,
                    null,
                    0,
                    null,
                    null,
                    null,
                    url,
                    RequestorType.SERVER
                )?.let { authentication ->
                    val username = resolveSecret(authentication.userName, varValue, "username", secretResolverFun)
                    val password =
                        resolveSecret(String(authentication.password), varValue, "password", secretResolverFun)
                    AuthenticationData(
                        url.toString().replaceCredentialsInUri(),
                        PasswordAuthentication(username, password.toCharArray()),
                        varName
                    ).also {
                        logger.info(
                            "Found variable '{}' defining credentials for service URL '{}'.",
                            varName,
                            it.url
                        )
                    }
                }
            }.getOrNull()

        /**
         * Resolve the secret with the given [name] using the [secretResolverFun] and handle errors. For unresolvable
         * secrets, log a warning derived from the given [url] and [type],  and return the original [name] as the
         * secret value.
         */
        private fun resolveSecret(
            name: String,
            url: String,
            type: String,
            secretResolverFun: InfraSecretResolverFun
        ): String = runCatching {
            secretResolverFun(Path(name))
        }.onFailure {
            logger.warn("Could not resolve {} secret in URL {}", type, url.replaceCredentialsInUri(), it)
        }.getOrDefault(name)
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.SERVER || requestingURL == null) return null

        return services.getAuthenticatedServiceFor(requestingHost, requestingURL)?.let { authData ->
            logger.info(
                "Using credentials for service URL '{}' defined by environment variable '{}'.",
                requestingURL?.toString(),
                authData.variableName
            )
            authData.authentication
        }
    }
}

/**
 * An internally used data class storing authentication information for a specific service. This contains the
 * actual credentials and also the variable that defined this service for diagnostic purposes.
 */
private data class AuthenticationData(
    /** The URL for which the credentials are defined. */
    val url: String,

    /** The actual authentication information. */
    val authentication: PasswordAuthentication,

    /** The name of the environment variable that defined this service. */
    val variableName: String
)
