/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.context

import org.eclipse.apoapsis.ortserver.components.secrets.SecretService
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.secrets.SecretValue
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

/**
 * Definition of a service interface that provides read access to secret values.
 *
 * An object implementing this interface is used by the [WorkerContext] to resolve the secrets not associated with
 * infrastructure services; for instance, secrets referenced by plugin configurations or environment variables.
 */
interface SecretResolverService {
    companion object {
        /**
         * Return a [SecretResolverService] implementation that wraps the given [service] and thus provides a
         * read-only view of the secrets managed by the service.
         */
        fun wrapSecretService(service: SecretService): SecretResolverService = SecretResolverServiceImpl(service)
    }

    /**
     * Get the value of a [secret]. Return *null* if the value is not found.
     */
    fun getSecretValue(secret: Secret): SecretValue?

    /**
     * List all secrets for the provided [hierarchy]. If there are secrets with the same name in different levels of the
     * hierarchy, only the one closest to the repository is returned.
     */
    fun listForHierarchy(hierarchy: Hierarchy): List<Secret>
}

/**
 * An implementation of [SecretResolverService] that wraps a [SecretService] to which it delegates all requests.
 */
private class SecretResolverServiceImpl(
    /** The [SecretService] to delegate requests to. */
    private val secretService: SecretService
) : SecretResolverService {
    override fun getSecretValue(secret: Secret): SecretValue? = secretService.getSecretValue(secret)

    override fun listForHierarchy(hierarchy: Hierarchy): List<Secret> =
        runBlocking { secretService.listForHierarchy(hierarchy) }
}
