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

package org.eclipse.apoapsis.ortserver.workers.common

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.Secret

/** A data class describing an infrastructure service with resolved secrets. */
data class ResolvedInfrastructureService(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** An optional description for this infrastructure service. */
    val description: String? = null,

    /** The [Secret] that contains the username of the credentials for this infrastructure service. */
    val usernameSecret: Secret,

    /** The [Secret] that contains the password of the credentials for this infrastructure service. */
    val passwordSecret: Secret,

    /**
     * The set of [CredentialsType]s for this infrastructure service. This determines in which configuration files the
     * credentials of the service are listed when generating the runtime environment for a worker. All services
     * involved in an ORT run are installed in the authenticator, so that their credentials are available when
     * accessing the corresponding URLs from within the JVM. If the credentials are also required from external tools
     * (e.g., the Git CLI), this needs to be indicated by adding the corresponding constant. Per default, the set
     * is empty, so that the services are only used by the authenticator of the JVM.
     */
    val credentialsTypes: Set<CredentialsType> = emptySet()
) {
    /** Convert this [ResolvedInfrastructureService] to an [InfrastructureService]. */
    fun toInfrastructureService(): InfrastructureService =
        InfrastructureService(
            name = name,
            url = url,
            description = description,
            usernameSecret = usernameSecret.name,
            passwordSecret = passwordSecret.name,
            organization = null,
            product = null,
            repository = null,
            credentialsTypes = credentialsTypes
        )

    /** Convert this [ResolvedInfrastructureService] to an [InfrastructureServiceDeclaration]. */
    fun toInfrastructureServiceDeclaration(): InfrastructureServiceDeclaration =
        InfrastructureServiceDeclaration(
            name = name,
            url = url,
            description = description,
            usernameSecret = usernameSecret.name,
            passwordSecret = passwordSecret.name,
            credentialsTypes = credentialsTypes
        )
}
