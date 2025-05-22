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

package org.eclipse.apoapsis.ortserver.secrets

import org.eclipse.apoapsis.ortserver.model.HierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

/**
 * Definition of an interface for accessing secrets stored in a specific storage implementation.
 *
 * This interface defines a basic CRUD API for interacting with a storage for secrets. There will be concrete
 * implementations for specific products. The API is reduced to a bare minimum to support a broad range of secret
 * storage implementations.
 *
 * Client code is not intended to use this interface directly. Instead, a wrapper implementation is used that provides
 * a rich API on top of the functions defined here.
 */
interface SecretsProvider {
    /**
     * Return the [Secret] associated with the given [Path] or `null` if no secret is associated with this path.
     * A concrete implementation may throw a proprietary exception if there was a problem when accessing the
     * underlying secret storage.
     */
    fun readSecret(path: Path): Secret?

    /**
     * Write the given [secret] under the given [path] into the underlying secret storage. An implementation may throw
     * a proprietary exception if it encounters a problem.
     */
    fun writeSecret(path: Path, secret: Secret)

    /**
     * Remove the [Secret] associated with the given [path] from the underlying secret storage. An implementation
     * should throw an exception if the operation failed.
     */
    fun removeSecret(path: Path)

    /**
     * Generate a [Path] in the hierarchy identified by [id] that is named [secretName]. This default implementation
     * concatenates the property names with underscores.
     */
    fun createPath(id: HierarchyId, secretName: String): Path {
        val secretType = when (id) {
            is OrganizationId -> "organization"
            is ProductId -> "product"
            is RepositoryId -> "repository"
        }

        return Path("${secretType}_${id.value}_$secretName")
    }
}
