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

package org.eclipse.apoapsis.ortserver.shared.apimodel

import kotlinx.serialization.Serializable

/**
 * The response object for the endpoint to manage infrastructure services.
 */
@Serializable
data class InfrastructureService(
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
    val usernameSecretRef: String,

    /** The reference to the secret that contains the password of the credentials for this infrastructure service. */
    val passwordSecretRef: String,

    /**
     * The set of [CredentialsType]s for this infrastructure service. This determines in which configuration files the
     * credentials of the service are listed when generating the runtime environment for a worker.
     */
    val credentialsTypes: Set<CredentialsType> = emptySet()
)
