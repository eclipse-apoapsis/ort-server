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

import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentVariableDefinition

/**
 * A data class to represent the whole environment configuration for a repository after all references to
 * infrastructure services and secrets have been resolved.
 *
 * Before a repository can be analyzed by ORT Server, it has to be ensured that the environment has been set up
 * according to the requirements of the repository. This includes things like environment variables, credentials, or
 * package manager-specific configuration files. The requirements are declared in a configuration file located in the
 * repository. This class holds all the information contained in this configuration file in a processed form, so that
 * all data needed to create configuration files is available.
 */
data class ResolvedEnvironmentConfig(
    /** A list with [ResolvedInfrastructureService]s required by the repository. */
    val infrastructureServices: List<ResolvedInfrastructureService> = emptyList(),

    /** A list with environment definitions needed for this repository. */
    val environmentDefinitions: List<EnvironmentServiceDefinition> = emptyList(),

    /** A set defining the environment variables that need to be present when analyzing this repository. */
    val environmentVariables: Set<EnvironmentVariableDefinition> = emptySet()
)
