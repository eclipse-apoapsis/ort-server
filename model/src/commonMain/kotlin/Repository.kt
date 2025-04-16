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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.Serializable

/**
 * A repository represents a source code repository, for example, a [Git][RepositoryType.GIT] repository.
 */
@Serializable
data class Repository(
    /**
     * The unique identifier of the repository.
     */
    val id: Long,

    /**
     * The unique identifier of the [Organization] this repository belongs to.
     */
    val organizationId: Long,

    /**
     * The unique identifier of the [Product] this repository belongs to.
     */
    val productId: Long,

    /**
     * The type of the repository.
     */
    val type: RepositoryType,

    /**
     * The URL of the repository.
     */
    val url: String,

    /**
    * The description of the repository.
    * */
    val description: String? = null,
)
