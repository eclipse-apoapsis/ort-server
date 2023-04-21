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

package org.ossreviewtoolkit.server.api.v1

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * Response object for the Secrets Metadata endpoint.
 */
@Serializable
data class Secret(
    /**
     * The name of the secret.
     */
    val name: String,

    /**
     * The description of the secret.
     */
    val description: String?
)

/**
 * Request object for the create secret endpoint.
 */
@Serializable
data class CreateSecret(
    val name: String,
    val value: String,
    val description: String?,
    val organizationId: Long?,
    val productId: Long?,
    val repositoryId: Long?
)

/**
 * Request object for the update secret endpoint.
 */
@Serializable
data class UpdateSecret(
    val name: OptionalValue<String> = OptionalValue.Absent,
    val value: OptionalValue<String> = OptionalValue.Absent,
    val description: OptionalValue<String> = OptionalValue.Absent,
)
