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

import io.ktor.server.application.ApplicationCall

import org.eclipse.apoapsis.ortserver.components.authorization.rights.EffectiveRole
import org.eclipse.apoapsis.ortserver.components.authorization.service.AuthorizationService

/**
 * An interface defining a mechanism to check for required permissions using an [AuthorizationService] instance.
 *
 * The idea behind this interface is that a concrete implementation is responsible for doing a concrete authorization
 * check, such as testing for the presence of a specific permission on an element of the hierarchy. To do this, the
 * instance needs to load the permissions on this element from the service and then test whether the affected
 * permission is contained.
 *
 * Implementations of this interface can be passed into special routing functions that use them to perform
 * authorization checks automatically. There are convenience functions to create default instances easily.
 *
 * In addition to the functions defined here, concrete implementations should provide a meaningful `toString()`
 * implementation, since this is used to construct a routes selector internally.
 */
interface AuthorizationChecker {
    /**
     * Use the provided [service] to load the [EffectiveRole] of the user with the given [userId] for the current
     * [call]. A typical implementation will figure out the ID of an element in the hierarchy (organization, product,
     * or repository) based on current call parameters. Then it can invoke the [service] to query the permissions on
     * this element.
     */
    suspend fun loadEffectiveRole(service: AuthorizationService, userId: String, call: ApplicationCall): EffectiveRole

    /**
     * Check whether the given [effectiveRole] contains the permission(s) required by this [AuthorizationChecker].
     * This function is called with the [EffectiveRole] that was loaded via [loadEffectiveRole].
     */
    fun checkAuthorization(effectiveRole: EffectiveRole): Boolean
}
