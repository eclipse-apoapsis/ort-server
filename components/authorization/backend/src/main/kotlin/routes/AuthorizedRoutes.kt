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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.components.authorization.routes

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Payload

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.util.AttributeKey

import java.net.URI
import java.util.concurrent.TimeUnit

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService

/**
 * Configure the authentication for this server application.
 *
 * This function sets up the Ktor plugins for authentication using JWT tokens. It configures the creation of an
 * [OrtServerPrincipal] instance for authorized requests.
 */
fun Application.configureAuthentication(config: ApplicationConfig, authorizationService: AuthorizationService) {
    val issuer = config.property("jwt.issuer").getString()
    val jwksUri = URI.create(config.property("jwt.jwksUri").getString()).toURL()
    val configuredRealm = config.property("jwt.realm").getString()
    val requiredAudience = config.property("jwt.audience").getString()
    val jwkProvider = JwkProviderBuilder(jwksUri)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt(AuthenticationProviders.TOKEN_PROVIDER) {
            realm = configuredRealm
            verifier(jwkProvider, issuer) {
                acceptLeeway(10)
            }

            validate { credential ->
                credential.payload.takeIf { it.audience.contains(requiredAudience) }?.let {
                    createAuthorizedPrincipal(authorizationService, credential.payload)
                }
            }
        }
    }
}

/**
 * Create an [OrtServerPrincipal] for this [ApplicationCall]. If an [AuthorizationChecker] is present in the current
 * context, use it to an [EffectiveRole] and perform an authorization check. Result is *null* if this check fails.
 */
suspend fun ApplicationCall.createAuthorizedPrincipal(
    authorizationService: AuthorizationService,
    payload: Payload
): OrtServerPrincipal? =
    (this as? RoutingPipelineCall)?.let { routingCall ->
        val checker = routingCall.route.findAuthorizationChecker()

        runCatching {
            val effectiveRole = if (checker != null) {
                checker.loadEffectiveRole(
                    service = authorizationService,
                    userId = payload.getClaim("preferred_username").asString(),
                    call = this
                )
            } else {
                EffectiveRole.EMPTY
            }

            OrtServerPrincipal.create(payload, effectiveRole)
        }.getOrElse(OrtServerPrincipal::fromException)
    }

/**
 * Create a new [Route] for HTTP GET requests that performs an automatic authorization check using the given [checker].
 */
fun Route.get(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = get(builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP GET requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.get(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = get(path, builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP POST requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.post(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = post(builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP POST requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.post(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = post(path, builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP PATCH requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.patch(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = patch(builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP PATCH requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.patch(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = patch(path, builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP PUT requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.put(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = put(builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP PUT requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.put(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = put(path, builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP DELETE requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.delete(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = delete(builder, authorizedBody(body)).installChecker(checker)

/**
 * Create a new [Route] for HTTP DELETE requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.delete(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = delete(path, builder, authorizedBody(body)).installChecker(checker)

/**
 * Transform the given [body] to an authorized body that checks whether an authorized principal is available in the
 * call.
 */
private fun authorizedBody(body: suspend RoutingContext.() -> Unit): suspend RoutingContext.() -> Unit = {
    if (call.principal<OrtServerPrincipal>()?.isAuthorized != true) {
        throw AuthorizationException()
    }

    body()
}

/**
 * Install the given [checker] into this [Route]'s attributes, so that it is available for authorization checks when
 * the route is called.
 */
private fun Route.installChecker(checker: AuthorizationChecker): Route = apply {
    attributes.put(AuthorizationCheckerKey, checker)
}

/**
 * Search for an [AuthorizationChecker] object in the context of the current [Route]. The checker has been defined
 * using the routes DSL. It may be available in this route or in any of its parent routes.
 */
private fun Route.findAuthorizationChecker(): AuthorizationChecker? =
    this.attributes.getOrNull(AuthorizationCheckerKey)
        ?: parent?.findAuthorizationChecker()

/**
 * Constant for a key in the attributes of a [Route] to store an [AuthorizationChecker].
 */
private val AuthorizationCheckerKey = AttributeKey<AuthorizationChecker>("AuthorizationCheckerKey")

/**
 * An object defining constants for the names of supported authentication providers.
 */
object AuthenticationProviders {
    /**
     * The name of the authentication provider for authorization based on JWT tokens.
     */
    const val TOKEN_PROVIDER = "token"
}

/**
 * An exception class to indicate a failed authorization check. Such exceptions are thrown by the route functions when
 * the current user does not have the required permissions. They are mapped to HTTP 403 Forbidden responses.
 */
class AuthorizationException : RuntimeException()
