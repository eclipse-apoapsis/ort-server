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

package org.eclipse.apoapsis.ortserver.workers.common.env.definition

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService

/**
 * A specific [EnvironmentServiceDefinition] class for generating the Yarn _.yarnrc.yml_ file with configuration
 * specific to registries.
 *
 * See https://yarnpkg.com/configuration/yarnrc#npmRegistries
 */
class YarnDefinition(
    service: ResolvedInfrastructureService,

    credentialsTypes: Set<CredentialsType>?,

    /**
     * A flag to control the generation of the `mpmAlwaysAuth` property for this registry. Via this flag, Yarn can be
     * instructed to always send authentication information.
     */
    val alwaysAuth: Boolean = true,

    /**
     * Defines the way authentication should be handled for this private registry.
     */
    val authMode: YarnAuthMode = YarnAuthMode.AUTH_TOKEN
) : EnvironmentServiceDefinition(service, credentialsTypes)

enum class YarnAuthMode {
    /**
     * Authentication is done via the `npmAuthIdent` property using the username and password secrets from the
     * infrastructure service as values. This authentication is strongly discouraged in favor of [AUTH_TOKEN]
     */
    AUTH_IDENT,

    /**
     * Authentication is done via the `npmAuthToken` property using the password secret from the infrastructure service
     * as a value. The username secret is ignored.
     */
    AUTH_TOKEN
}
