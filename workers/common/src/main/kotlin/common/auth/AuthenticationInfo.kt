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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.Secret

/**
 * A data class holding all authentication information that is currently available. Based on this information,
 * configuration files can be generated or credentials for repositories can be obtained.
 */
internal data class AuthenticationInfo(
    /**
     * A map with the currently known secrets. The map assigns the secret paths to their values.
     */
    val secrets: Map<String, String>,

    /** A list with the [InfrastructureService]s available in the current context. */
    val services: List<InfrastructureService>
) {
    /**
     * Return the value of the given [secret] or throw an [IllegalArgumentException] if the secret cannot be resolved.
     */
    fun resolveSecret(secret: Secret): String =
        requireNotNull(secrets[secret.path]) {
            "Cannot resolve secret '${secret.path}'."
        }
}
