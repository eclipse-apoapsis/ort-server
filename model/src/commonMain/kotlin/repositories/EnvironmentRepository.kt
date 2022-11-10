/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.model.repositories

import org.ossreviewtoolkit.server.model.runs.Environment

/**
 * A repository of [environments][Environment].
 */
interface EnvironmentRepository {
    /**
     * Create an environment.
     */
    fun create(
        ortVersion: String,
        javaVersion: String,
        os: String,
        processors: Int,
        maxMemory: Long,
        variables: Map<String, String>,
        toolVersions: Map<String, String>
    ): Environment

    /**
     * Get an environment by [id]. Returns null if the environment is not found.
     */
    fun get(id: Long): Environment?

    /**
     * List all environments.
     */
    fun list(): List<Environment>

    /**
     * Delete an environment by [id].
     */
    fun delete(id: Long)
}
