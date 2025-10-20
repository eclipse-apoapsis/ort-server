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

    /** The effective role computed for the principal. */
    val effectiveRole: EffectiveRole
) {
    companion object {
        /** Constant for the name of the claim containing the username. */
        private const val CLAIM_USERNAME = "preferred_username"

        /** Constant for the name of the claim containing the full name. */
        private const val CLAIM_FULL_NAME = "name"

        /**
         * Create an [OrtServerPrincipal] from the given JWT [payload] and [effectiveRole].
         */
        fun create(payload: Payload, effectiveRole: EffectiveRole): OrtServerPrincipal =
            OrtServerPrincipal(
                userId = payload.subject,
                username = payload.getClaim(CLAIM_USERNAME).asString(),
                fullName = payload.getClaim(CLAIM_FULL_NAME).asString(),
                effectiveRole = effectiveRole
            )
    }
}
