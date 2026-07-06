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

import java.net.Authenticator.RequestorType

import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService

import org.ossreviewtoolkit.utils.authentication.UserInfoAuthenticator
import org.ossreviewtoolkit.utils.common.replaceCredentialsInUri
import org.ossreviewtoolkit.utils.common.toUri

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserInfoSecretAuthenticator")

/**
 * Retrieve authentication information for infrastructure components whose URLs and credentials are defined in the
 * system environment.
 *
 * This function looks for environment variables that have the strings _URL_ or _URI_ in their names and whose
 * values are URLs with a user info component. It generates an [AuthenticationInfo] object to handle authentication
 * requests for these URLs (without the user info component) using the credentials defined in the environment. The
 * credentials are resolved against the [InfraSecretResolverFun] function passed as parameter. This mechanism allows
 * for a convenient way of configuring services giving administrators full flexibility how they manage their secrets.
 *
 * In case a secret cannot be resolved, its name is used instead. That way, this mechanism can be used with real
 * credentials passed in the URL as well like ORT's [UserInfoAuthenticator]. However, this typically indicates a
 * problem in the configuration; therefore, a warning is logged in this case.
 */
internal fun getAuthenticationInfoFromEnvironment(secretResolverFun: InfraSecretResolverFun): AuthenticationInfo {
    val userInfoAuthenticator = UserInfoAuthenticator()
    val secrets = mutableMapOf<String, String>()

    val services = System.getenv().entries.filter { (key, _) ->
        val upperKey = key.uppercase()
        "URL" in upperKey || "URI" in upperKey
    }.mapNotNull {
        getResolvedService(userInfoAuthenticator, it.key, it.value, secretResolverFun)
    }.map { (service, username, password) ->
        secrets[service.usernameSecret.name] = username
        secrets[service.passwordSecret.name] = password
        service
    }

    return AuthenticationInfo(secrets, services)
}

/**
 * Try to obtain a [ResolvedInfrastructureService] with its credentials for an environment variable defined by
 * [varName] and [varValue] that might specify a service URL. Use [userInfoAuthenticator] to extract credentials
 * contained in the URL, and the given [secretResolverFun] to resolve them. If successful, return a [Triple] with the
 * service, its username and its password; otherwise, return *null*.
 */
private fun getResolvedService(
    userInfoAuthenticator: UserInfoAuthenticator,
    varName: String,
    varValue: String,
    secretResolverFun: InfraSecretResolverFun
): Triple<ResolvedInfrastructureService, String, String>? =
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
            Triple(
                ResolvedInfrastructureService.createWithSecretNames(
                    name = varName,
                    url = url.toString().replaceCredentialsInUri(),
                    usernameSecretName = "$varName.username",
                    passwordSecretName = "$varName.password"
                ),
                username,
                password
            ).also {
                logger.info(
                    "Found variable '{}' defining credentials for service URL '{}'.",
                    varName,
                    it.first.url
                )
            }
        }
    }.getOrNull()

/**
 * Resolve the secret with the given [name] using the [secretResolverFun] and handle errors. For unresolvable
 * secrets, log a warning derived from the given [url] and [type], and return the original [name] as the
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
