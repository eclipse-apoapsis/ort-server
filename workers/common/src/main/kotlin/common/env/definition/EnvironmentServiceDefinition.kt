/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common.env.definition

import org.ossreviewtoolkit.server.model.InfrastructureService

/**
 * Definition of a base class for an environment definition that references an [InfrastructureService].
 *
 * An _environment definition_ specifies a configuration that needs to be present when running a specific worker.
 * Environment definitions based on [InfrastructureService]s are typically required to access private repositories
 * whose credentials can be obtained from the service. They can be used to generate package manager-specific
 * configuration files, to tell the package managers from where dependencies can be downloaded and how to authenticate
 * against these repositories.
 *
 * This base class just defines a property for the associated [InfrastructureService]. Concrete subclasses can
 * introduce further properties that are needed to use such a service in a specific context. For instance, when
 * referencing an artifact repository from a Maven _settings.xml_ file, an alias must be defined under which this
 * repository is referenced from the build definition file.
 */
open class EnvironmentServiceDefinition(
    /** The [InfrastructureService] referenced by this environment definition. */
    val service: InfrastructureService,

    /**
     * A flag to indicate whether the associated [InfrastructureService] should be excluded from the _.netrc_ file.
     * If this flag is defined, it overrides the corresponding flag from the service.
     */
    val excludeServiceFromNetrc: Boolean? = null
) {
    /**
     * Return a flag whether the [InfrastructureService] associated with this [EnvironmentServiceDefinition] should be
     * excluded when generating the _.netrc_ file. Per default, this is a property of the [InfrastructureService]
     * itself, but it is possible to override this flag in the [EnvironmentServiceDefinition].
     */
    fun excludeServiceFromNetrc(): Boolean = excludeServiceFromNetrc ?: service.excludeFromNetrc
}
