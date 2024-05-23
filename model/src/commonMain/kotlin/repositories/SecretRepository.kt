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

import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue

/**
 * A repository of [secrets][Secret].
 */
@Suppress("TooManyFunctions")
interface SecretRepository {
    /**
     * The entity to which a secret can be attached.
     */
    enum class Entity {
        ORGANIZATION,
        PRODUCT,
        REPOSITORY
    }

    /**
     * Create a secret of the given [name] at [path] attached to [entity] with [id] and an optional [description].
     */
    fun create(path: String, name: String, description: String?, entity: Entity, id: Long): Secret

    /**
     * Get a secret by [id] and [name]. Return null if the secret is not found.
     */
    fun get(entity: Entity, id: Long, name: String): Secret?

    /**
     * List all secrets for an [entity] with [id] according to the given [parameters].
     */
    fun list(entity: Entity, id: Long, parameters: ListQueryParameters = ListQueryParameters.DEFAULT): List<Secret>

    /**
     * Update the [description] for the secret with [name] in [entity] with [id].
     */
    fun update(entity: Entity, id: Long, name: String, description: OptionalValue<String?>): Secret

    /**
     * Delete the secret with [name] in [entity] with [id].
     */
    fun delete(entity: Entity, id: Long, name: String)
}
