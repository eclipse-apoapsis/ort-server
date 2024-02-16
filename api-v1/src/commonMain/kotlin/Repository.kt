/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.api.v1

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * Response object for the repository endpoint.
 */
@Serializable
data class Repository(
    val id: Long,

    /** The type of the repository. */
    val type: RepositoryType,

    /** The url to the repository. */
    val url: String
)

/**
 * Request object for the create repository endpoint.
 */
@Serializable
data class CreateRepository(
    val type: RepositoryType,
    val url: String
)

/**
 * Request object for the update repository endpoint.
 */
@Serializable
data class UpdateRepository(
    val type: OptionalValue<RepositoryType> = OptionalValue.Absent,
    val url: OptionalValue<String> = OptionalValue.Absent,
)

enum class RepositoryType {
    GIT,
    GIT_REPO,
    MERCURIAL,
    SUBVERSION,
    CVS
}
