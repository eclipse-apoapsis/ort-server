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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.Serializable

/**
 * A data class describing the declaration of an infrastructure service.
 *
 * This class can be used to describe infrastructure services that are referenced during an ORT run. Repositories can
 * contain a configuration file with such declarations; this means that the repositories depend on such services, and
 * they must be taken into account when setting up the build environment.
 *
 * In contrast to the [InfrastructureService] class, objects of this class need to be resolved first before they can
 * be used. This is especially necessary for the secrets they contain, since only the names are stored here. Another
 * difference is that this class does not reference a product or an organization. Service declarations only appear in
 * the context of a concrete repository, and from this context the whole hierarchy can be determined.
 */
@Serializable
data class InfrastructureServiceDeclaration(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** An optional description for this infrastructure service. */
    val description: String? = null,

    /** The name of the [Secret] that contains the username of the credentials for this infrastructure service. */
    val usernameSecret: String,

    /** The name of the [Secret] that contains the password of the credentials for this infrastructure service. */
    val passwordSecret: String,

    /** The set of [CredentialsType]s for this infrastructure service. */
    val credentialsTypes: Set<CredentialsType> = setOf(CredentialsType.NETRC_FILE)
)
