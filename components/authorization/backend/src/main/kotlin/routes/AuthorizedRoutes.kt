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

package org.eclipse.apoapsis.ortserver.components.authorization.routes

import com.auth0.jwt.interfaces.Payload

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put

import io.ktor.server.application.ApplicationCall
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

        val effectiveRole = if (checker != null) {
            checker.loadEffectiveRole(
                service = authorizationService,
                userId = payload.getClaim("preferred_username").asString(),
                call = this
            ).takeIf { checker.checkAuthorization(it) }
        } else {
            EffectiveRole.EMPTY
        }

        effectiveRole?.let { OrtServerPrincipal.create(payload, it) }
    }

/**
 * Create a new [Route] for HTTP GET requests that performs an automatic authorization check using the given [checker].
 */
fun Route.get(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker) { get(builder, body) }

/**
 * Create a new [Route] for HTTP POST requests that performs an automatic authorization check using the given [checker].
 */
fun Route.post(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker) { post(builder, body) }

/**
 * Create a new [Route] for HTTP PATCH requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.patch(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker) { patch(builder, body) }

/**
 * Create a new [Route] for HTTP PUT requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.put(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker) { put(builder, body) }

/**
 * Create a new [Route] for HTTP DELETE requests that performs an automatic authorization check using the given
 * [checker].
 */
fun Route.delete(
    builder: RouteConfig.() -> Unit,
    checker: AuthorizationChecker,
    body: suspend RoutingContext.() -> Unit
): Route = documentedAuthorized(checker) { delete(builder, body) }

/**
 * Generic function to create a new [Route] that performs an automatic authorization check using the given [checker].
 * The content of the route is defined by the given [build] function.
 */
private fun Route.documentedAuthorized(checker: AuthorizationChecker, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(authorizedRouteSelector(checker.toString()))
    authorizedRoute.attributes.put(AuthorizationCheckerKey, checker)
    authorizedRoute.build()
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
