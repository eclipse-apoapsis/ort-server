/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.model.repositories

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * Repository interface to manage [InfrastructureService] entities.
 */
@Suppress("TooManyFunctions")
interface InfrastructureServiceRepository {
    /**
     * Create a new [InfrastructureService] from the given properties for the hierarchy entity [id].
     */
    fun create(
        name: String,
        url: String,
        description: String?,
        usernameSecret: Secret,
        passwordSecret: Secret,
        credentialsTypes: Set<CredentialsType>,
        id: HierarchyId
    ): InfrastructureService

    /**
     * Return a list with the [InfrastructureService]s that belong to the given hierarchy entity [id],
     * and apply the given [parameters].
     */
    fun listForId(
        id: HierarchyId,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<InfrastructureService>

    /**
     * Return the [InfrastructureService] with the given [name] that is assigned to the given
     * hierarchy entity [id] or *null* if no such service exists.
     */
    fun getByIdAndName(id: HierarchyId, name: String): InfrastructureService?

    /**
     * Update selected properties of the [InfrastructureService] with the given [name] that is assigned to the given
     * hierarchy entity [id].
     */
    fun updateForIdAndName(
        id: HierarchyId,
        name: String,
        url: OptionalValue<String>,
        description: OptionalValue<String?>,
        usernameSecret: OptionalValue<Secret>,
        passwordSecret: OptionalValue<Secret>,
        credentialsTypes: OptionalValue<Set<CredentialsType>> = OptionalValue.Absent,
    ): InfrastructureService

    /**
     * Delete the [InfrastructureService] with the given [name] that is assigned to the given
     * hierarchy entity [id]. Throw an exception if the service cannot be found.
     */
    fun deleteForIdAndName(id: HierarchyId, name: String)

    /**
     * Return a list with [InfrastructureService]s that are associated with the given [organizationId], or
     * [productId]. If there are multiple services with the same URL, instances on a lower level of
     * the hierarchy are preferred, and others are dropped.
     */
    fun listForHierarchy(organizationId: Long, productId: Long): List<InfrastructureService>

    /**
     * Return a list with the [InfrastructureService]s that are associated with the given [Secret][secretId].
     */
    fun listForSecret(secretId: Long): List<InfrastructureService>
}
