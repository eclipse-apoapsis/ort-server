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

/**
 * The [Secret] holds the path under which the secret value can be retrieved along with the additional information
 * about the secret, such as name, description, organization, product or repository to which the given secret belongs.
 */
data class Secret(
    /**
     * The unique identifier of the secret.
     */
    val id: Long,

    /**
     * The path to the secret.
     */
    val path: String,

    /**
     * The name of the secret.
     */
    val name: String,

    /**
     * The description of the secret.
     */
    val description: String?,

    /**
     * The ID of the [Organization] the secret belongs to.
     */
    val organizationId: Long?,

    /**
     * The ID of the [Product] the secret belongs to.
     */
    val productId: Long?,

    /**
     * The ID of the [Repository] the secret belongs to.
     */
    val repositoryId: Long?
)
