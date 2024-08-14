/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.Secret

/**
 * An interface representing an environment variable definition.
 *
 * This is typically used to provide credentials or other values to external tools called during the analysis. The
 * variable can have an arbitrary name. Its value can be obtained from a [Secret] or be a fixed value.
 */
sealed interface EnvironmentVariableDefinition {
    val name: String
}

/**
 * Concrete implementation of the [EnvironmentVariableDefinition] Interface that obtains the value of the variable from
 * a Secret.
 */
data class SecretVariableDefinition(
    /** The name of the environment variable. */
    override val name: String,

    /** The secret defining the value of the variable. */
    val valueSecret: Secret,
) : EnvironmentVariableDefinition

/**
 * Concrete implementation of the [EnvironmentVariableDefinition] Interface that obtains the value of the variable from
 * a plaintext value.
 */
data class SimpleVariableDefinition(
    /** The name of the environment variable. */
    override val name: String,

    /** The value of the environment variable. */
    val value: String
) : EnvironmentVariableDefinition
