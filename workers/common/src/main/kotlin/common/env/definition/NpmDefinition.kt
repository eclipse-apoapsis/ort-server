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
 * An enumeration class defining the supported ways to declare the authentication against an NPM registry.
 */
enum class NpmAuthMode {
    /**
     * Authentication is done via the properties `username` and `_password`; the values are obtained directly from the
     * corresponding secrets defined for the infrastructure service.
      */
    PASSWORD,

    /**
     * Authentication is done via the properties `username` and `_password`; the values are obtained from the
     * corresponding secrets defined for the infrastructure service, but the value of the password secret is base64
     * encoded.
     */
    PASSWORD_BASE64,

    /**
     * Authentication is done via the `_auth` property using the password secret from the infrastructure service as
     * value. The username secret is ignored.
     */
    PASSWORD_AUTH,

    /**
     * Authentication is done via the `_authToken` property using the password secret from the infrastructure service
     * as value. The username secret is ignored.
     */
    PASSWORD_AUTH_TOKEN,

    /**
     * Authentication is done via the `_auth` property; the value is generated from the username and password secrets
     * defined for the infrastructure service whose values are concatenated, separated by a colon, and base64 encoded.
     */
    USERNAME_PASSWORD_AUTH
}

/**
 * A specific [EnvironmentServiceDefinition] class for generating the _.npmrc_ file for the NPM package manager.
 *
 * This class defines the NPM-specific configuration of a private registry. The most complex part here is the
 * authentication. NPM allows multiple options here; to reflect those, the [NpmAuthMode] enumeration class has been
 * introduced.
 *
 * See https://docs.npmjs.com/cli/v9/configuring-npm/npmrc?v=true
 */
class NpmDefinition(
    service: ResolvedInfrastructureService,

    credentialsTypes: Set<CredentialsType>?,

    /**
     * An optional scope of the registry. If defined, the generated _npmrc_ file will contain an entry that assigns
     * this scope to the registry, and NPM definition files can reference this scope.
     */
    val scope: String? = null,

    /**
     * An optional email address that may be required for the authentication.
     */
    val email: String? = null,

    /**
     * Defines the way authentication should be handled for this private registry.
     */
    val authMode: NpmAuthMode = NpmAuthMode.PASSWORD,

    /**
     * A flag to control the generation of the `always-auth` property for this registry. Via this flag, NPM can be
     * instructed to always send authentication information.
     */
    val alwaysAuth: Boolean = true
) : EnvironmentServiceDefinition(service, credentialsTypes)
