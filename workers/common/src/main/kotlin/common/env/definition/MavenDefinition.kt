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
 * A specific [EnvironmentServiceDefinition] class for generating the Maven _settings.xml_ file.
 *
 * This class defines under which ID a specific [InfrastructureService] with its credentials should be referenced from
 * the _settings.xml_ file.
 */
class MavenDefinition(
    service: InfrastructureService,

    credentialsTypes: Set<CredentialsType>?,

    /**
     * The ID of the represented service. This ID is used in _pom.xml_ files to refer to the represented repository.
     * In the generated _settings.xml_ file, the ID appears as the _id_ property for the corresponding server in the
     * _servers_ section. See https://maven.apache.org/settings.html#servers.
     */
    val id: String,

    /**
     * If set, this [MavenDefinition] is treated as a mirror definition and will additionally appear in the _mirrors_
     * section of the generated _settings.xml_ file.
     *
     * The value specifies the repository ID that this mirror should apply to, as used in the _mirrorOf_ element of
     * Maven’s mirror configuration. For example, to mirror Maven Central, use `"central"`, or use `"*"` to apply this
     * mirror to all repositories.
     */
    val mirrorOf: String? = null
) : EnvironmentServiceDefinition(service, credentialsTypes)
