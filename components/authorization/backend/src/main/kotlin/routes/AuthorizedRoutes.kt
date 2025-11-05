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

import com.auth0.jwt.interfaces.Payload

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService

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
): Route = documentedAuthorized(checker, body) { get(builder, it) }

/**
 * Create a new [Route] for HTTP GET requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.get(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { get(path, builder, it) }

/**
 * Create a new [Route] for HTTP POST requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.post(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { post(builder, it) }

/**
 * Create a new [Route] for HTTP POST requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.post(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { post(path, builder, it) }

/**
 * Create a new [Route] for HTTP PATCH requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.patch(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { patch(builder, it) }

/**
 * Create a new [Route] for HTTP PATCH requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.patch(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { patch(path, builder, it) }

/**
 * Create a new [Route] for HTTP PUT requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.put(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { put(builder, it) }

/**
 * Create a new [Route] for HTTP PUT requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.put(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { put(path, builder, it) }

/**
 * Create a new [Route] for HTTP DELETE requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.delete(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { delete(builder, it) }

/**
 * Create a new [Route] for HTTP DELETE requests with the given [path] that performs an automatic authorization check
 * using the given [checker].
 */
fun Route.delete(
    path: String,
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker, body) { delete(path, builder, it) }

/**
 * Generic function to create a new [Route] that performs an automatic authorization check using the given [checker].
 * The content of the route is defined by the given original [body] and the [build] function.
 */
private fun Route.documentedAuthorized(
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit,
    build: Route.(suspend RoutingContext.() -> Unit) -> Unit
): Route {
    val authorizedRoute = createChild(authorizedRouteSelector(checker.toString()))
    authorizedRoute.attributes.put(AuthorizationCheckerKey, checker)

    val authorizedBody: suspend RoutingContext.() -> Unit = {
        // Check whether an authorized principal is available in the call.
        if (call.principal<OrtServerPrincipal>()?.isAuthorized != true) {
            throw AuthorizationException()
        }

        body()
    }

    authorizedRoute.build(authorizedBody)
    return authorizedRoute
}

/**
 * Create a [RouteSelector] for a new authorized [Route] whose string representation is derived from the given [tag].
 */
private fun authorizedRouteSelector(tag: String): RouteSelector =
    object : RouteSelector() {
        override suspend fun evaluate(
            context: RoutingResolveContext,
            segmentIndex: Int
        ): RouteSelectorEvaluation = RouteSelectorEvaluation.Transparent

        override fun toString(): String {
            return "(authorized $tag)"
        }
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
