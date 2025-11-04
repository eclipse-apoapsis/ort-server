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

import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole

/**
 * A class storing information about the authenticated principal in the ORT Server.
 */
class OrtServerPrincipal(
    /** The internal ID of the user. */
    val userId: String,

    /** The username of the principal.*/
    val username: String,

    /** The full name of the principal. */
    val fullName: String,

    /**
     * An exception that occurred when setting up the principal. If this is not *null*, this exception is re-thrown
     * when querying the authorization status. The background of this property is that during authentication, all
     * exceptions are caught by Ktor and lead to HTTP 401 responses. However, for some exceptions, different status
     * codes are more appropriate, for instance, status 404 if a non-existing hierarchy ID was requested. This can
     * only be achieved by storing the exception first and re-throwing it later when a route handler is active.
     */
    val validationException: Throwable?,

    /**
     * The effective role computed for the principal. This can be *null* if either no authorization is required or the
     * authorization check failed. In the latter case, an exception is thrown when the role is accessed.
     */
    private val role: EffectiveRole?
) {
    companion object {
        /** Constant for the name of the claim containing the username. */
        private const val CLAIM_USERNAME = "preferred_username"

        /** Constant for the name of the claim containing the full name. */
        private const val CLAIM_FULL_NAME = "name"

        /**
         * Create an [OrtServerPrincipal] from the given JWT [payload] and [effectiveRole].
         */
        fun create(payload: Payload, effectiveRole: EffectiveRole?): OrtServerPrincipal =
            OrtServerPrincipal(
                userId = payload.subject,
                username = payload.getClaim(CLAIM_USERNAME).asString(),
                fullName = payload.getClaim(CLAIM_FULL_NAME).asString(),
                role = effectiveRole,
                validationException = null
            )

        /**
         * Create an [OrtServerPrincipal] for the case that during authentication the given [exception] occurred. This
         * exception is recorded, so that it can be handled later.
         */
        fun fromException(exception: Throwable): OrtServerPrincipal =
            OrtServerPrincipal(
                userId = "",
                username = "",
                fullName = "",
                role = null,
                validationException = exception
            )

        /**
         * Make sure that the current [RoutingContext] contains an authorized [OrtServerPrincipal] and return it.
         * Throw an [AuthorizationException] otherwise.
         */
        fun RoutingContext.requirePrincipal(): OrtServerPrincipal =
            call.principal<OrtServerPrincipal>() ?: throw AuthorizationException()
    }

    /**
     * A flag indicating whether the principal is authorized. If this is *true*, the effective role of the principal
     * can be accessed via [effectiveRole]. If a [validationException] is recorded in this instance, it is thrown
     * when accessing this property. Since this property is typically accessed in the beginning of a route handler,
     * this leads to proper exception handling and mapping to HTTP response status codes.
     */
    val isAuthorized: Boolean
        get() = validationException?.let { throw it } ?: (role != null)

    /**
     * The effective role of the principal if authorization was successful. Otherwise, accessing this property throws
     * an [AuthorizationException].
     */
    val effectiveRole: EffectiveRole
        get() = role ?: throw AuthorizationException()
}
