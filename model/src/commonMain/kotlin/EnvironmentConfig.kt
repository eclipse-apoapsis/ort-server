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
 * Type definition the generic map with environment definitions.
 */
typealias EnvironmentDefinitions = Map<String, List<Map<String, String>>>

/**
 * A data class describing the environment configuration of a specific repository.
 */
@Serializable
data class EnvironmentConfig(
    /** The list of infrastructure services declared for this repository. */
    val infrastructureServices: List<InfrastructureServiceDeclaration> = emptyList(),

    /** A map with environment definitions of different types. */
    val environmentDefinitions: EnvironmentDefinitions = emptyMap(),

    /** A list with declarations for environment variables for this repository. */
    val environmentVariables: List<EnvironmentVariableDeclaration> = emptyList(),

    /** A flag that determines how semantic errors in the configuration file should be treated. */
    val strict: Boolean = true
)
