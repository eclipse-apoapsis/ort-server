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

package org.ossreviewtoolkit.server.model

/**
 * A data class to represent the whole environment configuration for a [Repository].
 *
 * Before a repository can be analyzed by ORT Server, it has to be ensured that the environment has been set up
 * according to the requirements of the repository. This includes things like environment variables, credentials, or
 * package manager-specific configuration files.
 */
data class EnvironmentConfiguration(
    /** A list with [InfrastructureService]s required by the repository. */
    val infrastructureServices: List<InfrastructureService>
)
