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

package org.ossreviewtoolkit.server.secrets.vault.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A data class representing credentials to log into HashiCorp Vault. The information stored in an instance can be used
 * to obtain a token to be used in requests against the Vault API.
 */
@Serializable
data class VaultCredentials(
    /** The ID of the role assigned to the application. */
    @SerialName("role_id")
    val roleId: String,

    /** The secret ID required to obtain an authorized token. */
    @SerialName("secret_id")
    val secretId: String
)
