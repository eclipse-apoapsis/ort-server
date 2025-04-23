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
import org.eclipse.apoapsis.ortserver.model.InfrastructureService

/**
 * A specific [EnvironmentServiceDefinition] class for generating the NuGet _NuGet.Config_ file with configuration
 * specific to package sources.
 *
 * See https://learn.microsoft.com/en-us/nuget/reference/nuget-config-file
 */
class NuGetDefinition(
    service: InfrastructureService,

    credentialsTypes: Set<CredentialsType>?,

    /**
     * The name to assign to the package source.
     */
    val sourceName: String,

    /**
     * The path or URL of the package source.
     */
    val sourcePath: String,

    /**
     * The NuGet server protocol version to be used. The current version is "3". Defaults to version "2" when not
     * pointing to a package source URL ending in .json (e.g., https://api.nuget.org/v3/index.json). Supported in
     * NuGet 3.0+.
     *
     * See [NuGet Server API](https://learn.microsoft.com/en-us/nuget/api/overview) for more information about the
     * version 3 protocol.
     */
    val sourceProtocolVersion: String? = null,

    /**
     * Defines the authentication type for this package source.
     */
    val authMode: NuGetAuthMode = NuGetAuthMode.API_KEY
) : EnvironmentServiceDefinition(service, credentialsTypes)

enum class NuGetAuthMode {
    /**
     * Authentication for the repository is done using `ClearTextPassword` entry which will contain an unencrypted
     * password for the specific repository in `<packageSourceCredentials>` block.
     */
    PASSWORD,

    /**
     * Authentication for the repository is done via an API authentication key, in the same was as it is set with the
     * nuget `setapikey` command.
     */
    API_KEY
}
