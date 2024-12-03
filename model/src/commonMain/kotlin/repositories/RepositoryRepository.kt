/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [repositories][Repository].
 */
interface RepositoryRepository {
    /**
     * Create a repository.
     */
    fun create(type: RepositoryType, url: String, productId: Long): Repository

    /**
     * Get a repository by [id]. Returns null if the repository is not found.
     */
    fun get(id: Long): Repository?

    /**
     * Return a [Hierarchy] object for the repository with the given [id] with the entities it belongs to. Fail with
     * an exception if the [id] cannot be resolved.
     */
    fun getHierarchy(id: Long): Hierarchy

    /**
     * List all repositories according to the given [parameters].
     */
    fun list(parameters: ListQueryParameters = ListQueryParameters.DEFAULT): List<Repository>

    /**
     * List all repositories for a [product][productId] according to the given [parameters].
     */
    fun listForProduct(
        productId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): ListQueryResult<Repository>

    /**
     * Update a repository by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        type: OptionalValue<RepositoryType> = OptionalValue.Absent,
        url: OptionalValue<String> = OptionalValue.Absent
    ): Repository

    /**
     * Delete a repository by [id].
     */
    fun delete(id: Long)

    /**
     * Delete all [Repositories][Repository] associated to this [productId].
     */
    fun deleteByProduct(productId: Long): Int
}
