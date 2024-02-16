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
 * A class defining an environment variable which needs to be present when analyzing a repository.
 *
 * This is typically used to provide credentials or other values to external tools called during the analysis. The
 * variable can have an arbitrary name. Its value is obtained from a [Secret].
 */
data class EnvironmentVariableDefinition(
    /** The name of the environment variable. */
    val name: String,

    /** The secret defining the value of the variable. */
    val valueSecret: Secret
)
