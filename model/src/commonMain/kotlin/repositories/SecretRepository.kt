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

import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.util.ListQueryParameters
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * A repository of [secrets][Secret].
 */
interface SecretRepository {
    /**
     * Create a secret for either [organization][organizationId], [product][productId] or [repository][repositoryId]
     */
    fun create(
        path: String,
        name: String,
        description: String,
        organizationId: Long?,
        productId: Long?,
        repositoryId: Long?
    ): Secret

    /**
     * Get a secret by [id]. Returns null if the secret is not found.
     */
    fun get(id: Long): Secret?

    /**
     * List all secrets for an [organization][organizationId] according to the given [parameters].
     */
    fun listForOrganization(
        organizationId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Secret>

    /**
     * List all secrets for a [product][productId] according to the given [parameters].
     */
    fun listForProduct(
        productId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Secret>

    /**
     * List all secrets for a [repository][repositoryId] according to the given [parameters].
     */
    fun listForRepository(
        repositoryId: Long,
        parameters: ListQueryParameters = ListQueryParameters.DEFAULT
    ): List<Secret>

    /**
     * Update a secret by [id] with the [present][OptionalValue.Present] values.
     */
    fun update(
        id: Long,
        path: OptionalValue<String>,
        name: OptionalValue<String>,
        description: OptionalValue<String?>
    ): Secret

    /**
     * Delete a secret by [id].
     */
    fun delete(id: Long)
}
