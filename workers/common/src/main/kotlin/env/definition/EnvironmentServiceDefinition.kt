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
 * Definition of a base class for an environment definition that references an [ResolvedInfrastructureService].
 *
 * An _environment definition_ specifies a configuration that needs to be present when running a specific worker.
 * Environment definitions based on [ResolvedInfrastructureService]s are typically required to access private
 * repositories whose credentials can be obtained from the service. They can be used to generate package
 * manager-specific configuration files, to tell the package managers from where dependencies can be downloaded and how
 * to authenticate against these repositories.
 *
 * This base class just defines a property for the associated [ResolvedInfrastructureService]. Concrete subclasses can
 * introduce further properties that are needed to use such a service in a specific context. For instance, when
 * referencing an artifact repository from a Maven _settings.xml_ file, an alias must be defined under which this
 * repository is referenced from the build definition file.
 */
open class EnvironmentServiceDefinition(
    /** The [ResolvedInfrastructureService] referenced by this environment definition. */
    val service: ResolvedInfrastructureService,

    /**
     * A set determining the locations where the credentials of the associated [ResolvedInfrastructureService] should be
     * used. If it is defined, it overrides the corresponding property from the service.
     */
    val credentialsTypes: Set<CredentialsType>? = null
) {
    /**
     * Return a set indicating where the credentials of the [ResolvedInfrastructureService] associated with this
     * [EnvironmentServiceDefinition] should be used. Per default, this is a property of the [ResolvedInfrastructureService]
     * itself, but it is possible to override this property in the [EnvironmentServiceDefinition].
     */
    fun credentialsTypes(): Set<CredentialsType> = credentialsTypes ?: service.credentialsTypes
}
