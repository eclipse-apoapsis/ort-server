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

package org.eclipse.apoapsis.ortserver.workers.common.env.config

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.model.CredentialsType

/**
 * A data class representing the declaration of an infrastructure service in the environment configuration file of a
 * repository. In this file, specific infrastructure can be listed that is not defined for the owning product or
 * repository. When resolving references to infrastructure services from other parts of the configuration file, the
 * services defined here have the highest precedence; then services are looked up from the product or the organization.
 */
@Serializable
internal data class RepositoryInfrastructureService(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** An optional description for this infrastructure service. */
    val description: String? = null,

    /**
     * The reference to the secret that contains the username of the credentials for this infrastructure service.
     * The reference contains the name of the secret and optionally the structure it belongs to.
     */
    val usernameSecret: String,

    /** The reference to the secret that contains the password of the credentials for this infrastructure service. */
    val passwordSecret: String,

    /** The set of [CredentialsType]s for this infrastructure service. */
    val credentialsTypes: Set<CredentialsType> = emptySet()
)
