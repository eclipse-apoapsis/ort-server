/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.model.repositories

import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * A repository of [repositories][Repository].
 */
interface RepositoryRepository {
    /**
     * Create a repository.
     */
    suspend fun create(type: RepositoryType, url: String, productId: Long): Repository

    /**
     * Get a repository by [id]. Returns null if the product is not found.
     */
    suspend fun get(id: Long): Repository?

    /**
     * List all repositories for a [product][productId].
     */
    suspend fun listForProduct(productId: Long): List<Repository>

    /**
     * Update a repository by [id] with the [present][OptionalValue.Present] values.
     */
    suspend fun update(id: Long, type: OptionalValue<RepositoryType>, url: OptionalValue<String>): Repository

    /**
     * Delete a repository by [id].
     */
    suspend fun delete(id: Long)
}
